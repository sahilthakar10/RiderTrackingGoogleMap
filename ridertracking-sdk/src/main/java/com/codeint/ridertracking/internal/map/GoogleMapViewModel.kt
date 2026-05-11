package com.codeint.ridertracking.internal.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeint.ridertracking.internal.map.rider.RiderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GoogleMapViewModel(
    val googleMapUseCase: GoogleMapUseCase
) : ViewModel() {

    lateinit var globalOrderId: String
    lateinit var batchOrderId: String
    lateinit var initialCameraLatLng: LatLng

    private val _uiState = MutableStateFlow(GoogleMapUiState())
    val uiState: StateFlow<GoogleMapUiState> = _uiState.asStateFlow()

    private val initialCameraZoomPosition: Float = 8f

    private val riderManager = RiderManager(
        scope = viewModelScope,
        googleMapUseCase = googleMapUseCase
    )

    fun getInitialCameraPosition() = initialCameraLatLng
    fun getInitialCameraZoom() = initialCameraZoomPosition

    private var isRouteInitialized: Boolean = false

    init {
        // Observe rider state and update UI
        viewModelScope.launch {
            riderManager.riderState.collect { riderState ->
                val currentState = _uiState.value
                val list = currentState.animateRemainingRoutePoints.toMutableList()

                updateState { state ->
                    list.remove(riderState.animatedLocation)
                    state.copy(
                        animatedRiderLocation = riderState.animatedLocation,
                        riderHeading = riderState.heading,
                        isAnimating = riderState.isAnimating,
                        visitedRoutePoints = riderState.visitedRoutePoints,
                        remainingRoutePoints = riderState.remainingRoutePoints,
                        animateRemainingRoutePoints = list,
                        currentSegmentProgress = riderState.currentSegmentProgress
                    )
                }

                val (activePath, inactivePath) = calculateActiveAndInactivePaths()
                updateState { state ->
                    state.copy(activePathSegment = activePath, inactivePathSegments = inactivePath)
                }
            }
        }

        // Reactive camera updates
        viewModelScope.launch {
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

        // Check and show delivery data
        viewModelScope.launch {
            uiState.map { it.cameraBounds }.distinctUntilChanged().collect { bounds ->
                if (bounds != null && !_uiState.value.shouldShowDeliveryData) {
                    checkAndShowDeliveryData()
                }
            }
        }

        // Fallback camera updates
        viewModelScope.launch {
            uiState.map { Triple(it.cameraBounds, it.shouldShowMap, it.animatedRiderLocation) }
                .distinctUntilChanged()
                .collect { (bounds, shouldShow, riderLocation) ->
                    if (bounds != null && shouldShow == true && riderLocation == null) {
                        delay(200)
                        if (_uiState.value.animatedRiderLocation == null) {
                            updateState { it.copy(shouldShowDeliveryData = true) }
                        }
                    }
                }
        }
    }

    fun updateState(update: (GoogleMapUiState) -> GoogleMapUiState) {
        _uiState.value = update(_uiState.value)
    }

    private fun calculateActiveAndInactivePaths(): Pair<List<LatLng>, List<LatLng>> {
        val currentState = _uiState.value
        val remainingPoints = currentState.animateRemainingRoutePoints
        val riderLocation = currentState.animatedRiderLocation
        val visibleStores = currentState.visibleStores
        val destination = currentState.multiStopDestination

        if (remainingPoints.isEmpty()) return Pair(emptyList(), emptyList())

        // Prepend rider's current position to eliminate gap between rider and route
        val connectedRemainingPoints = if (riderLocation != null && remainingPoints.isNotEmpty()) {
            val first = remainingPoints.first()
            val distToFirst = RouteUtils.calculateDistance(
                riderLocation.latitude, riderLocation.longitude,
                first.latitude, first.longitude
            )
            // Only prepend if there's a meaningful gap (> 2 meters)
            if (distToFirst > 2.0) {
                listOf(riderLocation) + remainingPoints
            } else {
                remainingPoints
            }
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
        updateState { it.copy(isLoading = true, errorState = null) }
        try {
            googleMapUseCase.initialise(globalOrderId, batchOrderId)
                .distinctUntilChanged()
                .collect { locationResponse ->
                    if (_uiState.value.stores.size != locationResponse.stores.size || _uiState.value.isOrderArrived != locationResponse.isOrderArrived) {
                        handleLocationResponse(locationResponse)
                    }
                }
        } catch (e: Exception) {
            updateState { it.copy(errorState = "Failed to initialize map: ${e.message}", isLoading = false) }
        }
    }

    private fun updateCameraBoundsReactively(animatedRiderLocation: LatLng, remainingRoutePoints: List<LatLng>) {
        val currentState = _uiState.value
        val destination = currentState.multiStopDestination ?: return

        when {
            currentState.isCompletionAnimation -> {
                val bounds = RouteUtils.calculateCompletionAnimationBounds(animatedRiderLocation, destination)
                updateState { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
            }
            currentState.isOrderCompleted -> {
                val bounds = RouteUtils.calculateCompletedOrderBounds(destination)
                updateState { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
            }
            else -> {
                val bounds = RouteUtils.calculateTightBoundsWithRoute(
                    animatedRiderLocation = animatedRiderLocation,
                    remainingRoutePoints = remainingRoutePoints,
                    pendingStores = currentState.visibleStores,
                    destination = destination,
                    isRiderActive = true
                )
                updateState { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
            }
        }
    }

    private fun checkAndShowDeliveryData() {
        val currentState = _uiState.value
        if (currentState.shouldShowMap && !currentState.shouldShowDeliveryData) {
            viewModelScope.launch {
                delay(500)
                triggerZoomToDeliveryArea()
            }
        } else if (currentState.cameraBounds != null && !currentState.shouldShowDeliveryData) {
            viewModelScope.launch {
                delay(300)
                triggerZoomToDeliveryArea()
            }
        }
    }

    private fun triggerZoomToDeliveryArea() {
        val currentState = _uiState.value
        if (currentState.cameraBounds != null && !currentState.shouldShowDeliveryData) {
            updateState { it.copy(shouldShowDeliveryData = true) }
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
                viewModelScope.launch {
                    delay(100)
                    handleCompletedOrderInitialization()
                }
            } else {
                handleDeliveryStatusChange(isOrderPickedUp)
                setupInitialRouteInfo(isOrderPickedUp)

                viewModelScope.launch {
                    delay(100)
                    try {
                        loadInitialMultiStopRoute()
                        startRiderLocationTracking()
                        startPickupEventMonitoring()
                    } catch (_: Exception) {}
                }
            }

            updateState { it.copy(isOrderArrived = isOrderArrived, isOrderCompleted = isOrderArrived) }
            updateState { it.copy(isLoading = false) }
        } catch (e: Exception) {
            updateState { it.copy(errorState = "Failed to process location data: ${e.message}", isLoading = false) }
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
                        latitude = fromPoint.latitude + (toPoint.latitude - fromPoint.latitude) * progress,
                        longitude = fromPoint.longitude + (toPoint.longitude - fromPoint.longitude) * progress
                    ))
                }
            }
            densifiedPoints.add(toPoint)
        }
        return densifiedPoints
    }

    private suspend fun loadInitialMultiStopRoute() {
        try {
            when (_uiState.value.routingPhase) {
                RoutingPhase.PRE_PICKUP -> loadPrePickupRoute()
                RoutingPhase.POST_PICKUP -> loadPostPickupRoute()
            }
        } catch (e: Exception) {
            updateState { it.copy(errorState = "Route initialization failed: ${e.message}") }
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
                    val tempSegment = RouteSegment(
                        segmentId = "pre_pickup_$batchOrderId",
                        routePoints = densifiedRoute,
                        isActive = true
                    )
                    updateState { state ->
                        state.copy(
                            activeSegment = tempSegment,
                            remainingRoutePoints = densifiedRoute,
                            animateRemainingRoutePoints = densifiedRoute,
                            visitedRoutePoints = emptyList(),
                            isRouteVisible = false
                        )
                    }
                    riderManager.initializeForRoute(activeSegment = tempSegment)
                    calculateInitialCameraBounds()
                    isInitialRouteFetched = true

                    viewModelScope.launch {
                        delay(500)
                        if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea()
                    }
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

                    updateState { state ->
                        state.copy(
                            activeSegment = activeSegment,
                            remainingRoutePoints = densifiedRoutePoints,
                            animateRemainingRoutePoints = densifiedRoutePoints,
                            visitedRoutePoints = emptyList(),
                            isRouteVisible = true
                        )
                    }
                    riderManager.initializeForRoute(activeSegment = activeSegment)
                    calculateInitialCameraBounds()
                    isInitialRouteFetched = true

                    viewModelScope.launch {
                        delay(500)
                        if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea()
                    }
                } else {
                    updateState { it.copy(errorState = "No route available") }
                }
            },
            onFailure = {}
        )
    }

    private fun initializeRiderAtStore(storeLocation: LatLng) {
        val tempSegment = RouteSegment(
            segmentId = "direct_position_$batchOrderId",
            routePoints = listOf(storeLocation),
            isActive = true
        )
        updateState { state ->
            state.copy(
                remainingRoutePoints = listOf(storeLocation),
                animateRemainingRoutePoints = listOf(storeLocation),
                visitedRoutePoints = emptyList(),
                isRouteVisible = false
            )
        }
        riderManager.initializeForRoute(activeSegment = tempSegment)
        calculateInitialCameraBounds()
        isInitialRouteFetched = true

        viewModelScope.launch {
            delay(500)
            if (!_uiState.value.shouldShowDeliveryData) triggerZoomToDeliveryArea()
        }
    }

    private fun calculateInitialCameraBounds() {
        val currentState = _uiState.value
        val destination = currentState.multiStopDestination ?: return

        val bounds = RouteUtils.calculateTightBoundsWithRoute(
            animatedRiderLocation = currentState.animatedRiderLocation,
            remainingRoutePoints = currentState.remainingRoutePoints,
            pendingStores = currentState.visibleStores,
            destination = destination,
            isRiderActive = currentState.animatedRiderLocation != null
        )
        updateState { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
    }

    private fun handleDeliveryStatusChange(isOrderPickedUp: Boolean) {
        val statusChanged = _uiState.value.isOutForDelivery != isOrderPickedUp
        if (statusChanged) {
            updateState { it.copy(isOutForDelivery = isOrderPickedUp) }
            lastOutForDeliveryState = isOrderPickedUp
            if (!isOrderPickedUp) {
                clearRouteVisualization()
                isInitialRouteFetched = false
                updateState { it.copy(isRerouting = false) }
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
            updateState { state ->
                state.copy(
                    stores = allStores, multiStopDestination = multiStopDestination,
                    visibleStores = visibleStores, isOutForDelivery = isOrderPickedUp, shouldShowMap = true
                )
            }
            return
        }

        val routingPhase = determineRoutingPhase(allStores)
        val cameraBounds = calculateInitialCameraBounds(allStores, multiStopDestination)

        updateState { state ->
            state.copy(
                stores = allStores, multiStopDestination = multiStopDestination,
                visibleStores = visibleStores, isOutForDelivery = isOrderPickedUp,
                cameraBounds = cameraBounds, routingPhase = routingPhase,
                isRouteVisible = routingPhase == RoutingPhase.POST_PICKUP,
                shouldShowMap = cameraBounds != null
            )
        }

        if (cameraBounds != null) {
            viewModelScope.launch {
                delay(300)
                val currentState = _uiState.value
                if (currentState.shouldShowMap && !currentState.shouldShowDeliveryData) {
                    updateState { it.copy(shouldShowDeliveryData = true) }
                }
            }
        }
    }

    private fun determineRoutingPhase(stores: List<StoreLocation>): RoutingPhase {
        val hasAnyPickup = stores.any { it.isOrderPickedUp }
        return if (hasAnyPickup) RoutingPhase.POST_PICKUP else RoutingPhase.PRE_PICKUP
    }

    private fun calculateInitialCameraBounds(stores: List<StoreLocation>, destination: LatLng?): CameraBounds? {
        val allPoints = mutableListOf<LatLng>()
        stores.forEach { store -> allPoints.add(store.location) }
        destination?.let { allPoints.add(it) }
        if (allPoints.isEmpty()) return null

        var minLat = allPoints.first().latitude
        var maxLat = allPoints.first().latitude
        var minLng = allPoints.first().longitude
        var maxLng = allPoints.first().longitude

        allPoints.forEach { point ->
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLng = minOf(minLng, point.longitude)
            maxLng = maxOf(maxLng, point.longitude)
        }

        val latPadding = 0.002
        val lngPadding = 0.002
        return CameraBounds(
            southwest = LatLng(minLat - latPadding, minLng - lngPadding),
            northeast = LatLng(maxLat + latPadding, maxLng + lngPadding)
        )
    }

    private fun setupInitialRouteInfo(isOrderPickedUp: Boolean) {
        if (!isOrderPickedUp) {
            clearRouteVisualization()
        }
    }

    private fun startRiderLocationTracking() {
        riderTrackingJob?.cancel()
        riderTrackingJob = viewModelScope.launch {
            try {
                googleMapUseCase.getRiderPcOrderDetailData(batchOrderId)
                    .distinctUntilChanged()
                    .collect { response ->
                        if (isActive) handleRiderLocationResponse(response)
                    }
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleRiderLocationResponse(response: RiderLocationResponse?) {
        if (response == null) return
        if (_uiState.value.isOrderArrived) {
            handleOrderArrivedScenario(response)
        } else {
            handleActiveDeliveryScenario(response)
        }
    }

    private fun handleOrderArrivedScenario(response: RiderLocationResponse) {
        clearRouteVisualizationForCompletion()
        val latitude = response.location.latitude
        val longitude = response.location.longitude
        if (latitude != null && longitude != null) {
            riderManager.animateToDestination(LatLng(latitude, longitude))
        }
    }

    private suspend fun handleActiveDeliveryScenario(response: RiderLocationResponse) {
        processRiderLocationUpdate(response)
        if (!isRouteInitialized) {
            loadInitialMultiStopRoute()
            isRouteInitialized = true
        }
    }

    private fun startPickupEventMonitoring() {
        pickupStatusJob?.cancel()
        pickupStatusJob = viewModelScope.launch {
            try {
                googleMapUseCase.getPickupStatusFlow().collect { updatedStores ->
                    if (isActive) {
                        val visibleStores = updatedStores.filter { !it.isOrderPickedUp }
                        val newRoutingPhase = determineRoutingPhase(updatedStores)
                        val currentPhase = _uiState.value.routingPhase

                        if (currentPhase != newRoutingPhase) {
                            handlePhaseTransition(currentPhase, newRoutingPhase)
                        }

                        updateState { it.copy(
                            stores = updatedStores, visibleStores = visibleStores,
                            routingPhase = newRoutingPhase,
                            isRouteVisible = newRoutingPhase == RoutingPhase.POST_PICKUP
                        )}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handlePhaseTransition(fromPhase: RoutingPhase, toPhase: RoutingPhase) {
        when {
            fromPhase == RoutingPhase.PRE_PICKUP && toPhase == RoutingPhase.POST_PICKUP -> {
                viewModelScope.launch {
                    isInitialRouteFetched = false
                    googleMapUseCase.updateOrderPickedUp()
                    riderManager.clearAll()
                    loadPostPickupRoute()
                }
            }
            fromPhase == RoutingPhase.POST_PICKUP && toPhase == RoutingPhase.PRE_PICKUP -> {
                viewModelScope.launch {
                    isInitialRouteFetched = false
                    loadPrePickupRoute()
                }
            }
        }
    }

    private fun processRiderLocationUpdate(response: RiderLocationResponse) {
        try {
            val latitude = response.location.latitude
            val longitude = response.location.longitude
            if (latitude == null || longitude == null) return

            val newLocation = LatLng(latitude, longitude)
            val isOutForDelivery = response.isOrderPickedUp
            val isOrderArrived = response.isOrderArrived
            val currentState = _uiState.value

            if (!currentState.isOrderArrived && isOrderArrived) {
                updateState { it.copy(isOrderArrived = true, isOrderCompleted = true) }
                handleOrderArrivalTransition()
                return
            }
            if (currentState.isOrderCompleted) return

            processMultiStopRiderLocation(newLocation)
            handleDeliveryStatusAndRouteFetch(isOutForDelivery)

            riderManager.processRiderLocationUpdate(
                response = response,
                activeSegment = currentState.activeSegment,
                remainingRoutePoints = currentState.animateRemainingRoutePoints,
                childOrderId = batchOrderId,
                visibleStores = currentState.visibleStores,
                destination = currentState.multiStopDestination
            )
        } catch (_: Exception) {}
    }

    private fun processMultiStopRiderLocation(riderLocation: LatLng) {
        val locationUpdate = googleMapUseCase.processMultiStopLocationUpdate(batchOrderId, riderLocation)
        when (locationUpdate) {
            is MultiStopLocationUpdate.Deviation -> handleMultiStopDeviation(riderLocation)
            else -> {}
        }
    }

    private fun handleMultiStopDeviation(riderLocation: LatLng) {
        updateState { it.copy(isRerouting = true) }
        viewModelScope.launch { rerouteCurrentSegment(riderLocation) }
    }

    private suspend fun rerouteCurrentSegment(riderLocation: LatLng) {
        try {
            val segmentResult = googleMapUseCase.getMultiStopRouteSegment(batchOrderId, true)
            segmentResult.fold(
                onSuccess = { segment ->
                    if (segment != null && segment.routePoints.isNotEmpty()) {
                        val densifiedRoutePoints = densifyRoutePoints(segment.routePoints)
                        updateState { state ->
                            state.copy(
                                activeSegment = segment.copy(routePoints = densifiedRoutePoints),
                                remainingRoutePoints = densifiedRoutePoints,
                                animateRemainingRoutePoints = densifiedRoutePoints,
                                visitedRoutePoints = emptyList(), isRerouting = false
                            )
                        }
                        riderManager.handleReroute(segment)

                        val currentState = _uiState.value
                        val destination = currentState.multiStopDestination ?: return
                        val bounds = RouteUtils.calculateTightBoundsWithRoute(
                            animatedRiderLocation = riderLocation,
                            remainingRoutePoints = currentState.remainingRoutePoints,
                            pendingStores = currentState.visibleStores,
                            destination = destination, isRiderActive = true
                        )
                        updateState { it.copy(cameraBounds = CameraBounds(southwest = bounds.first, northeast = bounds.second)) }
                    } else {
                        updateState { it.copy(isRerouting = false) }
                    }
                },
                onFailure = { updateState { it.copy(isRerouting = false) } }
            )
        } catch (_: Exception) {
            updateState { it.copy(isRerouting = false) }
        }
    }

    private fun handleDeliveryStatusAndRouteFetch(isOutForDelivery: Boolean) {
        if (_uiState.value.isOutForDelivery != isOutForDelivery) {
            handleDeliveryStatusChange(isOutForDelivery)
        }
    }

    private fun clearRouteVisualization() {
        updateState { it.copy(remainingRoutePoints = emptyList(), visitedRoutePoints = emptyList()) }
        riderManager.clearAll()
    }

    private fun handleOrderArrivalTransition() {
        clearRouteVisualizationForCompletion()
        updateState { it.copy(isCompletionAnimation = true, isRouteVisible = false) }
    }

    private fun handleCompletedOrderInitialization() {
        val destination = _uiState.value.multiStopDestination ?: return
        clearRouteVisualizationForCompletion()
        updateState { it.copy(isCompletionAnimation = false, isOrderCompleted = true, isRouteVisible = false) }
        viewModelScope.launch {
            delay(100)
            triggerZoomToDeliveryArea()
        }
    }

    private fun clearRouteVisualizationForCompletion() {
        updateState { it.copy(
            remainingRoutePoints = emptyList(), animateRemainingRoutePoints = emptyList(),
            visitedRoutePoints = emptyList(), activeSegment = null, isRouteVisible = false
        )}
    }

    fun toggleFollowRider() {
        riderManager.toggleFollowRider()
    }

    override fun onCleared() {
        super.onCleared()
        riderManager.clearAll()
        riderTrackingJob?.cancel()
        pickupStatusJob?.cancel()
    }
}
