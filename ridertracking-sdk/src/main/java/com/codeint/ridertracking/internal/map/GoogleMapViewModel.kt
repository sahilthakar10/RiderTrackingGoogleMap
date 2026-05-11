package com.codeint.ridertracking.internal.map

import com.codeint.ridertracking.internal.map.rider.RiderManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class GoogleMapViewModel(
    private val googleMapUseCase: GoogleMapUseCase
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var globalOrderId: String = ""
        private set
    var batchOrderId: String = ""
        private set
    var initialCameraLatLng: LatLng = LatLng(0.0, 0.0)
        private set

    private val _uiState = MutableStateFlow(GoogleMapUiState())
    val uiState: StateFlow<GoogleMapUiState> = _uiState.asStateFlow()

    private val initialCameraZoomPosition: Float = 8f

    private val riderManager = RiderManager(
        scope = scope,
        googleMapUseCase = googleMapUseCase
    )

    fun getInitialCameraPosition() = initialCameraLatLng
    fun getInitialCameraZoom() = initialCameraZoomPosition

    private var isRouteInitialized: Boolean = false

    init {
        scope.launch {
            riderManager.riderState.collect { riderState ->
                _uiState.update { state ->
                    val list = state.animateRemainingRoutePoints.toMutableList()
                    list.remove(riderState.animatedLocation)
                    val (activePath, inactivePath) = calculateActiveAndInactivePaths(
                        state.copy(
                            animatedRiderLocation = riderState.animatedLocation,
                            animateRemainingRoutePoints = list
                        )
                    )
                    state.copy(
                        animatedRiderLocation = riderState.animatedLocation,
                        riderHeading = riderState.heading,
                        isAnimating = riderState.isAnimating,
                        visitedRoutePoints = riderState.visitedRoutePoints,
                        remainingRoutePoints = riderState.remainingRoutePoints,
                        animateRemainingRoutePoints = list,
                        currentSegmentProgress = riderState.currentSegmentProgress,
                        activePathSegment = activePath,
                        inactivePathSegments = inactivePath
                    )
                }
            }
        }

        scope.launch {
            riderManager.riderState
                .map { state -> Triple(state.animatedLocation, state.remainingRoutePoints, state.isActive) }
                .distinctUntilChanged()
                .collect { (animatedLocation, remainingPoints, isActive) ->
                    val currentState = _uiState.value
                    if (animatedLocation != null && currentState.shouldShowMap) {
                        when {
                            currentState.isCompletionAnimation || currentState.isOrderCompleted -> {
                                updateCameraBoundsReactively(animatedLocation, remainingPoints)
                            }
                            isActive -> {
                                delay(100)
                                updateCameraBoundsReactively(animatedLocation, remainingPoints)
                            }
                        }
                    }
                }
        }

        scope.launch {
            uiState.map { it.cameraBounds }.distinctUntilChanged().collect { bounds ->
                if (bounds != null && !_uiState.value.shouldShowDeliveryData) {
                    checkAndShowDeliveryData()
                }
            }
        }

        scope.launch {
            uiState.map { Triple(it.cameraBounds, it.shouldShowMap, it.animatedRiderLocation) }
                .distinctUntilChanged()
                .collect { (bounds, shouldShow, riderLocation) ->
                    if (bounds != null && shouldShow == true && riderLocation == null) {
                        delay(200)
                        if (_uiState.value.animatedRiderLocation == null) {
                            _uiState.update { it.copy(shouldShowDeliveryData = true) }
                        }
                    }
                }
        }
    }

    fun updateState(update: (GoogleMapUiState) -> GoogleMapUiState) {
        _uiState.update(update)
    }

    private fun calculateActiveAndInactivePaths(
        currentState: GoogleMapUiState = _uiState.value
    ): Pair<List<LatLng>, List<LatLng>> {
        val remainingPoints = currentState.animateRemainingRoutePoints
        val riderLocation = currentState.animatedRiderLocation
        val visibleStores = currentState.visibleStores
        val destination = currentState.multiStopDestination

        if (remainingPoints.isEmpty()) return Pair(emptyList(), emptyList())

        val connectedRemainingPoints = if (riderLocation != null && remainingPoints.isNotEmpty()) {
            val first = remainingPoints.first()
            val distToFirst = RouteUtils.calculateDistance(
                riderLocation.latitude, riderLocation.longitude,
                first.latitude, first.longitude
            )
            if (distToFirst > 2.0) listOf(riderLocation) + remainingPoints else remainingPoints
        } else {
            remainingPoints
        }

        if (visibleStores.isEmpty() && destination != null) {
            return Pair(connectedRemainingPoints, emptyList())
        }

        val nextStore = visibleStores.firstOrNull()
        if (nextStore != null) {
            val splitIndex = connectedRemainingPoints.indexOfFirst { point ->
                RouteUtils.calculateDistance(
                    point.latitude, point.longitude,
                    nextStore.location.latitude, nextStore.location.longitude
                ) <= GoogleMapConstants.STORE_ARRIVAL_THRESHOLD_METERS
            }
            return if (splitIndex > 0) {
                Pair(connectedRemainingPoints.take(splitIndex + 1), connectedRemainingPoints.drop(splitIndex))
            } else {
                Pair(connectedRemainingPoints, emptyList())
            }
        }
        return Pair(connectedRemainingPoints, emptyList())
    }

    private var riderTrackingJob: Job? = null
    private var pickupStatusJob: Job? = null
    private var isInitialRouteFetched = false
    private var lastOutForDeliveryState = false

    fun set(globalOrderId: String, batchOrderId: String, destination: SimpleLocation?) {
        this.globalOrderId = globalOrderId
        this.batchOrderId = batchOrderId
        this.initialCameraLatLng = LatLng(
            latitude = destination?.latitude ?: 12.97421537614,
            longitude = destination?.longitude ?: 77.680312844393
        )
    }

    suspend fun loadRoute() {
        _uiState.update { it.copy(isLoading = true, errorState = null) }
        try {
            googleMapUseCase.initialise(globalOrderId, batchOrderId)
                .distinctUntilChanged()
                .collect { locationResponse ->
                    if (_uiState.value.stores.size != locationResponse.stores.size || _uiState.value.isOrderArrived != locationResponse.isOrderArrived) {
                        handleLocationResponse(locationResponse)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorState = "Failed to initialize map: ${e.message}", isLoading = false) }
        }
    }

    /**
     * Cancel all coroutines and release resources. Must be called when the map is removed.
     */
    fun destroy() {
        riderManager.clearAll()
        riderTrackingJob?.cancel()
        pickupStatusJob?.cancel()
        scope.cancel()
    }

    private fun updateCameraBoundsReactively(animatedRiderLocation: LatLng, remainingRoutePoints: List<LatLng>) {
        val currentState = _uiState.value
        val destination = currentState.multiStopDestination ?: return

        val newBounds = when {
            currentState.isCompletionAnimation -> {
                val b = RouteUtils.calculateCompletionAnimationBounds(animatedRiderLocation, destination)
                CameraBounds(southwest = b.first, northeast = b.second)
            }
            currentState.isOrderCompleted -> {
                val b = RouteUtils.calculateCompletedOrderBounds(destination)
                CameraBounds(southwest = b.first, northeast = b.second)
            }
            else -> {
                val b = RouteUtils.calculateTightBoundsWithRoute(
                    animatedRiderLocation = animatedRiderLocation,
                    remainingRoutePoints = remainingRoutePoints,
                    pendingStores = currentState.visibleStores,
                    destination = destination, isRiderActive = true
                )
                CameraBounds(southwest = b.first, northeast = b.second)
            }
        }
        _uiState.update { it.copy(cameraBounds = newBounds) }
    }

    private fun checkAndShowDeliveryData() {
        val currentState = _uiState.value
        if (currentState.shouldShowMap && !currentState.shouldShowDeliveryData) {
            scope.launch {
                delay(500)
                triggerZoomToDeliveryArea()
            }
        } else if (currentState.cameraBounds != null && !currentState.shouldShowDeliveryData) {
            scope.launch {
                delay(300)
                triggerZoomToDeliveryArea()
            }
        }
    }

    private fun triggerZoomToDeliveryArea() {
        if (_uiState.value.cameraBounds != null && !_uiState.value.shouldShowDeliveryData) {
            _uiState.update { it.copy(shouldShowDeliveryData = true) }
        }
    }

    private fun handleLocationResponse(locationResponse: LocationResponse) {
        try {
            batchOrderId = locationResponse.childOrderId ?: ""
            val isOrderPickedUp = locationResponse.stores.any { it.isOrderPickedUp }
            val isOrderArrived = locationResponse.isOrderArrived

            handleUnifiedOrderInitialization(locationResponse, isOrderPickedUp)

            val previousOrderArrived = _uiState.value.isOrderArrived
            if (!previousOrderArrived && isOrderArrived) {
                startRiderLocationTracking()
                handleOrderArrivalTransition()
            } else if (isOrderArrived) {
                startRiderLocationTracking()
                scope.launch {
                    delay(100)
                    handleCompletedOrderInitialization()
                }
            } else {
                handleDeliveryStatusChange(isOrderPickedUp)
                setupInitialRouteInfo(isOrderPickedUp)
                scope.launch {
                    delay(100)
                    try {
                        loadInitialMultiStopRoute()
                        startRiderLocationTracking()
                        startPickupEventMonitoring()
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                }
            }

            _uiState.update { it.copy(isOrderArrived = isOrderArrived, isOrderCompleted = isOrderArrived, isLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(errorState = "Failed to process location data: ${e.message}", isLoading = false) }
        }
    }

    private fun densifyRoutePoints(originalPoints: List<LatLng>): List<LatLng> {
        if (originalPoints.size < 2) return originalPoints
        val targetSpacing = 10.0
        val minGapToDensify = 20.0
        val densifiedPoints = mutableListOf<LatLng>()
        densifiedPoints.add(originalPoints.first())
        for (i in 0 until originalPoints.size - 1) {
            val fromPoint = originalPoints[i]
            val toPoint = originalPoints[i + 1]
            val distance = RouteUtils.calculateDistance(fromPoint.latitude, fromPoint.longitude, toPoint.latitude, toPoint.longitude)
            if (distance > minGapToDensify) {
                val numIntermediatePoints = (distance / targetSpacing).toInt().coerceAtMost(15)
                for (j in 1..numIntermediatePoints) {
                    val progress = j.toFloat() / (numIntermediatePoints + 1)
                    densifiedPoints.add(LatLng(
                        fromPoint.latitude + (toPoint.latitude - fromPoint.latitude) * progress,
                        fromPoint.longitude + (toPoint.longitude - fromPoint.longitude) * progress
                    ))
                }
            }
            densifiedPoints.add(toPoint)
        }
        return densifiedPoints
    }

    private suspend fun loadInitialMultiStopRoute() {
        when (_uiState.value.routingPhase) {
            RoutingPhase.PRE_PICKUP -> loadPrePickupRoute()
            RoutingPhase.POST_PICKUP -> loadPostPickupRoute()
        }
    }

    private suspend fun loadPrePickupRoute() {
        val currentState = _uiState.value
        val firstStore = currentState.visibleStores.firstOrNull() ?: return
        val riderLocation = currentState.animatedRiderLocation
        if (riderLocation != null) {
            val distanceToStore = RouteUtils.calculateDistance(
                riderLocation.latitude, riderLocation.longitude,
                firstStore.location.latitude, firstStore.location.longitude
            )
            if (distanceToStore <= GoogleMapConstants.STORE_ARRIVAL_THRESHOLD_METERS) {
                initializeRiderAtStore(firstStore.location)
                return
            }
        }
        val segmentResult = googleMapUseCase.getPrePickupRoute(batchOrderId, firstStore.location)
        segmentResult.fold(
            onSuccess = { hiddenRoute ->
                if (!hiddenRoute.isNullOrEmpty()) {
                    val densifiedRoute = densifyRoutePoints(hiddenRoute)
                    val tempSegment = RouteSegment("pre_pickup_$batchOrderId", densifiedRoute, isActive = true)
                    _uiState.update { it.copy(
                        activeSegment = tempSegment, remainingRoutePoints = densifiedRoute,
                        animateRemainingRoutePoints = densifiedRoute, visitedRoutePoints = emptyList(), isRouteVisible = false
                    )}
                    riderManager.initializeForRoute(activeSegment = tempSegment)
                    calculateInitialCameraBounds()
                    isInitialRouteFetched = true
                    scope.launch { delay(500); if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea() }
                } else {
                    initializeRiderAtStore(firstStore.location)
                }
            },
            onFailure = { initializeRiderAtStore(firstStore.location) }
        )
    }

    private suspend fun loadPostPickupRoute() {
        val segmentResult = googleMapUseCase.getMultiStopRouteSegment(batchOrderId)
        segmentResult.fold(
            onSuccess = { segment ->
                if (segment != null && segment.routePoints.isNotEmpty()) {
                    val densifiedRoutePoints = densifyRoutePoints(segment.routePoints)
                    val activeSegment = segment.copy(routePoints = densifiedRoutePoints)
                    _uiState.update { it.copy(
                        activeSegment = activeSegment, remainingRoutePoints = densifiedRoutePoints,
                        animateRemainingRoutePoints = densifiedRoutePoints, visitedRoutePoints = emptyList(), isRouteVisible = true
                    )}
                    riderManager.initializeForRoute(activeSegment = activeSegment)
                    calculateInitialCameraBounds()
                    isInitialRouteFetched = true
                    scope.launch { delay(500); if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea() }
                }
            },
            onFailure = {}
        )
    }

    private fun initializeRiderAtStore(storeLocation: LatLng) {
        val tempSegment = RouteSegment("direct_position_$batchOrderId", listOf(storeLocation), isActive = true)
        _uiState.update { it.copy(
            remainingRoutePoints = listOf(storeLocation), animateRemainingRoutePoints = listOf(storeLocation),
            visitedRoutePoints = emptyList(), isRouteVisible = false
        )}
        riderManager.initializeForRoute(activeSegment = tempSegment)
        calculateInitialCameraBounds()
        isInitialRouteFetched = true
        scope.launch { delay(500); if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea() }
    }

    private fun calculateInitialCameraBounds() {
        val currentState = _uiState.value
        val destination = currentState.multiStopDestination ?: return
        val bounds = RouteUtils.calculateTightBoundsWithRoute(
            animatedRiderLocation = currentState.animatedRiderLocation,
            remainingRoutePoints = currentState.remainingRoutePoints,
            pendingStores = currentState.visibleStores,
            destination = destination, isRiderActive = currentState.animatedRiderLocation != null
        )
        _uiState.update { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
    }

    private fun handleDeliveryStatusChange(isOrderPickedUp: Boolean) {
        if (_uiState.value.isOutForDelivery != isOrderPickedUp) {
            _uiState.update { it.copy(isOutForDelivery = isOrderPickedUp) }
            lastOutForDeliveryState = isOrderPickedUp
            if (!isOrderPickedUp) {
                clearRouteVisualization()
                isInitialRouteFetched = false
            }
        }
    }

    private fun handleUnifiedOrderInitialization(locationResponse: LocationResponse, isOrderPickedUp: Boolean) {
        val allStores = locationResponse.stores
        val visibleStores = allStores.filter { !it.isOrderPickedUp }
        val multiStopDestination = locationResponse.multiStopDestination
        val isOrderArrived = locationResponse.isOrderArrived
        if (multiStopDestination == null || (multiStopDestination.latitude == 0.0 && multiStopDestination.longitude == 0.0)) return

        if (isOrderArrived) {
            _uiState.update { it.copy(
                stores = allStores, multiStopDestination = multiStopDestination,
                visibleStores = visibleStores, isOutForDelivery = isOrderPickedUp, shouldShowMap = true
            )}
            return
        }

        val routingPhase = determineRoutingPhase(allStores)
        val cameraBounds = calculateInitialCameraBounds(allStores, multiStopDestination)
        _uiState.update { it.copy(
            stores = allStores, multiStopDestination = multiStopDestination,
            visibleStores = visibleStores, isOutForDelivery = isOrderPickedUp,
            cameraBounds = cameraBounds, routingPhase = routingPhase,
            isRouteVisible = routingPhase == RoutingPhase.POST_PICKUP,
            shouldShowMap = cameraBounds != null
        )}

        if (cameraBounds != null) {
            scope.launch {
                delay(300)
                if (_uiState.value.shouldShowMap && !_uiState.value.shouldShowDeliveryData) {
                    _uiState.update { it.copy(shouldShowDeliveryData = true) }
                }
            }
        }
    }

    private fun determineRoutingPhase(stores: List<StoreLocation>): RoutingPhase =
        if (stores.any { it.isOrderPickedUp }) RoutingPhase.POST_PICKUP else RoutingPhase.PRE_PICKUP

    private fun calculateInitialCameraBounds(stores: List<StoreLocation>, destination: LatLng?): CameraBounds? {
        val allPoints = mutableListOf<LatLng>()
        stores.forEach { allPoints.add(it.location) }
        destination?.let { allPoints.add(it) }
        if (allPoints.isEmpty()) return null
        val padding = 0.002
        return CameraBounds(
            southwest = LatLng(allPoints.minOf { it.latitude } - padding, allPoints.minOf { it.longitude } - padding),
            northeast = LatLng(allPoints.maxOf { it.latitude } + padding, allPoints.maxOf { it.longitude } + padding)
        )
    }

    private fun setupInitialRouteInfo(isOrderPickedUp: Boolean) {
        if (!isOrderPickedUp) clearRouteVisualization()
    }

    private fun startRiderLocationTracking() {
        riderTrackingJob?.cancel()
        riderTrackingJob = scope.launch {
            try {
                googleMapUseCase.getRiderPcOrderDetailData(batchOrderId)
                    .distinctUntilChanged()
                    .collect { response -> if (isActive) handleRiderLocationResponse(response) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    private suspend fun handleRiderLocationResponse(response: RiderLocationResponse?) {
        if (response == null) return
        if (_uiState.value.isOrderArrived) handleOrderArrivedScenario(response)
        else handleActiveDeliveryScenario(response)
    }

    private fun handleOrderArrivedScenario(response: RiderLocationResponse) {
        clearRouteVisualizationForCompletion()
        val lat = response.location.latitude
        val lng = response.location.longitude
        if (lat != null && lng != null) riderManager.animateToDestination(LatLng(lat, lng))
    }

    private suspend fun handleActiveDeliveryScenario(response: RiderLocationResponse) {
        processRiderLocationUpdate(response)
        if (!isRouteInitialized) { loadInitialMultiStopRoute(); isRouteInitialized = true }
    }

    private fun startPickupEventMonitoring() {
        pickupStatusJob?.cancel()
        pickupStatusJob = scope.launch {
            try {
                googleMapUseCase.getPickupStatusFlow().collect { updatedStores ->
                    if (isActive) {
                        val visibleStores = updatedStores.filter { !it.isOrderPickedUp }
                        val newRoutingPhase = determineRoutingPhase(updatedStores)
                        val currentPhase = _uiState.value.routingPhase
                        if (currentPhase != newRoutingPhase) handlePhaseTransition(currentPhase, newRoutingPhase)
                        _uiState.update { it.copy(
                            stores = updatedStores, visibleStores = visibleStores,
                            routingPhase = newRoutingPhase, isRouteVisible = newRoutingPhase == RoutingPhase.POST_PICKUP
                        )}
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
    }

    private fun handlePhaseTransition(fromPhase: RoutingPhase, toPhase: RoutingPhase) {
        if (fromPhase == RoutingPhase.PRE_PICKUP && toPhase == RoutingPhase.POST_PICKUP) {
            scope.launch {
                isInitialRouteFetched = false
                googleMapUseCase.updateOrderPickedUp()
                riderManager.clearAll()
                loadPostPickupRoute()
            }
        }
    }

    private fun processRiderLocationUpdate(response: RiderLocationResponse) {
        try {
            val lat = response.location.latitude ?: return
            val lng = response.location.longitude ?: return
            val newLocation = LatLng(lat, lng)
            val currentState = _uiState.value

            if (!currentState.isOrderArrived && response.isOrderArrived) {
                _uiState.update { it.copy(isOrderArrived = true, isOrderCompleted = true) }
                handleOrderArrivalTransition()
                return
            }
            if (currentState.isOrderCompleted) return

            val locationUpdate = googleMapUseCase.processMultiStopLocationUpdate(batchOrderId, newLocation)
            if (locationUpdate is MultiStopLocationUpdate.Deviation) {
                _uiState.update { it.copy(isRerouting = true) }
                scope.launch { rerouteCurrentSegment(newLocation) }
            }

            if (_uiState.value.isOutForDelivery != response.isOrderPickedUp) handleDeliveryStatusChange(response.isOrderPickedUp)

            riderManager.processRiderLocationUpdate(
                response = response, activeSegment = currentState.activeSegment,
                remainingRoutePoints = currentState.animateRemainingRoutePoints,
                childOrderId = batchOrderId, visibleStores = currentState.visibleStores,
                destination = currentState.multiStopDestination
            )
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    private suspend fun rerouteCurrentSegment(riderLocation: LatLng) {
        try {
            val segmentResult = googleMapUseCase.getMultiStopRouteSegment(batchOrderId, true)
            segmentResult.fold(
                onSuccess = { segment ->
                    if (segment != null && segment.routePoints.isNotEmpty()) {
                        val densified = densifyRoutePoints(segment.routePoints)
                        _uiState.update { it.copy(
                            activeSegment = segment.copy(routePoints = densified),
                            remainingRoutePoints = densified, animateRemainingRoutePoints = densified,
                            visitedRoutePoints = emptyList(), isRerouting = false
                        )}
                        riderManager.handleReroute(segment)
                        val dest = _uiState.value.multiStopDestination ?: return
                        val bounds = RouteUtils.calculateTightBoundsWithRoute(
                            riderLocation, _uiState.value.remainingRoutePoints,
                            _uiState.value.visibleStores, dest, true
                        )
                        _uiState.update { it.copy(cameraBounds = CameraBounds(bounds.first, bounds.second)) }
                    } else _uiState.update { it.copy(isRerouting = false) }
                },
                onFailure = { _uiState.update { it.copy(isRerouting = false) } }
            )
        } catch (e: CancellationException) { throw e } catch (_: Exception) { _uiState.update { it.copy(isRerouting = false) } }
    }

    private fun clearRouteVisualization() {
        _uiState.update { it.copy(remainingRoutePoints = emptyList(), visitedRoutePoints = emptyList()) }
        riderManager.clearAll()
    }

    private fun handleOrderArrivalTransition() {
        clearRouteVisualizationForCompletion()
        _uiState.update { it.copy(isCompletionAnimation = true, isRouteVisible = false) }
    }

    private fun handleCompletedOrderInitialization() {
        val destination = _uiState.value.multiStopDestination ?: return
        clearRouteVisualizationForCompletion()
        _uiState.update { it.copy(isCompletionAnimation = false, isOrderCompleted = true, isRouteVisible = false) }
        scope.launch { delay(100); triggerZoomToDeliveryArea() }
    }

    private fun clearRouteVisualizationForCompletion() {
        _uiState.update { it.copy(
            remainingRoutePoints = emptyList(), animateRemainingRoutePoints = emptyList(),
            visitedRoutePoints = emptyList(), activeSegment = null, isRouteVisible = false
        )}
    }

    fun toggleFollowRider() { riderManager.toggleFollowRider() }
}
