package com.codeint.ridertracking.internal.map

data class GoogleMapUiState(
    // Route and location states
    val animatedRiderLocation: LatLng? = null,

    // Rider tracking states
    val riderHeading: Double = 0.0,
    val isAnimating: Boolean = false,
    val isOutForDelivery: Boolean = false,
    val isOrderArrived: Boolean = false,

    // Route visualization states
    val remainingRoutePoints: List<LatLng> = emptyList(),
    val animateRemainingRoutePoints: List<LatLng> = emptyList(),
    val visitedRoutePoints: List<LatLng> = emptyList(),

    // Re-routing states
    val isRerouting: Boolean = false,

    // Routing phase management
    val routingPhase: RoutingPhase = RoutingPhase.PRE_PICKUP,
    val isRouteVisible: Boolean = false,

    // Camera management states
    val cameraBounds: CameraBounds? = null,

    // Initial loading and zoom states
    val shouldShowMap: Boolean = false,
    val shouldShowDeliveryData: Boolean = false,

    // Error and loading states
    val errorState: String? = null,
    val isLoading: Boolean = false,

    // Multi-stop delivery states
    val stores: List<StoreLocation> = emptyList(),
    val multiStopDestination: LatLng? = null,

    // Segment-based routing
    val activeSegment: RouteSegment? = null,
    val currentSegmentProgress: Float = 0f,

    // Multi-stop progress tracking
    val nextStop: StoreLocation? = null,
    val visibleStores: List<StoreLocation> = emptyList(),

    // Active path segment highlighting
    val activePathSegment: List<LatLng> = emptyList(),
    val inactivePathSegments: List<LatLng> = emptyList(),

    // Order completion states
    val isCompletionAnimation: Boolean = false,
    val isOrderCompleted: Boolean = false
)

enum class RoutingPhase {
    PRE_PICKUP,
    POST_PICKUP
}

enum class CameraMode {
    SHOW_PENDING_LOCATIONS,
    FOLLOW_RIDER_AND_PENDING_LOCATIONS,
    FINAL_DESTINATION
}

data class CameraBounds(
    val southwest: LatLng,
    val northeast: LatLng
)
