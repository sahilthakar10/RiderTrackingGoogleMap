package com.codeint.ridertracking.internal.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RouteCacheTest {

    private lateinit var cache: RouteCache

    @Before
    fun setUp() {
        cache = RouteCache()
    }

    // ================================
    // Route CRUD
    // ================================

    @Test
    fun `getRoute - non existent key - returns null`() {
        assertNull(cache.getRoute("nonexistent"))
    }

    @Test
    fun `updateRoute then getRoute - returns stored route`() {
        val route = listOf(LatLng(12.0, 77.0), LatLng(12.1, 77.1))
        cache.updateRoute("order1", route)
        assertEquals(route, cache.getRoute("order1"))
    }

    @Test
    fun `updateRoute on existing entry - preserves lastLocation`() {
        val location = SimpleLocation(12.0, 77.0)
        cache.updateLastLocation("order1", location)
        cache.updateRoute("order1", listOf(LatLng(1.0, 1.0)))
        assertEquals(location, cache.getRiderData("order1")?.lastLocation)
    }

    @Test
    fun `updateLastLocation new entry - creates with null route`() {
        cache.updateLastLocation("order1", SimpleLocation(12.0, 77.0))
        assertNull(cache.getRoute("order1"))
        assertNotNull(cache.getRiderData("order1")?.lastLocation)
    }

    @Test
    fun `updateLastLocation existing entry - preserves route`() {
        val route = listOf(LatLng(1.0, 1.0))
        cache.updateRoute("order1", route)
        cache.updateLastLocation("order1", SimpleLocation(12.0, 77.0))
        assertEquals(route, cache.getRoute("order1"))
    }

    // ================================
    // Pruning state
    // ================================

    @Test
    fun `shouldPruneRoute - no cache entry - returns false`() {
        assertFalse(cache.shouldPruneRoute("nonexistent"))
    }

    @Test
    fun `shouldPruneRoute - has cache not pruned - returns true`() {
        cache.updateRoute("order1", listOf(LatLng(1.0, 1.0)))
        assertTrue(cache.shouldPruneRoute("order1"))
    }

    @Test
    fun `shouldPruneRoute - already pruned - returns false`() {
        cache.updateRoute("order1", listOf(LatLng(1.0, 1.0)))
        cache.markPruningComplete("order1")
        assertFalse(cache.shouldPruneRoute("order1"))
    }

    @Test
    fun `resetPruningState - after pruned - allows re-pruning`() {
        cache.updateRoute("order1", listOf(LatLng(1.0, 1.0)))
        cache.markPruningComplete("order1")
        cache.resetPruningState("order1")
        assertTrue(cache.shouldPruneRoute("order1"))
    }

    @Test
    fun `clearAllCache - empties everything`() {
        cache.updateRoute("order1", listOf(LatLng(1.0, 1.0)))
        cache.updateMultiStopData("order1", MultiStopCacheData(emptyList(), LatLng(0.0, 0.0)))
        cache.clearAllCache()
        assertNull(cache.getRoute("order1"))
    }

    // ================================
    // Segment cache
    // ================================

    @Test
    fun `cacheRouteSegment - exceeds cache size - evicts oldest`() {
        val seg1 = RouteSegment("a_seg", listOf(LatLng(1.0, 1.0)), isActive = true)
        val seg2 = RouteSegment("b_seg", listOf(LatLng(2.0, 2.0)), isActive = false)
        val seg3 = RouteSegment("c_seg", listOf(LatLng(3.0, 3.0)), isActive = false)
        cache.cacheRouteSegment("order1", seg1)
        cache.cacheRouteSegment("order1", seg2)
        cache.cacheRouteSegment("order1", seg3)
        // SEGMENT_CACHE_SIZE is 2, so "a_seg" (smallest id) should be evicted
        assertNull("a_seg should be evicted", cache.getActiveSegment("order1"))
    }

    @Test
    fun `getActiveSegment - returns only active segment`() {
        cache.cacheRouteSegment("order1", RouteSegment("s1", listOf(LatLng(1.0, 1.0)), isActive = false))
        cache.cacheRouteSegment("order1", RouteSegment("s2", listOf(LatLng(2.0, 2.0)), isActive = true))
        val active = cache.getActiveSegment("order1")
        assertNotNull(active)
        assertEquals("s2", active!!.segmentId)
    }

    @Test
    fun `getActiveSegment - no active segment - returns null`() {
        cache.cacheRouteSegment("order1", RouteSegment("s1", listOf(LatLng(1.0, 1.0)), isActive = false))
        assertNull(cache.getActiveSegment("order1"))
    }

    @Test
    fun `updateSegmentProgress - updates progress and active id`() {
        cache.cacheRouteSegment("order1", RouteSegment("s1", listOf(LatLng(1.0, 1.0)), isActive = false))
        cache.updateMultiStopData("order1", MultiStopCacheData(emptyList(), LatLng(0.0, 0.0)))
        cache.updateSegmentProgress("order1", "s1", 0.5f)
        // Segment should now be active
        assertNotNull(cache.getActiveSegment("order1"))
    }

    @Test
    fun `updateSegmentProgress - missing maps - no crash`() {
        // Should not throw
        cache.updateSegmentProgress("nonexistent", "seg", 0.5f)
    }
}
