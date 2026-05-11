package com.codeint.ridertracking.internal.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class GoogleMapUseCase(
    private val repository: GoogleMapRepository,
    private val routeCache: RouteCache
) {

    fun getPickupStatusFlow(): Flow<List<StoreLocation>> = repository.getPickupStatusFlow()

    var isOrderPickedUp: Boolean = false
    var isOrderArrived: Boolean = false

    var isMultiStopOrder: Boolean = false
    var stores: List<StoreLocation> = emptyList()
    var multiStopDestination: LatLng? = null
    var completedStops: Set<Int> = emptySet()
    var riderLocation: SimpleLocation? = null

    companion object {
        private const val MULTI_STOP_DEVIATION_THRESHOLD = 200.0
    }

    fun updateOrderPickedUp() {
        isOrderPickedUp = true
    }

    fun initialise(globalOrderId: String, groupingId: String): Flow<LocationResponse> = try {
        repository.init(globalOrderId, groupingId).map { orderData ->
            processOrderInitialization(orderData)
        }
    } catch (_: Exception) {
        flowOf(createDefaultLocationResponse())
    }

    fun getRiderPcOrderDetailData(childOrderId: String): Flow<RiderLocationResponse?> = try {
        repository.getRiderLatestLocation(childOrderId)
            .distinctUntilChanged()
            .map { location ->
                riderLocation = location
                processRiderLocationUpdate(location)
            }
    } catch (_: Exception) {
        flowOf(null)
    }

    fun updateRoute(childOrderId: String, routePoints: List<LatLng>) {
        repository.updateRoute(childOrderId, routePoints)
    }

    fun checkRouteDeviation(
        currentLocation: LatLng,
        routePoints: List<LatLng>,
        thresholdMeters: Double = MULTI_STOP_DEVIATION_THRESHOLD
    ): Pair<Boolean, Double> = try {
        RouteUtils.isRiderDeviated(currentLocation, routePoints, thresholdMeters)
    } catch (_: Exception) {
        Pair(false, 0.0)
    }

    private fun processOrderInitialization(orderData: OrderResponseData?): LocationResponse {
        return try {
            if (orderData == null) return createDefaultLocationResponse()
            updateOrderState(orderData)
            createLocationResponse(orderData)
        } catch (_: Exception) {
            createDefaultLocationResponse()
        }
    }

    private fun updateOrderState(orderData: OrderResponseData) {
        isOrderPickedUp = orderData.stores.any { it.isOrderPickedUp }
        isOrderArrived = orderData.isOrderArrived
        multiStopDestination = orderData.multiStopDestination
        isMultiStopOrder = orderData.stores.isNotEmpty()
        stores = orderData.stores
        completedStops = orderData.completedStops
        cacheMultiStopData(orderData.batchOrderId, orderData)
    }

    private fun cacheMultiStopData(childOrderId: String, orderData: OrderResponseData) {
        if (!isMultiStopOrder) return
        val multiStopData = MultiStopCacheData(
            stores = orderData.stores,
            destination = orderData.multiStopDestination ?: return,
            currentSegmentProgress = 0f,
            overallProgress = 0f,
            activeSegmentId = null,
            isAtStore = false,
            storePickupStartTime = null
        )
        routeCache.updateMultiStopData(childOrderId, multiStopData)
    }

    private fun createLocationResponse(orderData: OrderResponseData): LocationResponse = LocationResponse(
        isOrderPickedUp = orderData.stores.any { it.isOrderPickedUp },
        isOrderArrived = orderData.isOrderArrived,
        childOrderId = orderData.batchOrderId,
        stores = orderData.stores,
        multiStopDestination = orderData.multiStopDestination
    )

    private fun createDefaultLocationResponse(): LocationResponse = LocationResponse(
        isOrderPickedUp = false,
        isOrderArrived = false
    )

    private fun processRiderLocationUpdate(location: SimpleLocation?): RiderLocationResponse? {
        if (location == null) return null
        return RiderLocationResponse(
            location = location,
            isOrderPickedUp = isOrderPickedUp,
            isOrderArrived = isOrderArrived
        )
    }

    private fun createRouteRequest(origin: LatLng, destination: LatLng): RouteRequest = RouteRequest(
        origin = Waypoint(location = Location(latLng = ApiLatLng(origin.latitude, origin.longitude))),
        destination = Waypoint(location = Location(latLng = ApiLatLng(destination.latitude, destination.longitude)))
    )

    // Two-phase routing

    suspend fun getPrePickupRoute(
        childOrderId: String,
        firstStoreLocation: LatLng
    ): Result<List<LatLng>?> {
        return try {
            if (riderLocation == null || riderLocation!!.latitude == null || riderLocation!!.longitude == null) {
                return Result.failure(Exception("Rider location unavailable"))
            }
            val origin = LatLng(riderLocation!!.latitude!!, riderLocation!!.longitude!!)
            val routeRequest = createRouteRequest(origin, firstStoreLocation)
            val routeResult = repository.getRoute(routeRequest, childOrderId, false)

            routeResult.fold(
                onSuccess = { routePoints ->
                    if (!routePoints.isNullOrEmpty()) {
                        Result.success(routePoints)
                    } else {
                        Result.success(createFallbackRoute(origin, firstStoreLocation))
                    }
                },
                onFailure = { Result.success(createFallbackRoute(origin, firstStoreLocation)) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createFallbackRoute(origin: LatLng, destination: LatLng): List<LatLng> =
        createInterpolatedPoints(origin, destination, 15)

    suspend fun getMultiStopRouteSegment(
        childOrderId: String,
        isDeviated: Boolean = false
    ): Result<RouteSegment?> {
        return try {
            if (riderLocation == null || riderLocation!!.latitude == null || riderLocation!!.longitude == null) {
                return Result.failure(Exception("Rider location unavailable"))
            }
            val origin = LatLng(riderLocation!!.latitude!!, riderLocation!!.longitude!!)
            val destination = multiStopDestination ?: return Result.failure(Throwable("No multi-stop destination"))

            val routeRequest = createRouteRequest(origin, destination)
            val routeResult = repository.getRoute(routeRequest, childOrderId, isDeviated)

            routeResult.fold(
                onSuccess = { routePoints ->
                    if (!routePoints.isNullOrEmpty()) {
                        val segmentId = "complete_route_$childOrderId"
                        val segment = createCompleteRouteSegment(routePoints, segmentId)
                        routeCache.cacheRouteSegment(childOrderId, segment)
                        Result.success(segment)
                    } else {
                        Result.success(createFallbackCompleteRoute(childOrderId))
                    }
                },
                onFailure = { Result.success(createFallbackCompleteRoute(childOrderId)) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createCompleteRouteSegment(routePoints: List<LatLng>, segmentId: String): RouteSegment {
        val fromLocation = stores.first().location
        val toLocation = multiStopDestination ?: stores.last().location
        val optimizedRoutePoints = RouteUtils.validateRouteSegment(routePoints, fromLocation, toLocation, "Complete Multi-Stop Route")
        return RouteSegment(segmentId = segmentId, routePoints = optimizedRoutePoints, isActive = true, isCompleted = false)
    }

    private fun createFallbackCompleteRoute(childOrderId: String): RouteSegment {
        val segmentId = "fallback_complete_route_$childOrderId"
        val allLocations = stores.map { it.location }.toMutableList()
        multiStopDestination?.let { allLocations.add(it) }
        val routePoints = mutableListOf<LatLng>()
        for (i in 0 until allLocations.size - 1) {
            val segmentPoints = createInterpolatedPoints(allLocations[i], allLocations[i + 1], 20)
            if (i == 0) routePoints.addAll(segmentPoints) else routePoints.addAll(segmentPoints.drop(1))
        }
        return RouteSegment(segmentId = segmentId, routePoints = routePoints, isActive = true, isCompleted = false)
    }

    private fun createInterpolatedPoints(from: LatLng, to: LatLng, numPoints: Int): List<LatLng> =
        (0..numPoints).map { i ->
            val progress = i.toFloat() / numPoints
            LatLng(
                latitude = from.latitude + (to.latitude - from.latitude) * progress,
                longitude = from.longitude + (to.longitude - from.longitude) * progress
            )
        }

    fun processMultiStopLocationUpdate(childOrderId: String, riderLocation: LatLng): MultiStopLocationUpdate {
        if (!isMultiStopOrder) return MultiStopLocationUpdate.NotMultiStop

        val activeSegment = routeCache.getActiveSegment(childOrderId)
        if (activeSegment != null) {
            val overallProgress = RouteUtils.calculateSegmentProgress(riderLocation, activeSegment.routePoints)
            routeCache.updateSegmentProgress(childOrderId, activeSegment.segmentId, overallProgress)
        }

        val deviationCheck = RouteUtils.detectMultiStopDeviation(riderLocation, activeSegment, MULTI_STOP_DEVIATION_THRESHOLD)
        if (deviationCheck.first) return MultiStopLocationUpdate.Deviation(deviationCheck.second)
        return MultiStopLocationUpdate.Normal
    }
}

sealed class MultiStopLocationUpdate {
    object NotMultiStop : MultiStopLocationUpdate()
    object Normal : MultiStopLocationUpdate()
    data class Deviation(val distanceMeters: Double) : MultiStopLocationUpdate()
}
