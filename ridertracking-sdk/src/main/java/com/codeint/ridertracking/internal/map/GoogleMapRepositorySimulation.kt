package com.codeint.ridertracking.internal.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    // STORE CONFIGURATION
    private val storeConfigurations = listOf(
        StoreConfig(
            id = "store1",
            name = "Pizza Palace",
            latitude = 12.9170491,
            longitude = 77.6729254
        )
    )

    private val numberOfStores = 1
    private val finalDestinationLocation = SimpleLocation(
        latitude = 12.925499916077,
        longitude = 77.66960144043
    )

    private val activeStores: List<StoreConfig> = storeConfigurations.take(numberOfStores)
    private val activeStoreLocations: List<SimpleLocation> = activeStores.map {
        SimpleLocation(latitude = it.latitude, longitude = it.longitude)
    }

    private var storeList = activeStores.map { store ->
        StoreLocation(
            storeName = store.name,
            location = LatLng(store.latitude, store.longitude),
            isOrderPickedUp = false
        )
    }.toMutableList()

    // Route and state variables
    private var completeRoutePoints: List<LatLng> = emptyList()
    private var currentRouteIndex = 0
    private var isOrderPickedUp = false
    private var startTime = 0L
    private var currentStopIndex = 0

    private val _pickupStatusFlow = MutableSharedFlow<List<StoreLocation>>(replay = 1)

    // Pickup state tracking
    private var currentPickupStoreIndex: Int? = null
    private var pickupStartTime: Long? = null
    private var pickupInProgress = false
    private var riderStationaryLocation: LatLng? = null

    private val pickupDurationSeconds = 10
    private val arrivalRadius = 50.0

    private var storeRouteIndices: MutableList<Int> = mutableListOf()
    private var destinationRouteIndex = 0

    private val sourceLocation = activeStoreLocations.first()

    init {
        initializeCompleteRoute()
        startTime = System.currentTimeMillis()
        _pickupStatusFlow.tryEmit(storeList)
    }

    private fun initializeCompleteRoute() {
        try {
            val segment1 = PolylineDecoder.decode("}zymA_oayM_CtF{AzD]hAiBfIcAfCeCbGqAtEW|Ae@Kq@UIGOSo@kBaD}I{@eBoBmFiBGeEEsBG]I_@Ke@A")
            val decodedPoints = segment1

            if (decodedPoints.isNotEmpty()) {
                completeRoutePoints = decodedPoints
                findStorePositionsOnRoute()
            } else {
                createFallbackCompleteRoute()
            }
        } catch (_: Exception) {
            createFallbackCompleteRoute()
        }
    }

    private fun findStorePositionsOnRoute() {
        if (completeRoutePoints.isEmpty()) return

        storeRouteIndices.clear()

        activeStoreLocations.forEachIndexed { _, storeLocation ->
            val closestIndex = findClosestRoutePointIndex(storeLocation)
            storeRouteIndices.add(closestIndex)
        }

        destinationRouteIndex = completeRoutePoints.size - 1
        ensureCorrectStoreOrder()
    }

    private fun ensureCorrectStoreOrder() {
        val sortedIndices = storeRouteIndices.sorted()
        val minDistance = completeRoutePoints.size / (numberOfStores + 2)

        for (i in sortedIndices.indices) {
            val adjustedIndex = when {
                i == 0 -> maxOf(sortedIndices[i], minDistance)
                else -> maxOf(sortedIndices[i], sortedIndices[i - 1] + minDistance)
            }
            storeRouteIndices[i] = adjustedIndex.coerceAtMost(completeRoutePoints.size - minDistance - 1)
        }
    }

    private fun findClosestRoutePointIndex(storeLocation: SimpleLocation): Int {
        if (completeRoutePoints.isEmpty() || storeLocation.latitude == null || storeLocation.longitude == null) return 0

        var closestIndex = 0
        var minDistance = Double.MAX_VALUE

        completeRoutePoints.forEachIndexed { index, point ->
            val distance = calculateDistance(storeLocation.latitude, storeLocation.longitude, point.latitude, point.longitude)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        return closestIndex
    }

    private fun createFallbackCompleteRoute() {
        val allLocations = mutableListOf<LatLng>()
        activeStoreLocations.forEach { storeLocation ->
            allLocations.add(LatLng(storeLocation.latitude!!, storeLocation.longitude!!))
        }
        allLocations.add(LatLng(finalDestinationLocation.latitude!!, finalDestinationLocation.longitude!!))

        val routePoints = mutableListOf<LatLng>()
        for (i in 0 until allLocations.size - 1) {
            val from = allLocations[i]
            val to = allLocations[i + 1]
            val segmentPoints = createSegmentPoints(from, to, 20)
            if (i == 0) routePoints.addAll(segmentPoints) else routePoints.addAll(segmentPoints.drop(1))
        }
        completeRoutePoints = routePoints

        storeRouteIndices.clear()
        val segmentSize = routePoints.size / (numberOfStores + 1)
        for (i in 0 until numberOfStores) {
            storeRouteIndices.add(i * segmentSize)
        }
        destinationRouteIndex = completeRoutePoints.size - 1
    }

    private fun createSegmentPoints(from: LatLng, to: LatLng, numPoints: Int): List<LatLng> =
        (0..numPoints).map { i ->
            val progress = i.toFloat() / numPoints
            LatLng(
                latitude = from.latitude + (to.latitude - from.latitude) * progress,
                longitude = from.longitude + (to.longitude - from.longitude) * progress
            )
        }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1).toRadians()
        val dLng = (lng2 - lng1).toRadians()
        val a = sin(dLat / 2).pow(2) + cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun Double.toRadians(): Double = this * (PI / 180)

    override fun init(globalOrderId: String, batchOrderId: String): Flow<OrderResponseData?> = flow {
        val orderData = OrderResponseData(
            batchOrderId = "MultiStopOrder123",
            isOrderArrived = false,
            stores = storeList,
            multiStopDestination = LatLng(finalDestinationLocation.latitude!!, finalDestinationLocation.longitude!!)
        )
        emit(orderData)

        delay(5.seconds) // Wait 5 seconds before pickup starts

        storeList[0] = storeList[0].copy(isOrderPickedUp = true)
        isOrderPickedUp = true
        trackingId = "OrderTracking123"

        _pickupStatusFlow.emit(storeList)
    }

    override suspend fun getRoute(
        request: RouteRequest,
        batchOrderId: String,
        isDeviated: Boolean
    ): Result<List<LatLng>?> = try {
        if (completeRoutePoints.isNotEmpty()) {
            Result.success(completeRoutePoints)
        } else {
            createFallbackCompleteRoute()
            Result.success(completeRoutePoints)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun getRiderLatestLocation(batchOrderId: String): Flow<SimpleLocation> = flow {
        while (true) {
            val mockLocation = if (trackingId != null && isOrderPickedUp) {
                val currentTime = System.currentTimeMillis()
                generateMultiStopRiderMovement(currentTime)
            } else {
                SimpleLocation(sourceLocation.latitude, sourceLocation.longitude)
            }

            emit(mockLocation)
            previousPoint = mockLocation
            delay(3.seconds)
        }
    }.flowOn(Dispatchers.IO)

    override fun updateRoute(batchOrderId: String, route: List<LatLng>) {
        // no-op for simulation
    }

    private fun generateMultiStopRiderMovement(currentTime: Long): SimpleLocation {
        val timeElapsed = currentTime - (startTime + 5000)

        if (!pickupInProgress) {
            val arrivalCheck = checkStoreArrivalByDistance()
            if (arrivalCheck.isAtStore) {
                startRepositoryControlledPickup(arrivalCheck.storeIndex, currentTime)
            }
        }

        if (pickupInProgress) {
            handleRepositoryControlledPickup(currentTime)
        }

        return if (pickupInProgress && riderStationaryLocation != null) {
            generateStationaryPickupLocation()
        } else {
            followExactRoutePath(timeElapsed)
        }
    }

    private fun followExactRoutePath(timeElapsed: Long): SimpleLocation {
        if (completeRoutePoints.isEmpty()) {
            return SimpleLocation(sourceLocation.latitude, sourceLocation.longitude)
        }

        val baseJourneyMinutes = GoogleMapConstants.MULTI_STOP_JOURNEY_DURATION_MINUTES
        val additionalTimePerStore = 2
        val totalJourneyMinutes = baseJourneyMinutes + maxOf(0, numberOfStores - 3) * additionalTimePerStore
        val totalJourneyTimeMs = totalJourneyMinutes * 60 * 1000L

        val progress = (timeElapsed.toFloat() / totalJourneyTimeMs).coerceIn(0f, 1f)
        val exactIndex = progress * (completeRoutePoints.size - 1)
        val baseIndex = exactIndex.toInt().coerceIn(0, completeRoutePoints.size - 1)
        currentRouteIndex = baseIndex

        val riderPosition = if (baseIndex < completeRoutePoints.size - 1 && progress < 1f) {
            val currentPoint = completeRoutePoints[baseIndex]
            val nextPoint = completeRoutePoints[baseIndex + 1]
            val segmentProgress = exactIndex - baseIndex
            LatLng(
                latitude = currentPoint.latitude + (nextPoint.latitude - currentPoint.latitude) * segmentProgress,
                longitude = currentPoint.longitude + (nextPoint.longitude - currentPoint.longitude) * segmentProgress
            )
        } else {
            completeRoutePoints[baseIndex]
        }

        val gpsAccuracyMeters = 10.0
        val latNoiseDegrees = (Random.nextDouble() - 0.5) * (gpsAccuracyMeters / 111320.0)
        val lngNoiseDegrees = (Random.nextDouble() - 0.5) * (gpsAccuracyMeters / (111320.0 * cos(riderPosition.latitude * PI / 180.0)))

        return SimpleLocation(
            latitude = riderPosition.latitude + latNoiseDegrees,
            longitude = riderPosition.longitude + lngNoiseDegrees
        )
    }

    private fun checkStoreArrivalByDistance(): StoreArrivalCheck {
        val currentLocation = getCurrentRiderLocation()
        val pendingStores = storeList.filter { !it.isOrderPickedUp }

        for (storeLocation in pendingStores) {
            val distance = calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                storeLocation.location.latitude, storeLocation.location.longitude
            )
            if (distance <= arrivalRadius) {
                val originalIndex = storeList.indexOf(storeLocation)
                return StoreArrivalCheck(true, originalIndex)
            }
        }
        return StoreArrivalCheck(false, -1)
    }

    private fun startRepositoryControlledPickup(storeIndex: Int, currentTime: Long) {
        if (!pickupInProgress && storeIndex < storeList.size) {
            val store = activeStores[storeIndex]
            pickupInProgress = true
            currentPickupStoreIndex = storeIndex
            pickupStartTime = currentTime
            riderStationaryLocation = LatLng(store.latitude, store.longitude)

            storeList[storeIndex] = storeList[storeIndex].copy(isOrderPickedUp = true)
            _pickupStatusFlow.tryEmit(storeList)
        }
    }

    private fun handleRepositoryControlledPickup(currentTime: Long) {
        val startTime = pickupStartTime ?: return
        val storeIndex = currentPickupStoreIndex ?: return
        val elapsedSeconds = ((currentTime - startTime) / 1000).toInt()
        if (elapsedSeconds >= pickupDurationSeconds) {
            completeRepositoryControlledPickup(storeIndex)
        }
    }

    private fun completeRepositoryControlledPickup(storeIndex: Int) {
        val pendingStores = storeList.filter { !it.isOrderPickedUp }
        currentStopIndex = (numberOfStores - pendingStores.size + 1).coerceAtMost(numberOfStores)
        pickupInProgress = false
        currentPickupStoreIndex = null
        pickupStartTime = null
        riderStationaryLocation = null

        if (storeIndex < storeList.size) {
            storeList[storeIndex] = storeList[storeIndex].copy(isOrderPickedUp = true)
            _pickupStatusFlow.tryEmit(storeList)
        }
    }

    private fun generateStationaryPickupLocation(): SimpleLocation {
        val baseLocation = riderStationaryLocation ?: return SimpleLocation(null, null)
        val gpsAccuracyMeters = 5.0
        val latNoiseDegrees = (Random.nextDouble() - 0.5) * (gpsAccuracyMeters / 111320.0)
        val lngNoiseDegrees = (Random.nextDouble() - 0.5) * (gpsAccuracyMeters / (111320.0 * cos(baseLocation.latitude * PI / 180.0)))
        return SimpleLocation(
            latitude = baseLocation.latitude + latNoiseDegrees,
            longitude = baseLocation.longitude + lngNoiseDegrees
        )
    }

    private fun getCurrentRiderLocation(): LatLng {
        if (completeRoutePoints.isEmpty()) {
            return LatLng(sourceLocation.latitude!!, sourceLocation.longitude!!)
        }
        return completeRoutePoints.getOrElse(currentRouteIndex) { completeRoutePoints.first() }
    }

    override fun getPickupStatusFlow(): Flow<List<StoreLocation>> = _pickupStatusFlow

    private data class StoreArrivalCheck(val isAtStore: Boolean, val storeIndex: Int)
}
