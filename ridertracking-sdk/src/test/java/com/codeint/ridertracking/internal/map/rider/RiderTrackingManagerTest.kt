package com.codeint.ridertracking.internal.map.rider

import com.codeint.ridertracking.internal.map.*
import com.codeint.ridertracking.testutil.FakeGoogleMapRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiderTrackingManagerTest {

    private lateinit var useCase: GoogleMapUseCase
    private lateinit var manager: RiderTrackingManager

    private val route = (0..20).map { LatLng(12.0 + it * 0.001, 77.0) }

    @Before
    fun setUp() {
        val fakeRepo = FakeGoogleMapRepository()
        useCase = GoogleMapUseCase(fakeRepo, RouteCache())
        manager = RiderTrackingManager(useCase)
    }

    // ================================
    // updateRoutePruning
    // ================================

    @Test
    fun `updateRoutePruning - empty remaining points - returns null`() {
        val result = manager.updateRoutePruning(LatLng(12.0, 77.0), null, "order1", emptyList())
        assertNull(result)
    }

    @Test
    fun `updateRoutePruning - valid route - splits visited and remaining`() {
        val riderLocation = route[10] // middle of route
        val result = manager.updateRoutePruning(riderLocation, null, "order1", route)
        assertNotNull(result)
        assertTrue("Should have visited points", result!!.visitedPoints.isNotEmpty())
        assertTrue("Should have remaining points", result.remainingPoints.isNotEmpty())
    }

    @Test
    fun `updateRoutePruning - progress is monotonic - never decreases`() {
        // First update at middle
        manager.updateRoutePruning(route[10], null, "order1", route)
        val state1 = manager.trackingState.value.currentSegmentProgress

        // Second update at earlier point (backward movement)
        manager.updateRoutePruning(route[5], null, "order1", route)
        val state2 = manager.trackingState.value.currentSegmentProgress

        assertTrue("Progress should not decrease: $state2 >= $state1", state2 >= state1)
    }

    // ================================
    // shouldUpdatePruning
    // ================================

    @Test
    fun `shouldUpdatePruning - no last location - returns true`() {
        assertTrue(manager.shouldUpdatePruning(LatLng(12.0, 77.0)))
    }

    @Test
    fun `shouldUpdatePruning - small movement - returns false`() {
        val location = LatLng(12.0, 77.0)
        manager.updatePruningLocation(location)
        // Very tiny movement (< MIN_PRUNING_DISTANCE_METERS)
        assertFalse(manager.shouldUpdatePruning(LatLng(12.00001, 77.0)))
    }

    @Test
    fun `shouldUpdatePruning - sufficient movement - returns true`() {
        manager.updatePruningLocation(LatLng(12.0, 77.0))
        // Large movement (~1km)
        assertTrue(manager.shouldUpdatePruning(LatLng(12.01, 77.0)))
    }

    // ================================
    // checkRouteDeviation
    // ================================

    @Test
    fun `checkRouteDeviation - empty route - returns not deviated`() {
        val result = manager.checkRouteDeviation(LatLng(12.0, 77.0), emptyList())
        assertFalse(result.isDeviated)
    }

    @Test
    fun `checkRouteDeviation - deviated - updates tracking state`() {
        val result = manager.checkRouteDeviation(LatLng(12.05, 77.0), route, 100.0)
        assertTrue(result.isDeviated)
        assertTrue(manager.trackingState.value.isRiderDeviated)
    }

    @Test
    fun `checkRouteDeviation - significant deviation - sets isSignificant`() {
        // Place rider very far from route
        val result = manager.checkRouteDeviation(LatLng(13.0, 78.0), route, 100.0)
        assertTrue(result.isDeviated)
        assertTrue(result.isSignificant)
    }

    // ================================
    // resetForNewRoute
    // ================================

    @Test
    fun `resetForNewRoute - clears all state`() {
        manager.updateRoutePruning(route[10], null, "order1", route)
        manager.resetForNewRoute()
        val state = manager.trackingState.value
        assertEquals(0f, state.currentSegmentProgress, 0.01f)
        assertTrue(state.visitedRoutePoints.isEmpty())
        assertNull(state.lastValidLocation)
    }
}
