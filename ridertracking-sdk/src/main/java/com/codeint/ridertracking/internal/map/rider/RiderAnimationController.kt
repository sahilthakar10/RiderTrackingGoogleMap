package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Smooth rider animation using continuous time-based interpolation.
 * Targets 60 FPS for fluid movement like Uber/Swiggy.
 */
class RiderAnimationController(
    private val scope: CoroutineScope
) {

    private val _riderState = MutableStateFlow(RiderAnimationState())
    val riderState: StateFlow<RiderAnimationState> = _riderState.asStateFlow()

    private var animationJob: Job? = null

    // Target position the rider is smoothly moving towards
    private var targetLocation: LatLng? = null
    private var animationStartLocation: LatLng? = null
    private var animationStartTime: Long = 0L
    private var animationDurationMs: Long = 0L

    // Speed in km/h - controls how fast the rider icon moves
    private var riderSpeedKmh: Float = 240f

    private var lastLocationUpdateTime = 0L
    private val minLocationUpdateInterval = GoogleMapConstants.MIN_LOCATION_UPDATE_INTERVAL_MS

    companion object {
        private const val FRAME_DELAY_MS = 16L // 60 FPS
        private const val MIN_ANIMATION_DURATION_MS = 200L
        private const val MAX_ANIMATION_DURATION_MS = 5000L
    }

    fun setRiderSpeed(speedKmh: Float) {
        riderSpeedKmh = speedKmh.coerceIn(5f, 300f)
    }

    fun getRiderSpeed(): Float = riderSpeedKmh

    /**
     * Process a new rider location. Starts smooth animation from current position to new target.
     */
    fun processRiderLocation(
        riderLocation: LatLng,
        isOutForDelivery: Boolean,
        visitedRoutePoints: List<LatLng>,
        remainingRoutePoints: List<LatLng>
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLocationUpdateTime < minLocationUpdateInterval) return

        _riderState.value = _riderState.value.copy(
            rawLocation = riderLocation,
            isActive = true,
            visitedRoutePoints = visitedRoutePoints,
            remainingRoutePoints = remainingRoutePoints
        )

        if (isOutForDelivery) {
            smoothAnimateToLocation(riderLocation)
            updateHeadingSmooth(riderLocation)
        } else {
            initializeRiderPosition(remainingRoutePoints)
        }

        lastLocationUpdateTime = currentTime
    }

    /**
     * Core smooth animation - interpolates from current position to target over calculated duration.
     * Uses time-based interpolation at 60 FPS for fluid movement.
     */
    private fun smoothAnimateToLocation(target: LatLng) {
        val currentPos = _riderState.value.animatedLocation ?: target

        val distance = RouteUtils.calculateDistance(
            currentPos.latitude, currentPos.longitude,
            target.latitude, target.longitude
        )

        // Skip if barely moved
        if (distance < 0.5) return

        // Calculate animation duration based on distance and speed
        val speedMs = riderSpeedKmh * 1000.0 / 3600.0
        val travelDuration = ((distance / speedMs) * 1000).toLong()
            .coerceIn(MIN_ANIMATION_DURATION_MS, MAX_ANIMATION_DURATION_MS)

        animationStartLocation = currentPos
        targetLocation = target
        animationStartTime = System.currentTimeMillis()
        animationDurationMs = travelDuration

        // Start continuous animation if not already running
        if (animationJob?.isActive != true) {
            startContinuousAnimation()
        }
    }

    /**
     * Continuous 60 FPS animation loop.
     * Smoothly interpolates between start and target positions based on elapsed time.
     */
    private fun startContinuousAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            try {
                _riderState.value = _riderState.value.copy(isAnimating = true)

                while (isActive) {
                    val start = animationStartLocation ?: break
                    val target = targetLocation ?: break
                    val elapsed = System.currentTimeMillis() - animationStartTime
                    val rawProgress = if (animationDurationMs > 0) {
                        (elapsed.toFloat() / animationDurationMs).coerceIn(0f, 1f)
                    } else {
                        1f
                    }

                    // Smooth easing for natural movement
                    val easedProgress = easeInOutQuad(rawProgress)

                    val interpolated = LatLng(
                        latitude = start.latitude + (target.latitude - start.latitude) * easedProgress,
                        longitude = start.longitude + (target.longitude - start.longitude) * easedProgress
                    )

                    _riderState.value = _riderState.value.copy(animatedLocation = interpolated)

                    if (rawProgress >= 1f) {
                        // Arrived at target - snap to exact position and wait for next update
                        _riderState.value = _riderState.value.copy(animatedLocation = target)
                        break
                    }

                    delay(FRAME_DELAY_MS)
                }
            } catch (_: CancellationException) {
                // Normal cancellation
            } finally {
                _riderState.value = _riderState.value.copy(isAnimating = false)
            }
        }
    }

    /**
     * Quadratic ease-in-out for smooth acceleration and deceleration.
     */
    private fun easeInOutQuad(t: Float): Float =
        if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f

    /**
     * Cubic ease-in-out for completion animation.
     */
    private fun easeInOutCubic(progress: Float): Float = if (progress < 0.5f) {
        4f * progress * progress * progress
    } else {
        val p = progress - 1f
        1f + 4f * p * p * p
    }

    /**
     * Smoothly rotate heading towards the movement direction.
     */
    private fun updateHeadingSmooth(target: LatLng) {
        val current = _riderState.value.animatedLocation ?: return
        val distance = RouteUtils.calculateDistance(
            current.latitude, current.longitude, target.latitude, target.longitude
        )
        if (distance < 2.0) return // Don't update heading for tiny movements

        val newBearing = RouteUtils.calculateBearing(current, target).toDouble()
        val currentHeading = _riderState.value.heading

        var diff = newBearing - currentHeading
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360

        if (abs(diff) > 5.0) {
            // Smooth rotation - blend towards target heading
            val smoothed = currentHeading + diff * 0.4
            val normalized = ((smoothed % 360) + 360) % 360
            _riderState.value = _riderState.value.copy(heading = normalized)
        }
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

        _riderState.value = _riderState.value.copy(
            animatedLocation = initialPosition,
            rawLocation = initialPosition
        )

        // Set initial heading
        if (remainingRoutePoints.size > 1) {
            val bearing = RouteUtils.calculateBearing(initialPosition, remainingRoutePoints[1])
            _riderState.value = _riderState.value.copy(heading = bearing.toDouble())
        }
    }

    fun updateRiderTrail(location: LatLng) {
        val currentTrail = _riderState.value.riderTrail.toMutableList()
        currentTrail.add(location)
        if (currentTrail.size > GoogleMapConstants.RIDER_TRAIL_MAX_SIZE) {
            currentTrail.removeAt(0)
        }
        _riderState.value = _riderState.value.copy(riderTrail = currentTrail)
    }

    fun clearAnimation() {
        animationJob?.cancel()
        targetLocation = null
        animationStartLocation = null
        _riderState.value = RiderAnimationState()
    }

    fun stopAnimation() {
        animationJob?.cancel()
    }

    /**
     * Animate rider to destination for order completion - smooth 60 FPS with cubic easing.
     */
    fun animateDirectToDestination(destination: LatLng) {
        val currentPosition = _riderState.value.animatedLocation
            ?: _riderState.value.rawLocation

        if (currentPosition == null) {
            positionAtDestination(destination)
            return
        }

        animationJob?.cancel()
        animationJob = scope.launch {
            try {
                _riderState.value = _riderState.value.copy(isAnimating = true)

                val bearing = RouteUtils.calculateBearing(currentPosition, destination)
                _riderState.value = _riderState.value.copy(heading = bearing.toDouble())

                val distance = RouteUtils.calculateDistance(
                    currentPosition.latitude, currentPosition.longitude,
                    destination.latitude, destination.longitude
                )

                val speedMs = 100.0 * 1000.0 / 3600.0 // 100 km/h completion speed
                val duration = ((distance / speedMs) * 1000).toLong()
                    .coerceIn(500L, 8000L)

                val startTime = System.currentTimeMillis()

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                    val eased = easeInOutCubic(progress)

                    val pos = LatLng(
                        currentPosition.latitude + (destination.latitude - currentPosition.latitude) * eased,
                        currentPosition.longitude + (destination.longitude - currentPosition.longitude) * eased
                    )

                    _riderState.value = _riderState.value.copy(
                        animatedLocation = pos,
                        rawLocation = pos
                    )

                    if (progress >= 1f) break
                    delay(FRAME_DELAY_MS)
                }

                _riderState.value = _riderState.value.copy(
                    animatedLocation = destination,
                    rawLocation = destination,
                    isAnimating = false
                )
            } catch (_: CancellationException) {
            } finally {
                _riderState.value = _riderState.value.copy(isAnimating = false)
            }
        }
    }

    fun positionAtDestination(destination: LatLng) {
        animationJob?.cancel()
        _riderState.value = _riderState.value.copy(
            animatedLocation = destination, rawLocation = destination,
            isActive = true, isAnimating = false,
            visitedRoutePoints = emptyList(), remainingRoutePoints = emptyList()
        )
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
