package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class RiderManager(
    private val scope: CoroutineScope,
    private val googleMapUseCase: GoogleMapUseCase
) {

    private val animationController = RiderAnimationController(scope)
    private val trackingManager = RiderTrackingManager(googleMapUseCase)

    val riderState: StateFlow<CombinedRiderState> = combine(
        animationController.riderState,
        trackingManager.trackingState
    ) { animationState, trackingState ->
        CombinedRiderState(
            animatedLocation = animationState.animatedLocation,
            rawLocation = animationState.rawLocation,
            heading = animationState.heading,
            isAnimating = animationState.isAnimating,
            visitedRoutePoints = trackingState.visitedRoutePoints,
            remainingRoutePoints = trackingState.remainingRoutePoints,
            currentSegmentProgress = trackingState.currentSegmentProgress,
            isRiderDeviated = trackingState.isRiderDeviated,
            riderTrail = animationState.riderTrail,
            cameraBounds = null,
            cameraMode = CameraMode.FOLLOW_RIDER_AND_PENDING_LOCATIONS,
            shouldFollowRider = true,
            isActive = animationState.isActive
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CombinedRiderState()
    )

    fun processRiderLocationUpdate(
        response: RiderLocationResponse,
        activeSegment: RouteSegment?,
        childOrderId: String,
        visibleStores: List<StoreLocation>,
        destination: LatLng?,
        remainingRoutePoints: List<LatLng>
    ) {
        val latitude = response.location.latitude
        val longitude = response.location.longitude
        if (latitude == null || longitude == null) return

        val riderLocation = LatLng(latitude, longitude)

        val pruningResult = processRouteTracking(
            riderLocation = riderLocation,
            activeSegment = activeSegment,
            childOrderId = childOrderId,
            remainingRoutePoints = remainingRoutePoints
        )

        animationController.processRiderLocation(
            riderLocation = riderLocation,
            isOutForDelivery = true,
            visitedRoutePoints = pruningResult?.visitedPoints ?: listOf(),
            remainingRoutePoints = pruningResult?.remainingPoints ?: listOf()
        )

        animationController.updateRiderTrail(riderLocation)
        checkRouteDeviation(riderLocation, trackingManager.trackingState.value.remainingRoutePoints, childOrderId)
    }

    fun processMultiStopLocation(childOrderId: String, riderLocation: LatLng): MultiStopLocationUpdate =
        trackingManager.processMultiStopLocation(childOrderId, riderLocation)

    fun initializeForRoute(activeSegment: RouteSegment) {
        trackingManager.resetForNewRoute()
        initializeRiderPosition(activeSegment)
    }

    fun handleReroute(newSegment: RouteSegment?) {
        if (newSegment == null || newSegment.routePoints.isEmpty()) return
        trackingManager.resetForNewRoute()
        animationController.clearAnimation()
        animationController.initializeRiderPosition(newSegment.routePoints)
    }

    fun toggleFollowRider(): Boolean = true

    fun clearAll() {
        animationController.clearAnimation()
        trackingManager.clearTracking()
    }

    fun animateToDestination(destination: LatLng) {
        animationController.animateDirectToDestination(destination)
    }

    fun positionAtDestination(destination: LatLng) {
        animationController.positionAtDestination(destination)
    }

    private fun processRouteTracking(
        riderLocation: LatLng,
        activeSegment: RouteSegment?,
        childOrderId: String,
        remainingRoutePoints: List<LatLng>
    ): RoutePruningResult? {
        if (!trackingManager.shouldUpdatePruning(riderLocation)) return null
        val pruningResult = trackingManager.updateRoutePruning(
            riderLocation = riderLocation,
            activeSegment = activeSegment,
            childOrderId = childOrderId,
            remainingRoutePoints = remainingRoutePoints
        )
        if (pruningResult != null) trackingManager.updatePruningLocation(riderLocation)
        return pruningResult
    }

    private fun checkRouteDeviation(riderLocation: LatLng, routePoints: List<LatLng>, childOrderId: String) {
        trackingManager.checkRouteDeviation(riderLocation = riderLocation, routePoints = routePoints)
    }

    private fun initializeRiderPosition(activeSegment: RouteSegment?) {
        val routePoints = activeSegment?.routePoints ?: emptyList()
        animationController.initializeRiderPosition(routePoints)
    }
}

data class CombinedRiderState(
    val animatedLocation: LatLng? = null,
    val rawLocation: LatLng? = null,
    val heading: Double = 0.0,
    val isAnimating: Boolean = false,
    val visitedRoutePoints: List<LatLng> = emptyList(),
    val remainingRoutePoints: List<LatLng> = emptyList(),
    val currentSegmentProgress: Float = 0f,
    val isRiderDeviated: Boolean = false,
    val riderTrail: List<LatLng> = emptyList(),
    val cameraBounds: CameraBounds? = null,
    val cameraMode: CameraMode = CameraMode.SHOW_PENDING_LOCATIONS,
    val shouldFollowRider: Boolean = true,
    val isActive: Boolean = false
)
