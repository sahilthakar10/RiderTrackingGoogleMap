package com.codeint.ridertracking.internal.map

import android.util.Log
import com.codeint.ridertracking.internal.map.network.RouteApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class GoogleMapRepositorySimulation : GoogleMapRepository {

    private var previousPoint: SimpleLocation = SimpleLocation(null, null)
    private var trackingId: String? = null

    private var storeConfigs: List<StoreConfig> = emptyList()
    private var finalDestination: LatLng = LatLng(0.0, 0.0)
    private var numberOfStores: Int = 0

    private var storeList: MutableList<StoreLocation> = mutableListOf()
    private var activeStoreLocations: List<SimpleLocation> = emptyList()

    private var completeRoutePoints: List<LatLng> = emptyList()
    private var currentRouteIndex = 0
    private var isOrderPickedUp = false
    private var startTime = 0L

    private val _pickupStatusFlow = MutableSharedFlow<List<StoreLocation>>(replay = 1)

    private var currentPickupStoreIndex: Int? = null
    private var pickupStartTime: Long? = null
    private var pickupInProgress = false
    private var riderStationaryLocation: LatLng? = null

    private val pickupDurationSeconds = 10
    private val arrivalRadius = 50.0

    private var storeRouteIndices: MutableList<Int> = mutableListOf()

    // Route API for fetching real road-following routes
    private var routeApiService: RouteApiService? = null
    private var routesApiKey: String = ""

    fun setRouteApiService(service: RouteApiService, apiKey: String) {
        this.routeApiService = service
        this.routesApiKey = apiKey
    }

    fun configure(stores: List<StoreConfig>, destination: LatLng) {
        this.storeConfigs = stores
        this.finalDestination = destination
        this.numberOfStores = stores.size

        this.activeStoreLocations = stores.map {
            SimpleLocation(latitude = it.latitude, longitude = it.longitude)
        }

        this.storeList = stores.map { store ->
            StoreLocation(
                storeName = store.name,
                location = LatLng(store.latitude, store.longitude),
                isOrderPickedUp = false
            )
        }.toMutableList()

        startTime = System.currentTimeMillis()
        _pickupStatusFlow.tryEmit(storeList.toList())
    }

    /**
     * Fetch real road route from Google Routes API.
     * Falls back to straight-line interpolation if API fails.
     */
    private suspend fun fetchRealRoute(): List<LatLng> {
        val api = routeApiService
        if (api == null || routesApiKey.isEmpty()) {
            return buildStraightLineRoute()
        }

        try {
            val allWaypoints = mutableListOf<LatLng>()
            activeStoreLocations.forEach { loc ->
                if (loc.latitude != null && loc.longitude != null) {
                    allWaypoints.add(LatLng(loc.latitude, loc.longitude))
                }
            }
            allWaypoints.add(finalDestination)

            if (allWaypoints.size < 2) return buildStraightLineRoute()

            val allRoutePoints = mutableListOf<LatLng>()

            // Fetch route segment by segment (store1->store2->...->destination)
            for (i in 0 until allWaypoints.size - 1) {
                val origin = allWaypoints[i]
                val dest = allWaypoints[i + 1]

                val request = RouteRequest(
                    origin = Waypoint(Location(ApiLatLng(origin.latitude, origin.longitude))),
                    destination = Waypoint(Location(ApiLatLng(dest.latitude, dest.longitude)))
                )

                val response = withContext(Dispatchers.IO) {
                    api.computeRoutes(apiKey = routesApiKey, request = request)
                }

                val legs = response.routes?.firstOrNull()?.legs
                if (legs != null) {
                    for (leg in legs) {
                        val encoded = leg.polyline?.encodedPolyline
                        if (encoded != null) {
                            val points = PolylineDecoder.decode(encoded)
                            if (allRoutePoints.isNotEmpty() && points.isNotEmpty()) {
                                allRoutePoints.addAll(points.drop(1)) // avoid duplicate junction point
                            } else {
                                allRoutePoints.addAll(points)
                            }
                        }
                    }
                }
            }

            if (allRoutePoints.size >= 2) {
                Log.d("Simulation", "Got real route with ${allRoutePoints.size} points")
                return allRoutePoints
            }
        } catch (e: Exception) {
            Log.w("Simulation", "Routes API failed, using straight line: ${e.message}")
        }

        return buildStraightLineRoute()
    }

    private fun buildStraightLineRoute(): List<LatLng> {
        val allLocations = mutableListOf<LatLng>()
        activeStoreLocations.forEach { loc ->
            if (loc.latitude != null && loc.longitude != null) {
                allLocations.add(LatLng(loc.latitude, loc.longitude))
            }
        }
        allLocations.add(finalDestination)

        val routePoints = mutableListOf<LatLng>()
        for (i in 0 until allLocations.size - 1) {
            val from = allLocations[i]
            val to = allLocations[i + 1]
            val distance = calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude)
            val numPoints = (distance / 5.0).toInt().coerceIn(10, 100)
            val segmentPoints = createSegmentPoints(from, to, numPoints)
            if (i == 0) routePoints.addAll(segmentPoints) else routePoints.addAll(segmentPoints.drop(1))
        }
        return routePoints
    }

    private fun findStorePositionsOnRoute() {
        if (completeRoutePoints.isEmpty()) return
        storeRouteIndices.clear()
        activeStoreLocations.forEach { storeLocation ->
            storeRouteIndices.add(findClosestRoutePointIndex(storeLocation))
        }
        ensureCorrectStoreOrder()
    }

    private fun ensureCorrectStoreOrder() {
        if (storeRouteIndices.isEmpty() || completeRoutePoints.isEmpty()) return
        val minDistance = maxOf(1, completeRoutePoints.size / (numberOfStores + 2))
        val sorted = storeRouteIndices.sorted().toMutableList()
        for (i in sorted.indices) {
            val adjusted = when {
                i == 0 -> maxOf(sorted[i], minDistance)
                else -> maxOf(sorted[i], sorted[i - 1] + minDistance)
            }
            sorted[i] = adjusted.coerceAtMost(completeRoutePoints.size - minDistance - 1)
        }
        storeRouteIndices = sorted
    }

    private fun findClosestRoutePointIndex(storeLocation: SimpleLocation): Int {
        if (completeRoutePoints.isEmpty() || storeLocation.latitude == null || storeLocation.longitude == null) return 0
        var closestIndex = 0
        var minDistance = Double.MAX_VALUE
        completeRoutePoints.forEachIndexed { index, point ->
            val distance = calculateDistance(storeLocation.latitude, storeLocation.longitude, point.latitude, point.longitude)
            if (distance < minDistance) { minDistance = distance; closestIndex = index }
        }
        return closestIndex
    }

    private fun createSegmentPoints(from: LatLng, to: LatLng, numPoints: Int): List<LatLng> =
        (0..numPoints).map { i ->
            val progress = i.toFloat() / numPoints
            LatLng(from.latitude + (to.latitude - from.latitude) * progress, from.longitude + (to.longitude - from.longitude) * progress)
        }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1).toRadians()
        val dLng = (lng2 - lng1).toRadians()
        val a = sin(dLat / 2).pow(2) + cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun Double.toRadians(): Double = this * (PI / 180)

    override fun init(globalOrderId: String, batchOrderId: String): Flow<OrderResponseData?> = flow {
        if (storeList.isEmpty()) { emit(null); return@flow }

        // Fetch real route before emitting order data
        completeRoutePoints = fetchRealRoute()
        findStorePositionsOnRoute()

        val orderData = OrderResponseData(
            batchOrderId = batchOrderId,
            isOrderArrived = false,
            stores = storeList.toList(),
            multiStopDestination = finalDestination
        )
        emit(orderData)

        delay(5.seconds)

        if (storeList.isNotEmpty()) {
            storeList[0] = storeList[0].copy(isOrderPickedUp = true)
            isOrderPickedUp = true
            trackingId = "OrderTracking_$batchOrderId"
            _pickupStatusFlow.emit(storeList.toList())
        }
    }

    override suspend fun getRoute(request: RouteRequest, batchOrderId: String, isDeviated: Boolean): Result<List<LatLng>?> = try {
        if (completeRoutePoints.isNotEmpty()) {
            Result.success(completeRoutePoints)
        } else {
            completeRoutePoints = fetchRealRoute()
            findStorePositionsOnRoute()
            Result.success(completeRoutePoints)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getRiderLatestLocation(batchOrderId: String): Flow<SimpleLocation> = flow {
        while (true) {
            val mockLocation = if (trackingId != null && isOrderPickedUp) {
                generateMultiStopRiderMovement(System.currentTimeMillis())
            } else if (activeStoreLocations.isNotEmpty()) {
                val first = activeStoreLocations.first()
                SimpleLocation(first.latitude, first.longitude)
            } else {
                SimpleLocation(null, null)
            }
            emit(mockLocation)
            previousPoint = mockLocation
            delay(1500)
        }
    }.flowOn(Dispatchers.IO)

    override fun updateRoute(batchOrderId: String, route: List<LatLng>) {}

    private fun generateMultiStopRiderMovement(currentTime: Long): SimpleLocation {
        val timeElapsed = currentTime - (startTime + 5000)

        if (!pickupInProgress) {
            val arrivalCheck = checkStoreArrivalByDistance()
            if (arrivalCheck.isAtStore) startRepositoryControlledPickup(arrivalCheck.storeIndex, currentTime)
        }
        if (pickupInProgress) handleRepositoryControlledPickup(currentTime)

        return if (pickupInProgress && riderStationaryLocation != null) {
            generateStationaryPickupLocation()
        } else {
            followExactRoutePath(timeElapsed)
        }
    }

    private fun followExactRoutePath(timeElapsed: Long): SimpleLocation {
        if (completeRoutePoints.isEmpty()) return SimpleLocation(null, null)

        val totalJourneyMinutes = GoogleMapConstants.MULTI_STOP_JOURNEY_DURATION_MINUTES + maxOf(0, numberOfStores - 1) * 2
        val totalJourneyTimeMs = totalJourneyMinutes * 60 * 1000L

        val progress = (timeElapsed.toFloat() / totalJourneyTimeMs).coerceIn(0f, 1f)
        val exactIndex = progress * (completeRoutePoints.size - 1)
        val baseIndex = exactIndex.toInt().coerceIn(0, completeRoutePoints.size - 1)
        currentRouteIndex = baseIndex

        val riderPosition = if (baseIndex < completeRoutePoints.size - 1 && progress < 1f) {
            val curr = completeRoutePoints[baseIndex]
            val next = completeRoutePoints[baseIndex + 1]
            val segProg = exactIndex - baseIndex
            LatLng(curr.latitude + (next.latitude - curr.latitude) * segProg, curr.longitude + (next.longitude - curr.longitude) * segProg)
        } else {
            completeRoutePoints[baseIndex]
        }

        // Minimal GPS noise for realistic feel
        val noise = 3.0
        val latN = (Random.nextDouble() - 0.5) * (noise / 111320.0)
        val lngN = (Random.nextDouble() - 0.5) * (noise / (111320.0 * cos(riderPosition.latitude * PI / 180.0)))

        return SimpleLocation(riderPosition.latitude + latN, riderPosition.longitude + lngN)
    }

    private fun checkStoreArrivalByDistance(): StoreArrivalCheck {
        val currentLocation = getCurrentRiderLocation() ?: return StoreArrivalCheck(false, -1)
        for (store in storeList.filter { !it.isOrderPickedUp }) {
            val dist = calculateDistance(currentLocation.latitude, currentLocation.longitude, store.location.latitude, store.location.longitude)
            if (dist <= arrivalRadius) return StoreArrivalCheck(true, storeList.indexOf(store))
        }
        return StoreArrivalCheck(false, -1)
    }

    private fun startRepositoryControlledPickup(storeIndex: Int, currentTime: Long) {
        if (!pickupInProgress && storeIndex < storeList.size && storeIndex < storeConfigs.size) {
            val store = storeConfigs[storeIndex]
            pickupInProgress = true
            currentPickupStoreIndex = storeIndex
            pickupStartTime = currentTime
            riderStationaryLocation = LatLng(store.latitude, store.longitude)
            storeList[storeIndex] = storeList[storeIndex].copy(isOrderPickedUp = true)
            _pickupStatusFlow.tryEmit(storeList.toList())
        }
    }

    private fun handleRepositoryControlledPickup(currentTime: Long) {
        val start = pickupStartTime ?: return
        val storeIndex = currentPickupStoreIndex ?: return
        if (((currentTime - start) / 1000).toInt() >= pickupDurationSeconds) {
            completeRepositoryControlledPickup(storeIndex)
        }
    }

    private fun completeRepositoryControlledPickup(storeIndex: Int) {
        pickupInProgress = false
        currentPickupStoreIndex = null
        pickupStartTime = null
        riderStationaryLocation = null
        if (storeIndex < storeList.size) {
            storeList[storeIndex] = storeList[storeIndex].copy(isOrderPickedUp = true)
            _pickupStatusFlow.tryEmit(storeList.toList())
        }
    }

    private fun generateStationaryPickupLocation(): SimpleLocation {
        val base = riderStationaryLocation ?: return SimpleLocation(null, null)
        val n = 2.0
        return SimpleLocation(
            base.latitude + (Random.nextDouble() - 0.5) * (n / 111320.0),
            base.longitude + (Random.nextDouble() - 0.5) * (n / (111320.0 * cos(base.latitude * PI / 180.0)))
        )
    }

    private fun getCurrentRiderLocation(): LatLng? {
        if (completeRoutePoints.isEmpty()) return null
        return completeRoutePoints.getOrElse(currentRouteIndex) { completeRoutePoints.first() }
    }

    override fun getPickupStatusFlow(): Flow<List<StoreLocation>> = _pickupStatusFlow

    private data class StoreArrivalCheck(val isAtStore: Boolean, val storeIndex: Int)
}
