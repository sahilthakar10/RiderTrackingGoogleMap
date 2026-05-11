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

    @Test
    fun `setRiderSpeed - clamps to range`() {
        controller.setRiderSpeed(1f)
        assertEquals(5f, controller.getRiderSpeed(), 0.01f)

        controller.setRiderSpeed(500f)
        assertEquals(300f, controller.getRiderSpeed(), 0.01f)

        controller.setRiderSpeed(30f)
        assertEquals(30f, controller.getRiderSpeed(), 0.01f)
    }

    @Test
    fun `getRiderSpeed - returns current speed`() {
        assertTrue(controller.getRiderSpeed() > 0)
    }

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
        assertNull(controller.riderState.value.animatedLocation)
    }

    @Test
    fun `initializeRiderPosition - sets heading towards next point`() {
        controller.initializeRiderPosition(sampleRoute)
        // Route goes due north (increasing latitude, same longitude)
        // Bearing should be approximately 0 degrees (north)
        val heading = controller.riderState.value.heading
        assertTrue("Heading should be near 0 (north), was $heading", heading < 10.0 || heading > 350.0)
    }

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

    @Test
    fun `animateDirectToDestination - null current position - positions immediately`() {
        val dest = LatLng(12.9, 77.6)
        controller.animateDirectToDestination(dest)
        assertEquals(dest, controller.riderState.value.animatedLocation)
    }

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
        assertEquals(sampleRoute.first(), controller.riderState.value.animatedLocation)
    }

    @Test
    fun `processRiderLocation - sets raw location and active`() {
        val location = LatLng(12.0, 77.0)
        controller.processRiderLocation(location, true, emptyList(), sampleRoute)
        val state = controller.riderState.value
        assertEquals(location, state.rawLocation)
        assertTrue(state.isActive)
    }
}
