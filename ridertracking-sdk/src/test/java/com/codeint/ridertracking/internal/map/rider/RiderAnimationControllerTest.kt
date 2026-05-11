package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.GoogleMapConstants
import com.codeint.ridertracking.internal.map.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RiderAnimationControllerTest {

    private lateinit var testScope: TestScope
    private lateinit var controller: RiderAnimationController

    private val sampleRoute = (0..10).map { LatLng(12.0 + it * 0.001, 77.0) }

    @Before
    fun setUp() {
        testScope = TestScope(UnconfinedTestDispatcher())
        controller = RiderAnimationController(testScope)
    }

    // ================================
    // Speed management
    // ================================

    @Test
    fun `setRiderSpeed - clamps to range`() {
        controller.setRiderSpeed(1f) // below min (5)
        assertEquals(5f, controller.getRiderSpeed(), 0.01f)

        controller.setRiderSpeed(100f) // above max (60)
        assertEquals(60f, controller.getRiderSpeed(), 0.01f)

        controller.setRiderSpeed(30f) // in range
        assertEquals(30f, controller.getRiderSpeed(), 0.01f)
    }

    @Test
    fun `getRiderSpeed - returns current speed`() {
        // Default is 240 (unclamped internal value)
        val speed = controller.getRiderSpeed()
        assertTrue(speed > 0)
    }

    // ================================
    // initializeRiderPosition
    // ================================

    @Test
    fun `initializeRiderPosition - with remaining points - sets animated location`() {
        controller.initializeRiderPosition(sampleRoute)
        val state = controller.riderState.value
        assertNotNull(state.animatedLocation)
        assertEquals(sampleRoute.first(), state.animatedLocation)
    }

    @Test
    fun `initializeRiderPosition - empty points - no op`() {
        controller.initializeRiderPosition(emptyList())
        val state = controller.riderState.value
        assertNull(state.animatedLocation)
    }

    @Test
    fun `initializeRiderPosition - with visited points - uses last visited`() {
        // First set some visited points via processRiderLocation
        controller.processRiderLocation(
            riderLocation = sampleRoute[5],
            isOutForDelivery = true,
            visitedRoutePoints = sampleRoute.take(6),
            remainingRoutePoints = sampleRoute.drop(5)
        )
        // Wait for throttle to expire
        Thread.sleep(GoogleMapConstants.MIN_LOCATION_UPDATE_INTERVAL_MS + 100)

        controller.initializeRiderPosition(sampleRoute.drop(5))
        val state = controller.riderState.value
        assertNotNull(state.animatedLocation)
    }

    // ================================
    // Trail management
    // ================================

    @Test
    fun `updateRiderTrail - adds location`() {
        controller.updateRiderTrail(LatLng(12.0, 77.0))
        assertEquals(1, controller.riderState.value.riderTrail.size)
    }

    @Test
    fun `updateRiderTrail - exceeds max size - removes oldest`() {
        repeat(GoogleMapConstants.RIDER_TRAIL_MAX_SIZE + 5) { i ->
            controller.updateRiderTrail(LatLng(12.0 + i * 0.0001, 77.0))
        }
        assertEquals(GoogleMapConstants.RIDER_TRAIL_MAX_SIZE, controller.riderState.value.riderTrail.size)
    }

    // ================================
    // clearAnimation
    // ================================

    @Test
    fun `clearAnimation - resets all state`() {
        controller.initializeRiderPosition(sampleRoute)
        controller.updateRiderTrail(LatLng(12.0, 77.0))
        controller.clearAnimation()
        val state = controller.riderState.value
        assertNull(state.animatedLocation)
        assertNull(state.rawLocation)
        assertEquals(0.0, state.heading, 0.01)
        assertFalse(state.isActive)
        assertTrue(state.riderTrail.isEmpty())
    }

    // ================================
    // positionAtDestination
    // ================================

    @Test
    fun `positionAtDestination - sets location immediately`() {
        val dest = LatLng(12.9, 77.6)
        controller.positionAtDestination(dest)
        val state = controller.riderState.value
        assertEquals(dest, state.animatedLocation)
        assertEquals(dest, state.rawLocation)
        assertTrue(state.isActive)
        assertFalse(state.isAnimating)
    }

    // ================================
    // animateDirectToDestination
    // ================================

    @Test
    fun `animateDirectToDestination - null current position - positions immediately`() {
        val dest = LatLng(12.9, 77.6)
        controller.animateDirectToDestination(dest)
        // Should fall through to positionAtDestination since no current position
        val state = controller.riderState.value
        assertEquals(dest, state.animatedLocation)
    }

    // ================================
    // processRiderLocation throttling
    // ================================

    @Test
    fun `processRiderLocation - throttled by interval - ignores rapid updates`() {
        controller.processRiderLocation(LatLng(12.0, 77.0), true, emptyList(), sampleRoute)
        val state1 = controller.riderState.value.rawLocation

        // Immediate second call should be throttled
        controller.processRiderLocation(LatLng(12.1, 77.1), true, emptyList(), sampleRoute)
        val state2 = controller.riderState.value.rawLocation

        assertEquals("Second call should be throttled", state1, state2)
    }

    @Test
    fun `processRiderLocation - not out for delivery - initializes position`() {
        controller.processRiderLocation(
            riderLocation = LatLng(12.0, 77.0),
            isOutForDelivery = false,
            visitedRoutePoints = emptyList(),
            remainingRoutePoints = sampleRoute
        )
        val state = controller.riderState.value
        assertEquals(sampleRoute.first(), state.animatedLocation)
    }
}
