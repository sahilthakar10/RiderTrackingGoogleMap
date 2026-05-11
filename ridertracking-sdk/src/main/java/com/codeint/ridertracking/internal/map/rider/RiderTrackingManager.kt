package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RiderTrackingManager(
    private val googleMapUseCase: GoogleMapUseCase
) {

    private val _trackingState = MutableStateFlow(RiderTrackingState())
    val trackingState: StateFlow<RiderTrackingState> = _trackingState.asStateFlow()

    fun updateRoutePruning(
        riderLocation: LatLng,
        activeSegment: RouteSegment?,
        childOrderId: String,
        remainingRoutePoints: List<LatLng>
    ): RoutePruningResult? {
        if (remainingRoutePoints.isEmpty()) return null

        val currentState = _trackingState.value

        try {
            val rawProgress = RouteUtils.calculateSegmentProgress(
                riderLocation, remainingRoutePoints, currentState.lastValidLocation
            )
            val currentProgress = currentState.currentSegmentProgress
            val monotonicProgress = if (rawProgress > currentProgress) rawProgress else currentProgress
            val monotonicProgressIndex = (monotonicProgress * (remainingRoutePoints.size - 1)).toInt()

            return if (monotonicProgressIndex >= 0 && monotonicProgressIndex < remainingRoutePoints.size) {
                val visitedPoints = remainingRoutePoints.take(monotonicProgressIndex + 1)
                val remainingPoints = remainingRoutePoints.drop(monotonicProgressIndex)

                updateTrackingState { state ->
                    state.copy(
                        visitedRoutePoints = visitedPoints,
                        remainingRoutePoints = remainingPoints,
                        currentSegmentProgress = monotonicProgress,
                        lastValidLocation = riderLocation
                    )
                }
                googleMapUseCase.updateRoute(childOrderId = childOrderId, routePoints = remainingPoints)

                RoutePruningResult(
                    visitedPoints = visitedPoints,
                    remainingPoints = remainingPoints,
                    progress = monotonicProgress,
                    hasForwardProgress = true
                )
            } else {
                null
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun shouldUpdatePruning(currentLocation: LatLng): Boolean {
        val lastPruningLocation = _trackingState.value.lastPruningLocation ?: return true
        val distance = RouteUtils.calculateDistance(
            currentLocation.latitude, currentLocation.longitude,
            lastPruningLocation.latitude, lastPruningLocation.longitude
        )
        return distance > GoogleMapConstants.MIN_PRUNING_DISTANCE_METERS
    }

    fun updatePruningLocation(location: LatLng) {
        updateTrackingState { state -> state.copy(lastPruningLocation = location) }
    }

    fun checkRouteDeviation(
        riderLocation: LatLng,
        routePoints: List<LatLng>,
        thresholdMeters: Double = GoogleMapConstants.MULTI_STOP_DEVIATION_THRESHOLD_METERS
    ): RouteDeviationResult {
        if (routePoints.isEmpty()) return RouteDeviationResult(isDeviated = false, deviationDistance = 0.0)

        val (isDeviated, deviationDistance) = googleMapUseCase.checkRouteDeviation(riderLocation, routePoints, thresholdMeters)
        updateTrackingState { state -> state.copy(isRiderDeviated = isDeviated) }

        return RouteDeviationResult(
            isDeviated = isDeviated,
            deviationDistance = deviationDistance,
            isSignificant = isDeviated && deviationDistance > thresholdMeters * GoogleMapConstants.MAX_DEVIATION_THRESHOLD_MULTIPLIER
        )
    }

    fun processMultiStopLocation(childOrderId: String, riderLocation: LatLng): MultiStopLocationUpdate =
        googleMapUseCase.processMultiStopLocationUpdate(childOrderId, riderLocation)

    fun resetForNewRoute() {
        updateTrackingState { RiderTrackingState() }
    }

    fun clearTracking() {
        _trackingState.value = RiderTrackingState()
    }

    private fun updateTrackingState(update: (RiderTrackingState) -> RiderTrackingState) {
        _trackingState.value = update(_trackingState.value)
    }
}

data class RiderTrackingState(
    val visitedRoutePoints: List<LatLng> = emptyList(),
    val remainingRoutePoints: List<LatLng> = emptyList(),
    val currentSegmentProgress: Float = 0f,
    val lastValidLocation: LatLng? = null,
    val lastPruningLocation: LatLng? = null,
    val isRiderDeviated: Boolean = false,
    val currentStoreIndex: Int = 0,
    val isAtStore: Boolean = false
)

data class RoutePruningResult(
    val visitedPoints: List<LatLng>,
    val remainingPoints: List<LatLng>,
    val progress: Float,
    val hasForwardProgress: Boolean
)

data class RouteDeviationResult(
    val isDeviated: Boolean,
    val deviationDistance: Double,
    val isSignificant: Boolean = false
)
