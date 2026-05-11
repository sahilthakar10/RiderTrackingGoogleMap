package com.codeint.ridertracking.internal.map

import kotlin.math.*

object RouteUtils {

    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLng = (lng2 - lng1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = from.latitude * PI / 180
        val lat2 = to.latitude * PI / 180
        val deltaLng = (to.longitude - from.longitude) * PI / 180
        val y = sin(deltaLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
        val bearing = atan2(y, x)
        return ((bearing * 180 / PI + 360) % 360).toFloat()
    }

    fun getClosestPointOnLineSegment(
        point: LatLng,
        lineStart: LatLng,
        lineEnd: LatLng
    ): LatLng {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        if (dx == 0.0 && dy == 0.0) return lineStart
        val t = ((point.longitude - lineStart.longitude) * dx +
            (point.latitude - lineStart.latitude) * dy) / (dx * dx + dy * dy)
        return when {
            t < 0 -> lineStart
            t > 1 -> lineEnd
            else -> LatLng(lineStart.latitude + t * dy, lineStart.longitude + t * dx)
        }
    }

    fun calculateDistanceToPolyline(point: LatLng, polylinePoints: List<LatLng>): Double {
        if (polylinePoints.isEmpty()) return Double.MAX_VALUE
        if (polylinePoints.size == 1) {
            return calculateDistance(point.latitude, point.longitude, polylinePoints[0].latitude, polylinePoints[0].longitude)
        }
        var minDistance = Double.MAX_VALUE
        for (i in 0 until polylinePoints.size - 1) {
            val closestPoint = getClosestPointOnLineSegment(point, polylinePoints[i], polylinePoints[i + 1])
            val distance = calculateDistance(point.latitude, point.longitude, closestPoint.latitude, closestPoint.longitude)
            minDistance = minOf(minDistance, distance)
        }
        return minDistance
    }

    fun isRiderDeviated(
        riderLocation: LatLng,
        routePoints: List<LatLng>,
        deviationThreshold: Double = 100.0
    ): Pair<Boolean, Double> {
        val distanceToRoute = calculateDistanceToPolyline(riderLocation, routePoints)
        return Pair(distanceToRoute > deviationThreshold, distanceToRoute)
    }

    fun interpolate(start: LatLng, end: LatLng, progress: Float): LatLng {
        val clampedProgress = progress.coerceIn(0f, 1f)
        return LatLng(
            start.latitude + (end.latitude - start.latitude) * clampedProgress,
            start.longitude + (end.longitude - start.longitude) * clampedProgress
        )
    }

    fun isWithinRadius(center: LatLng, point: LatLng, radiusMeters: Double): Boolean {
        val distance = calculateDistance(center.latitude, center.longitude, point.latitude, point.longitude)
        return distance <= radiusMeters
    }

    fun isValidLocation(location: LatLng, sourceLocation: LatLng?, destinationLocation: LatLng?): Boolean {
        if (location.latitude !in GoogleMapConstants.MIN_LATITUDE..GoogleMapConstants.MAX_LATITUDE ||
            location.longitude !in GoogleMapConstants.MIN_LONGITUDE..GoogleMapConstants.MAX_LONGITUDE
        ) return false
        if (location.latitude == GoogleMapConstants.NULL_ISLAND_LATITUDE &&
            location.longitude == GoogleMapConstants.NULL_ISLAND_LONGITUDE
        ) return false
        if (sourceLocation != null && destinationLocation != null) {
            val maxDistanceFromRoute = GoogleMapConstants.MAX_DISTANCE_FROM_ROUTE_METERS
            val distanceFromStore = calculateDistance(location.latitude, location.longitude, sourceLocation.latitude, sourceLocation.longitude)
            val distanceFromDestination = calculateDistance(location.latitude, location.longitude, destinationLocation.latitude, destinationLocation.longitude)
            if (distanceFromStore > maxDistanceFromRoute && distanceFromDestination > maxDistanceFromRoute) return false
        }
        return true
    }

    fun calculateSegmentProgress(
        riderLocation: LatLng,
        segmentPoints: List<LatLng>,
        lastLocation: LatLng? = null
    ): Float {
        if (segmentPoints.isEmpty()) return 0f
        if (segmentPoints.size == 1) return 1f
        val closestIndex = findClosestPointIndex(riderLocation, segmentPoints, lastLocation)
        return closestIndex.toFloat() / maxOf(1, segmentPoints.size - 1).toFloat()
    }

    private fun findClosestPointIndex(location: LatLng, routePoints: List<LatLng>, lastValidLocation: LatLng?): Int {
        if (routePoints.isEmpty()) return 0
        val lastIndex = lastValidLocation?.let { findClosestPointIndexSimple(it, routePoints) } ?: 0
        val isLargeJump = lastValidLocation?.let {
            calculateDistance(location.latitude, location.longitude, it.latitude, it.longitude) > 50.0
        } ?: true

        return if (isLargeJump) {
            findClosestPointIndexSimple(location, routePoints)
        } else {
            val searchRange = GoogleMapConstants.ROUTE_SEARCH_RANGE_POINTS
            val searchStart = maxOf(0, lastIndex - searchRange)
            val searchEnd = minOf(routePoints.size, lastIndex + searchRange)
            var minDistance = Double.MAX_VALUE
            var closestIndex = 0
            for (i in searchStart until searchEnd) {
                val distance = calculateDistance(location.latitude, location.longitude, routePoints[i].latitude, routePoints[i].longitude)
                if (distance < minDistance) {
                    minDistance = distance
                    closestIndex = i
                }
            }
            closestIndex
        }
    }

    private fun findClosestPointIndexSimple(location: LatLng, routePoints: List<LatLng>): Int {
        var minDistance = Double.MAX_VALUE
        var closestIndex = 0
        routePoints.forEachIndexed { index, point ->
            val distance = calculateDistance(location.latitude, location.longitude, point.latitude, point.longitude)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        return closestIndex
    }

    // Camera bounds methods

    fun calculateTightBoundsWithRoute(
        animatedRiderLocation: LatLng?,
        remainingRoutePoints: List<LatLng>,
        pendingStores: List<StoreLocation>,
        destination: LatLng,
        isRiderActive: Boolean
    ): Pair<LatLng, LatLng> {
        val relevantPoints = mutableListOf<LatLng>()
        animatedRiderLocation?.let { relevantPoints.add(it) }
        relevantPoints.addAll(remainingRoutePoints)
        relevantPoints.addAll(pendingStores.map { it.location })
        relevantPoints.add(destination)
        if (relevantPoints.isEmpty()) return Pair(LatLng(0.0, 0.0), LatLng(0.0, 0.0))

        val margin = when {
            pendingStores.isEmpty() && animatedRiderLocation != null -> GoogleMapConstants.SUPER_TIGHT_FOCUS_MARGIN
            isRiderActive -> GoogleMapConstants.RIDER_FOCUS_BOUNDS_MARGIN
            else -> GoogleMapConstants.TIGHT_FOCUS_BOUNDS_MARGIN
        }
        return calculateBoundsWithMargin(relevantPoints, margin)
    }

    fun calculateCompletionAnimationBounds(riderLocation: LatLng, destination: LatLng): Pair<LatLng, LatLng> {
        val circleRadiusMeters = GoogleMapConstants.DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS
        val paddingMeters = 50.0
        val totalRadiusMeters = circleRadiusMeters + paddingMeters
        val circleLatMargin = totalRadiusMeters / 111320.0
        val circleLngMargin = totalRadiusMeters / (111320.0 * cos(destination.latitude * PI / 180.0))

        val minLat = minOf(riderLocation.latitude, destination.latitude - circleLatMargin)
        val maxLat = maxOf(riderLocation.latitude, destination.latitude + circleLatMargin)
        val minLng = minOf(riderLocation.longitude, destination.longitude - circleLngMargin)
        val maxLng = maxOf(riderLocation.longitude, destination.longitude + circleLngMargin)
        val extraPadding = 0.0002
        return Pair(
            LatLng(minLat - extraPadding, minLng - extraPadding),
            LatLng(maxLat + extraPadding, maxLng + extraPadding)
        )
    }

    fun calculateCompletedOrderBounds(destination: LatLng): Pair<LatLng, LatLng> {
        val circleRadiusMeters = GoogleMapConstants.DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS
        val paddingMeters = 50.0
        val totalRadiusMeters = circleRadiusMeters + paddingMeters
        val latMargin = totalRadiusMeters / 111320.0
        val lngMargin = totalRadiusMeters / (111320.0 * cos(destination.latitude * PI / 180.0))
        return Pair(
            LatLng(destination.latitude - latMargin, destination.longitude - lngMargin),
            LatLng(destination.latitude + latMargin, destination.longitude + lngMargin)
        )
    }

    fun validateRouteSegment(
        segmentPoints: List<LatLng>,
        expectedStart: LatLng,
        expectedEnd: LatLng,
        segmentName: String = "Unknown"
    ): List<LatLng> {
        if (segmentPoints.isEmpty() || segmentPoints.size < 2) {
            return createFallbackSegmentPoints(expectedStart, expectedEnd, 20)
        }
        return if (segmentPoints.size < 15) {
            interpolateRoutePoints(segmentPoints, 25)
        } else {
            segmentPoints
        }
    }

    private fun createFallbackSegmentPoints(start: LatLng, end: LatLng, numPoints: Int): List<LatLng> =
        (0..numPoints).map { i ->
            val progress = i.toFloat() / numPoints
            LatLng(
                latitude = start.latitude + (end.latitude - start.latitude) * progress,
                longitude = start.longitude + (end.longitude - start.longitude) * progress
            )
        }

    private fun interpolateRoutePoints(originalPoints: List<LatLng>, targetPointCount: Int): List<LatLng> {
        if (originalPoints.size >= targetPointCount) return originalPoints
        val interpolatedPoints = mutableListOf<LatLng>()
        val segmentCount = targetPointCount - 1
        for (i in 0..segmentCount) {
            val progress = i.toFloat() / segmentCount
            val scaledIndex = progress * (originalPoints.size - 1)
            val baseIndex = scaledIndex.toInt()
            if (baseIndex >= originalPoints.size - 1) {
                interpolatedPoints.add(originalPoints.last())
            } else {
                val segmentProgress = scaledIndex - baseIndex
                interpolatedPoints.add(interpolate(originalPoints[baseIndex], originalPoints[baseIndex + 1], segmentProgress))
            }
        }
        return interpolatedPoints
    }

    fun detectMultiStopDeviation(
        riderLocation: LatLng,
        activeSegment: RouteSegment?,
        deviationThreshold: Double = GoogleMapConstants.MULTI_STOP_DEVIATION_THRESHOLD_METERS
    ): Pair<Boolean, Double> {
        if (activeSegment == null) return Pair(false, 0.0)
        return isRiderDeviated(riderLocation, activeSegment.routePoints, deviationThreshold)
    }

    fun isRiderAtStore(
        riderLocation: LatLng,
        storeLocation: LatLng,
        arrivalRadius: Double = GoogleMapConstants.STORE_ARRIVAL_THRESHOLD_METERS
    ): Boolean = isWithinRadius(storeLocation, riderLocation, arrivalRadius)

    private fun calculateBoundsWithMargin(points: List<LatLng>, margin: Double): Pair<LatLng, LatLng> {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        return Pair(
            LatLng(minLat - margin, minLng - margin),
            LatLng(maxLat + margin, maxLng + margin)
        )
    }
}
