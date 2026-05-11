package com.codeint.ridertracking.internal.map

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RouteUtilsTest {

    private val delta = 0.01 // tolerance for double comparisons
    private val degreeDelta = 2.0 // tolerance for bearing comparisons

    // ================================
    // calculateDistance
    // ================================

    @Test
    fun `calculateDistance - known coordinates - returns accurate distance`() {
        // Bangalore: ~1.2km apart
        val distance = RouteUtils.calculateDistance(12.9170491, 77.6729254, 12.925499916077, 77.66960144043)
        assertTrue("Distance should be ~1000-1500m, was $distance", distance in 800.0..2000.0)
    }

    @Test
    fun `calculateDistance - same point - returns zero`() {
        val distance = RouteUtils.calculateDistance(12.9, 77.6, 12.9, 77.6)
        assertEquals(0.0, distance, delta)
    }

    @Test
    fun `calculateDistance - antipodal points - returns half earth circumference`() {
        val distance = RouteUtils.calculateDistance(90.0, 0.0, -90.0, 0.0)
        assertTrue("Pole-to-pole should be ~20000km, was ${distance / 1000}km", distance in 19_900_000.0..20_100_000.0)
    }

    // ================================
    // calculateBearing
    // ================================

    @Test
    fun `calculateBearing - due north - returns approximately zero`() {
        val bearing = RouteUtils.calculateBearing(LatLng(10.0, 77.0), LatLng(11.0, 77.0))
        assertTrue("Bearing north should be ~0, was $bearing", bearing < 5f || bearing > 355f)
    }

    @Test
    fun `calculateBearing - due east - returns approximately 90`() {
        val bearing = RouteUtils.calculateBearing(LatLng(10.0, 77.0), LatLng(10.0, 78.0))
        assertEquals(90.0, bearing.toDouble(), degreeDelta)
    }

    @Test
    fun `calculateBearing - result is always in 0 to 360 range`() {
        val bearing = RouteUtils.calculateBearing(LatLng(10.0, 77.0), LatLng(9.0, 76.0))
        assertTrue("Bearing should be in [0,360), was $bearing", bearing >= 0f && bearing < 360f)
    }

    // ================================
    // getClosestPointOnLineSegment
    // ================================

    @Test
    fun `getClosestPointOnLineSegment - point projects onto segment - returns projection`() {
        val result = RouteUtils.getClosestPointOnLineSegment(
            LatLng(1.0, 0.5), LatLng(0.0, 0.0), LatLng(0.0, 1.0)
        )
        assertEquals(0.0, result.latitude, delta)
        assertEquals(0.5, result.longitude, delta)
    }

    @Test
    fun `getClosestPointOnLineSegment - point before start - returns start`() {
        val start = LatLng(0.0, 0.0)
        val result = RouteUtils.getClosestPointOnLineSegment(
            LatLng(0.0, -1.0), start, LatLng(0.0, 1.0)
        )
        assertEquals(start, result)
    }

    @Test
    fun `getClosestPointOnLineSegment - point beyond end - returns end`() {
        val end = LatLng(0.0, 1.0)
        val result = RouteUtils.getClosestPointOnLineSegment(
            LatLng(0.0, 2.0), LatLng(0.0, 0.0), end
        )
        assertEquals(end, result)
    }

    @Test
    fun `getClosestPointOnLineSegment - degenerate segment - returns start`() {
        val point = LatLng(5.0, 5.0)
        val result = RouteUtils.getClosestPointOnLineSegment(point, point, point)
        assertEquals(point, result)
    }

    // ================================
    // calculateDistanceToPolyline
    // ================================

    @Test
    fun `calculateDistanceToPolyline - empty polyline - returns max value`() {
        assertEquals(Double.MAX_VALUE, RouteUtils.calculateDistanceToPolyline(LatLng(0.0, 0.0), emptyList()), delta)
    }

    @Test
    fun `calculateDistanceToPolyline - single point - returns point distance`() {
        val dist = RouteUtils.calculateDistanceToPolyline(LatLng(0.0, 0.0), listOf(LatLng(0.001, 0.0)))
        assertTrue("Distance should be >0, was $dist", dist > 0)
    }

    @Test
    fun `calculateDistanceToPolyline - multi segment - returns minimum distance`() {
        val polyline = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(0.0, 2.0))
        val point = LatLng(0.001, 1.0) // very close to middle point
        val dist = RouteUtils.calculateDistanceToPolyline(point, polyline)
        assertTrue("Should be very close, was $dist", dist < 200.0)
    }

    // ================================
    // isRiderDeviated
    // ================================

    @Test
    fun `isRiderDeviated - within threshold - returns false`() {
        val route = listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0))
        val (deviated, _) = RouteUtils.isRiderDeviated(LatLng(12.0005, 77.0), route, 100.0)
        assertFalse(deviated)
    }

    @Test
    fun `isRiderDeviated - beyond threshold - returns true`() {
        val route = listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0))
        val (deviated, _) = RouteUtils.isRiderDeviated(LatLng(12.01, 77.0), route, 100.0)
        assertTrue(deviated)
    }

    @Test
    fun `isRiderDeviated - custom threshold - is respected`() {
        val route = listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0))
        val rider = LatLng(12.003, 77.0) // ~330m away
        val (deviated500, _) = RouteUtils.isRiderDeviated(rider, route, 500.0)
        val (deviated100, _) = RouteUtils.isRiderDeviated(rider, route, 100.0)
        assertFalse(deviated500)
        assertTrue(deviated100)
    }

    // ================================
    // interpolate
    // ================================

    @Test
    fun `interpolate - progress zero - returns start`() {
        val start = LatLng(10.0, 20.0)
        val end = LatLng(30.0, 40.0)
        assertEquals(start, RouteUtils.interpolate(start, end, 0f))
    }

    @Test
    fun `interpolate - progress one - returns end`() {
        val start = LatLng(10.0, 20.0)
        val end = LatLng(30.0, 40.0)
        assertEquals(end, RouteUtils.interpolate(start, end, 1f))
    }

    @Test
    fun `interpolate - out of range - is clamped`() {
        val start = LatLng(10.0, 20.0)
        val end = LatLng(30.0, 40.0)
        assertEquals(start, RouteUtils.interpolate(start, end, -5f))
        assertEquals(end, RouteUtils.interpolate(start, end, 10f))
    }

    @Test
    fun `interpolate - half progress - returns midpoint`() {
        val result = RouteUtils.interpolate(LatLng(0.0, 0.0), LatLng(10.0, 10.0), 0.5f)
        assertEquals(5.0, result.latitude, delta)
        assertEquals(5.0, result.longitude, delta)
    }

    // ================================
    // isWithinRadius
    // ================================

    @Test
    fun `isWithinRadius - inside - returns true`() {
        assertTrue(RouteUtils.isWithinRadius(LatLng(12.0, 77.0), LatLng(12.0001, 77.0), 50.0))
    }

    @Test
    fun `isWithinRadius - outside - returns false`() {
        assertFalse(RouteUtils.isWithinRadius(LatLng(12.0, 77.0), LatLng(12.01, 77.0), 50.0))
    }

    @Test
    fun `isWithinRadius - exactly on boundary - returns true`() {
        // ~111m apart (0.001 degree latitude)
        val center = LatLng(0.0, 0.0)
        val point = LatLng(0.001, 0.0)
        val dist = RouteUtils.calculateDistance(center.latitude, center.longitude, point.latitude, point.longitude)
        assertTrue(RouteUtils.isWithinRadius(center, point, dist)) // exactly on boundary
    }

    // ================================
    // isValidLocation
    // ================================

    @Test
    fun `isValidLocation - invalid latitude - returns false`() {
        assertFalse(RouteUtils.isValidLocation(LatLng(91.0, 77.0), null, null))
        assertFalse(RouteUtils.isValidLocation(LatLng(-91.0, 77.0), null, null))
    }

    @Test
    fun `isValidLocation - invalid longitude - returns false`() {
        assertFalse(RouteUtils.isValidLocation(LatLng(12.0, 181.0), null, null))
        assertFalse(RouteUtils.isValidLocation(LatLng(12.0, -181.0), null, null))
    }

    @Test
    fun `isValidLocation - null island - returns false`() {
        assertFalse(RouteUtils.isValidLocation(LatLng(0.0, 0.0), null, null))
    }

    @Test
    fun `isValidLocation - too far from source and destination - returns false`() {
        val source = LatLng(12.0, 77.0)
        val dest = LatLng(12.01, 77.01)
        val farAway = LatLng(20.0, 80.0) // very far
        assertFalse(RouteUtils.isValidLocation(farAway, source, dest))
    }

    @Test
    fun `isValidLocation - null source and destination - skips distance check`() {
        assertTrue(RouteUtils.isValidLocation(LatLng(12.0, 77.0), null, null))
    }

    @Test
    fun `isValidLocation - valid location near source - returns true`() {
        val source = LatLng(12.0, 77.0)
        val dest = LatLng(12.01, 77.01)
        assertTrue(RouteUtils.isValidLocation(LatLng(12.005, 77.005), source, dest))
    }

    // ================================
    // calculateSegmentProgress
    // ================================

    @Test
    fun `calculateSegmentProgress - empty segment - returns zero`() {
        assertEquals(0f, RouteUtils.calculateSegmentProgress(LatLng(0.0, 0.0), emptyList()), 0.01f)
    }

    @Test
    fun `calculateSegmentProgress - single point - returns one`() {
        assertEquals(1f, RouteUtils.calculateSegmentProgress(LatLng(0.0, 0.0), listOf(LatLng(0.0, 0.0))), 0.01f)
    }

    @Test
    fun `calculateSegmentProgress - rider at start - returns approximately zero`() {
        val route = (0..10).map { LatLng(12.0 + it * 0.001, 77.0) }
        val progress = RouteUtils.calculateSegmentProgress(route.first(), route)
        assertTrue("Progress at start should be ~0, was $progress", progress < 0.15f)
    }

    @Test
    fun `calculateSegmentProgress - rider at end - returns approximately one`() {
        val route = (0..10).map { LatLng(12.0 + it * 0.001, 77.0) }
        val progress = RouteUtils.calculateSegmentProgress(route.last(), route)
        assertTrue("Progress at end should be ~1, was $progress", progress > 0.85f)
    }

    @Test
    fun `calculateSegmentProgress - rider in middle - returns proportional progress`() {
        val route = (0..10).map { LatLng(12.0 + it * 0.001, 77.0) }
        val progress = RouteUtils.calculateSegmentProgress(route[5], route)
        assertTrue("Progress at middle should be ~0.5, was $progress", progress in 0.3f..0.7f)
    }

    // ================================
    // validateRouteSegment
    // ================================

    @Test
    fun `validateRouteSegment - empty or too short - returns fallback points`() {
        val start = LatLng(12.0, 77.0)
        val end = LatLng(12.01, 77.01)
        val result = RouteUtils.validateRouteSegment(emptyList(), start, end)
        assertTrue("Fallback should have 21 points, had ${result.size}", result.size == 21)
    }

    @Test
    fun `validateRouteSegment - few points - interpolates to minimum`() {
        val points = (0..5).map { LatLng(12.0 + it * 0.001, 77.0) }
        val result = RouteUtils.validateRouteSegment(points, points.first(), points.last())
        assertTrue("Should be interpolated to >= 25, had ${result.size}", result.size >= 25)
    }

    @Test
    fun `validateRouteSegment - sufficient points - returns unmodified`() {
        val points = (0..20).map { LatLng(12.0 + it * 0.001, 77.0) }
        val result = RouteUtils.validateRouteSegment(points, points.first(), points.last())
        assertEquals(points.size, result.size)
    }

    // ================================
    // Camera bounds methods
    // ================================

    @Test
    fun `calculateTightBoundsWithRoute - empty points - returns origin bounds`() {
        val (sw, ne) = RouteUtils.calculateTightBoundsWithRoute(null, emptyList(), emptyList(), LatLng(0.0, 0.0), false)
        // destination alone should still produce bounds
        assertTrue(sw.latitude < ne.latitude || (sw.latitude == 0.0 - GoogleMapConstants.TIGHT_FOCUS_BOUNDS_MARGIN))
    }

    @Test
    fun `calculateTightBoundsWithRoute - no stores rider active - uses super tight margin`() {
        val rider = LatLng(12.0, 77.0)
        val dest = LatLng(12.01, 77.01)
        val (sw, ne) = RouteUtils.calculateTightBoundsWithRoute(rider, emptyList(), emptyList(), dest, true)
        val latMargin = abs(sw.latitude - 12.0)
        assertTrue("Margin should be close to SUPER_TIGHT, was $latMargin",
            latMargin < GoogleMapConstants.TIGHT_FOCUS_BOUNDS_MARGIN + 0.001)
    }

    @Test
    fun `calculateCompletedOrderBounds - returns symmetric bounds around destination`() {
        val dest = LatLng(12.0, 77.0)
        val (sw, ne) = RouteUtils.calculateCompletedOrderBounds(dest)
        assertEquals(dest.latitude - sw.latitude, ne.latitude - dest.latitude, 0.0001)
    }

    // ================================
    // detectMultiStopDeviation
    // ================================

    @Test
    fun `detectMultiStopDeviation - null segment - returns false zero`() {
        val (deviated, dist) = RouteUtils.detectMultiStopDeviation(LatLng(12.0, 77.0), null)
        assertFalse(deviated)
        assertEquals(0.0, dist, delta)
    }

    @Test
    fun `detectMultiStopDeviation - deviated - returns true`() {
        val segment = RouteSegment("s1", listOf(LatLng(12.0, 77.0), LatLng(12.001, 77.0)), true)
        val (deviated, _) = RouteUtils.detectMultiStopDeviation(LatLng(12.01, 77.0), segment, 100.0)
        assertTrue(deviated)
    }

    // ================================
    // isRiderAtStore
    // ================================

    @Test
    fun `isRiderAtStore - within arrival radius - returns true`() {
        assertTrue(RouteUtils.isRiderAtStore(LatLng(12.0, 77.0), LatLng(12.0001, 77.0)))
    }

    @Test
    fun `isRiderAtStore - outside radius - returns false`() {
        assertFalse(RouteUtils.isRiderAtStore(LatLng(12.0, 77.0), LatLng(12.01, 77.0)))
    }
}
