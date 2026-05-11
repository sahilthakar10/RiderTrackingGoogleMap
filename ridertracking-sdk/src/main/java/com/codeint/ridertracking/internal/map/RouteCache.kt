package com.codeint.ridertracking.internal.map

class RouteCache {
    private val cacheMap: MutableMap<String, RiderLastData> = java.util.concurrent.ConcurrentHashMap()
    private val pruningStateMap: MutableMap<String, Boolean> = java.util.concurrent.ConcurrentHashMap()
    private val multiStopDataMap: MutableMap<String, MultiStopCacheData> = java.util.concurrent.ConcurrentHashMap()
    private val segmentCacheMap: MutableMap<String, MutableMap<String, RouteSegment>> = java.util.concurrent.ConcurrentHashMap()

    fun getRoute(batchOrderId: String): List<LatLng>? = cacheMap[batchOrderId]?.route

    fun getRiderData(batchOrderId: String): RiderLastData? = cacheMap[batchOrderId]

    fun updateRoute(batchOrderId: String, route: List<LatLng>) {
        val existingData = cacheMap[batchOrderId]
        cacheMap[batchOrderId] = existingData?.copy(route = route)
            ?: RiderLastData(lastLocation = null, route = route)
    }

    fun updateLastLocation(batchOrderId: String, location: SimpleLocation) {
        val existingData = cacheMap[batchOrderId]
        cacheMap[batchOrderId] = existingData?.copy(lastLocation = location)
            ?: RiderLastData(lastLocation = location, route = null)
    }

    fun shouldPruneRoute(batchOrderId: String): Boolean =
        cacheMap.containsKey(batchOrderId) && pruningStateMap[batchOrderId] != true

    fun markPruningComplete(batchOrderId: String) {
        pruningStateMap[batchOrderId] = true
    }

    fun resetPruningState(batchOrderId: String) {
        pruningStateMap[batchOrderId] = false
    }

    fun clearAllCache() {
        cacheMap.clear()
        pruningStateMap.clear()
        multiStopDataMap.clear()
        segmentCacheMap.clear()
    }

    fun updateMultiStopData(batchOrderId: String, data: MultiStopCacheData) {
        multiStopDataMap[batchOrderId] = data
    }

    fun cacheRouteSegment(batchOrderId: String, segment: RouteSegment) {
        val orderSegments = segmentCacheMap.getOrPut(batchOrderId) { mutableMapOf() }
        orderSegments[segment.segmentId] = segment
        if (orderSegments.size > GoogleMapConstants.SEGMENT_CACHE_SIZE) {
            val oldestSegment = orderSegments.values.minByOrNull { it.segmentId }
            oldestSegment?.let { orderSegments.remove(it.segmentId) }
        }
    }

    fun getActiveSegment(batchOrderId: String): RouteSegment? =
        segmentCacheMap[batchOrderId]?.values?.find { it.isActive }

    fun updateSegmentProgress(batchOrderId: String, segmentId: String, progress: Float) {
        val orderSegments = segmentCacheMap[batchOrderId] ?: return
        val segment = orderSegments[segmentId] ?: return
        orderSegments[segmentId] = segment.copy(isActive = true)
        val multiStopData = multiStopDataMap[batchOrderId] ?: return
        multiStopDataMap[batchOrderId] = multiStopData.copy(
            currentSegmentProgress = progress,
            activeSegmentId = segmentId
        )
    }
}

data class RiderLastData(
    val lastLocation: SimpleLocation?,
    val route: List<LatLng>?
)

data class MultiStopCacheData(
    val stores: List<StoreLocation>,
    val destination: LatLng,
    val currentSegmentProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val activeSegmentId: String? = null,
    val isAtStore: Boolean = false,
    val storePickupStartTime: Long? = null
)
