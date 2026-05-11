package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class RiderAnimationController(
    private val scope: CoroutineScope
) {

    private val _riderState = MutableStateFlow(RiderAnimationState())
    val riderState: StateFlow<RiderAnimationState> = _riderState.asStateFlow()

    private var routeAnimationJob: Job? = null
    private var isAnimatingRoute = false
    private var lastAnimatedVisitedPointsCount = 0

    private var riderSpeedKmh: Float = 240f
    private val completionAnimationSpeedKmh: Float = 100f

    fun setRiderSpeed(speedKmh: Float) {
        riderSpeedKmh = speedKmh.coerceIn(5f, 60f)
    }

    fun getRiderSpeed(): Float = riderSpeedKmh

    private var lastLocationUpdateTime = 0L
    private val minLocationUpdateInterval = GoogleMapConstants.MIN_LOCATION_UPDATE_INTERVAL_MS

    fun processRiderLocation(
        riderLocation: LatLng,
        isOutForDelivery: Boolean,
        visitedRoutePoints: List<LatLng>,
        remainingRoutePoints: List<LatLng>
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLocationUpdateTime < minLocationUpdateInterval) return

        if (isOutForDelivery) {
            updateRiderState { state ->
                state.copy(
                    rawLocation = riderLocation,
                    isActive = true,
                    visitedRoutePoints = visitedRoutePoints,
                    remainingRoutePoints = remainingRoutePoints
                )
            }
            checkAndStartRouteAnimation()
        } else {
            initializeRiderPosition(remainingRoutePoints)
        }
        lastLocationUpdateTime = currentTime
    }

    fun initializeRiderPosition(remainingRoutePoints: List<LatLng>) {
        val currentState = _riderState.value
        val initialPosition = if (currentState.visitedRoutePoints.isNotEmpty()) {
            currentState.visitedRoutePoints.last()
        } else if (remainingRoutePoints.isNotEmpty()) {
            remainingRoutePoints.first()
        } else {
            return
        }

        updateRiderState { state ->
            state.copy(animatedLocation = initialPosition, rawLocation = initialPosition)
        }
        setPredictiveHeading(remainingRoutePoints)
    }

    fun updateRiderTrail(location: LatLng) {
        updateRiderState { state ->
            val currentTrail = state.riderTrail.toMutableList()
            currentTrail.add(location)
            if (currentTrail.size > GoogleMapConstants.RIDER_TRAIL_MAX_SIZE) {
                currentTrail.removeAt(0)
            }
            state.copy(riderTrail = currentTrail)
        }
    }

    fun clearAnimation() {
        routeAnimationJob?.cancel()
        isAnimatingRoute = false
        lastAnimatedVisitedPointsCount = 0
        updateRiderState { state ->
            state.copy(
                animatedLocation = null, rawLocation = null, heading = 0.0,
                isActive = false, visitedRoutePoints = emptyList(),
                remainingRoutePoints = emptyList(), riderTrail = emptyList()
            )
        }
    }

    fun stopAnimation() {
        routeAnimationJob?.cancel()
        isAnimatingRoute = false
    }

    fun animateDirectToDestination(destination: LatLng) {
        val currentState = _riderState.value
        val currentPosition = currentState.animatedLocation ?: currentState.rawLocation

        if (currentPosition == null) {
            positionAtDestination(destination)
            return
        }

        routeAnimationJob?.cancel()

        routeAnimationJob = scope.launch {
            try {
                isAnimatingRoute = true
                updateRiderState { state -> state.copy(isAnimating = true) }

                val distance = RouteUtils.calculateDistance(
                    currentPosition.latitude, currentPosition.longitude,
                    destination.latitude, destination.longitude
                )

                val timing = calculateCompletionAnimationTiming(distance)
                val bearing = RouteUtils.calculateBearing(currentPosition, destination)
                updateRiderState { state -> state.copy(heading = bearing.toDouble()) }

                val startTime = System.currentTimeMillis()
                val totalDuration = timing.totalDurationMs
                var lastUpdateTime = startTime

                while (true) {
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - startTime
                    val rawProgress = (elapsedTime.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    val easedProgress = applyEaseInOutCubic(rawProgress)
                    val interpolatedPosition = interpolateLocation(currentPosition, destination, easedProgress)

                    updateRiderState { state ->
                        state.copy(animatedLocation = interpolatedPosition, rawLocation = interpolatedPosition)
                    }

                    if (rawProgress >= 1.0f) break

                    val targetFrameTime = 16L
                    val actualFrameTime = currentTime - lastUpdateTime
                    val nextDelay = (targetFrameTime - actualFrameTime).coerceAtLeast(8L)
                    lastUpdateTime = currentTime
                    delay(nextDelay)
                }

                updateRiderState { state ->
                    state.copy(animatedLocation = destination, rawLocation = destination, isAnimating = false)
                }
            } catch (_: Exception) {
            } finally {
                isAnimatingRoute = false
                updateRiderState { state -> state.copy(isAnimating = false) }
            }
        }
    }

    private fun applyEaseInOutCubic(progress: Float): Float = if (progress < 0.5f) {
        4f * progress * progress * progress
    } else {
        val adjustedProgress = progress - 1f
        1f + 4f * adjustedProgress * adjustedProgress * adjustedProgress
    }

    fun positionAtDestination(destination: LatLng) {
        routeAnimationJob?.cancel()
        isAnimatingRoute = false
        updateRiderState { state ->
            state.copy(
                animatedLocation = destination, rawLocation = destination,
                isActive = true, isAnimating = false,
                visitedRoutePoints = emptyList(), remainingRoutePoints = emptyList()
            )
        }
    }

    private fun checkAndStartRouteAnimation() {
        val currentState = _riderState.value
        if (currentState.visitedRoutePoints.isNotEmpty()) {
            startSpeedControlledRouteAnimation()
        }
    }

    private fun startSpeedControlledRouteAnimation() {
        routeAnimationJob?.cancel()

        val currentState = _riderState.value
        val visitedPoints = currentState.visitedRoutePoints
        val remainingPoints = currentState.remainingRoutePoints

        if (visitedPoints.isEmpty()) {
            if (remainingPoints.isNotEmpty()) {
                updateRiderState { state -> state.copy(animatedLocation = remainingPoints.first()) }
                setPredictiveHeading(remainingPoints)
            }
            return
        }

        routeAnimationJob = scope.launch {
            isAnimatingRoute = true
            animateAlongVisitedRouteWithSpeed(visitedPoints, 0)

            if (remainingPoints.isNotEmpty()) {
                animateToPositionWithSpeed(remainingPoints.first())
                setPredictiveHeading(remainingPoints)
            }
            lastAnimatedVisitedPointsCount = visitedPoints.size
        }
    }

    private suspend fun animateAlongVisitedRouteWithSpeed(visitedPoints: List<LatLng>, startIndex: Int) {
        for (i in startIndex until visitedPoints.size) {
            val targetPoint = visitedPoints[i]
            animateToPositionWithSpeed(targetPoint)
            if (i < visitedPoints.size - 1) {
                updateRiderHeading(targetPoint, visitedPoints[i + 1])
            }
            delay(calculateInterPointDelay())
        }
    }

    private suspend fun animateToPositionWithSpeed(targetPosition: LatLng) {
        val currentPosition = _riderState.value.animatedLocation ?: targetPosition
        val distance = RouteUtils.calculateDistance(
            currentPosition.latitude, currentPosition.longitude,
            targetPosition.latitude, targetPosition.longitude
        )

        if (distance < 1.0) {
            updateRiderState { state -> state.copy(animatedLocation = targetPosition) }
            return
        }

        val timing = calculateAnimationTiming(distance)
        for (step in 1..timing.steps) {
            val progress = step.toFloat() / timing.steps.toFloat()
            val interpolatedPosition = interpolateLocation(currentPosition, targetPosition, progress)
            updateRiderState { state -> state.copy(animatedLocation = interpolatedPosition) }
            if (step < timing.steps) delay(timing.stepDelayMs)
        }
        updateRiderState { state -> state.copy(animatedLocation = targetPosition) }
    }

    private fun calculateAnimationTiming(distanceMeters: Double): AnimationTiming {
        if (distanceMeters <= 0) return AnimationTiming(steps = 1, stepDelayMs = 50, totalDurationMs = 50)
        val speedMeterPerSecond = riderSpeedKmh * 1000 / 3600
        val travelTimeMs = ((distanceMeters / speedMeterPerSecond) * 1000).toLong()
        val targetFrameDelayMs = 50L
        val calculatedFrames = (travelTimeMs / targetFrameDelayMs).coerceIn(2, 60)
        val actualStepDelay = travelTimeMs / calculatedFrames
        return AnimationTiming(steps = calculatedFrames.toInt(), stepDelayMs = actualStepDelay, totalDurationMs = travelTimeMs)
    }

    private fun calculateInterPointDelay(): Long {
        val baseDelayMs = 100L
        val speedFactor = 25f / riderSpeedKmh
        return (baseDelayMs * speedFactor).toLong().coerceIn(20L, 300L)
    }

    private fun calculateCompletionAnimationTiming(distanceMeters: Double): AnimationTiming {
        if (distanceMeters <= 0) return AnimationTiming(steps = 1, stepDelayMs = 50, totalDurationMs = 50)
        val speedMeterPerSecond = completionAnimationSpeedKmh * 1000 / 3600
        val travelTimeMs = ((distanceMeters / speedMeterPerSecond) * 1000).toLong()
        val targetFrameDelayMs = 50L
        val calculatedFrames = (travelTimeMs / targetFrameDelayMs).coerceIn(2, 80)
        val actualStepDelay = travelTimeMs / calculatedFrames
        return AnimationTiming(steps = calculatedFrames.toInt(), stepDelayMs = actualStepDelay, totalDurationMs = travelTimeMs)
    }

    private fun updateRiderHeading(fromLocation: LatLng, toLocation: LatLng) {
        val newBearing = RouteUtils.calculateBearing(fromLocation, toLocation)
        val currentHeading = _riderState.value.heading
        val headingDifference = newBearing - currentHeading
        val normalizedDifference = when {
            headingDifference > GoogleMapConstants.BEARING_WRAPAROUND_THRESHOLD -> headingDifference - GoogleMapConstants.FULL_CIRCLE_DEGREES
            headingDifference < -GoogleMapConstants.BEARING_WRAPAROUND_THRESHOLD -> headingDifference + GoogleMapConstants.FULL_CIRCLE_DEGREES
            else -> headingDifference
        }

        if (abs(normalizedDifference) > GoogleMapConstants.MIN_HEADING_CHANGE_DEGREES) {
            val smoothingFactor = 0.7
            val smoothedHeading = currentHeading + (normalizedDifference * smoothingFactor)
            val normalizedHeading = when {
                smoothedHeading >= 360.0 -> smoothedHeading - 360.0
                smoothedHeading < 0.0 -> smoothedHeading + 360.0
                else -> smoothedHeading
            }
            updateRiderState { state -> state.copy(heading = normalizedHeading) }
        }
    }

    private fun setPredictiveHeading(remainingRoutePoints: List<LatLng>) {
        val currentState = _riderState.value
        val currentPosition = currentState.animatedLocation ?: return
        if (remainingRoutePoints.size <= 1) return
        val predictiveBearing = RouteUtils.calculateBearing(currentPosition, remainingRoutePoints[1])
        updateRiderState { state -> state.copy(heading = predictiveBearing.toDouble()) }
    }

    private fun interpolateLocation(from: LatLng, to: LatLng, progress: Float): LatLng {
        val clampedProgress = progress.coerceIn(0f, 1f)
        return LatLng(
            latitude = from.latitude + (to.latitude - from.latitude) * clampedProgress,
            longitude = from.longitude + (to.longitude - from.longitude) * clampedProgress
        )
    }

    private fun updateRiderState(update: (RiderAnimationState) -> RiderAnimationState) {
        _riderState.value = update(_riderState.value)
    }
}

data class RiderAnimationState(
    val animatedLocation: LatLng? = null,
    val rawLocation: LatLng? = null,
    val heading: Double = 0.0,
    val visitedRoutePoints: List<LatLng> = emptyList(),
    val remainingRoutePoints: List<LatLng> = emptyList(),
    val riderTrail: List<LatLng> = emptyList(),
    val isActive: Boolean = false,
    val isAnimating: Boolean = false
)

data class AnimationTiming(
    val steps: Int,
    val stepDelayMs: Long,
    val totalDurationMs: Long
)
