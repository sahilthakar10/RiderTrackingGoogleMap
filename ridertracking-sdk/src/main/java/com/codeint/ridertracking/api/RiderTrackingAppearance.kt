package com.codeint.ridertracking.api

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Customize the look and feel of the tracking map.
 *
 * ```kotlin
 * RiderTrackingMap(
 *     order = myOrder,
 *     appearance = RiderTrackingAppearance(
 *         activeRouteColor = Color.Green,
 *         riderIcon = R.drawable.my_bike,
 *         loadingContent = { MyShimmerLoader() },
 *         reroutingContent = { MyReroutingDialog() },
 *         mapOverlayContent = { uiState -> EtaCard(uiState.stores) }
 *     )
 * )
 * ```
 */
data class RiderTrackingAppearance(

    // ================================
    // ROUTE
    // ================================

    /** Color of the active route segment (rider to next target). */
    val activeRouteColor: Color = Color(0xFF1A73E8),

    /** Color of the inactive/future route segments. */
    val inactiveRouteColor: Color = Color(0xFF90CAF9),

    /** Color of the visited/completed route. Transparent to hide. */
    val visitedRouteColor: Color = Color.Gray.copy(alpha = 0f),

    /** Width of route polylines in pixels. */
    val routeWidth: Float = 10f,

    /** Dash pattern for rerouting state. Null for solid. */
    val reroutingDashPattern: List<Float>? = listOf(15f, 8f),

    // ================================
    // MARKER ICONS
    // ================================

    /** Custom drawable resource for the rider icon. Null = default. */
    @DrawableRes val riderIcon: Int? = null,

    /** Size of the rider icon in pixels. */
    val riderIconSize: Int = 64,

    /** Custom drawable resource for the store marker icon. Null = default. */
    @DrawableRes val storeIcon: Int? = null,

    /** Size of the store icon in pixels. */
    val storeIconSize: Int = 48,

    /** Custom drawable resource for the destination marker icon. Null = default. */
    @DrawableRes val destinationIcon: Int? = null,

    /** Size of the destination icon (width x height). */
    val destinationIconWidth: Int = 48,
    val destinationIconHeight: Int = 60,

    // ================================
    // STORE LABEL
    // ================================

    /** Background color of the store label bubble. */
    val storeLabelBackground: Color = Color(0xCC333333),

    /** Text color of store labels. */
    val storeLabelTextColor: Color = Color.White,

    /** Background color of the pickup checkmark circle. */
    val storePickupCheckColor: Color = Color(0xFF4CAF50),

    /** Whether to show store name labels on markers. */
    val showStoreLabels: Boolean = true,

    // ================================
    // ARRIVAL CIRCLE
    // ================================

    /** Fill color of the arrival circle at destination. */
    val arrivalCircleFill: Color = Color(0x401A73E8),

    /** Stroke color of the arrival circle border. */
    val arrivalCircleStroke: Color = Color(0xFF1A73E8),

    /** Stroke width of the arrival circle border. */
    val arrivalCircleStrokeWidth: Float = 3f,

    /** Radius of the arrival circle in meters. */
    val arrivalCircleRadiusMeters: Double = 150.0,

    /** Whether to show arrival circle when order arrives. */
    val showArrivalCircle: Boolean = true,

    // ================================
    // MAP STYLE
    // ================================

    /** Custom Google Maps style JSON. Null = SDK default style. */
    val mapStyleJson: String? = null,

    /** Show compass on the map. */
    val showCompass: Boolean = true,

    // ================================
    // CUSTOM COMPOSABLES (slot-based)
    // ================================

    /**
     * Custom loading composable shown before map is ready.
     * Null = default loading spinner with message.
     *
     * ```kotlin
     * loadingContent = {
     *     ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
     * }
     * ```
     */
    val loadingContent: (@Composable () -> Unit)? = null,

    /** Loading message for the default loading indicator (used when loadingContent is null). */
    val loadingMessage: String = "Loading delivery information...",

    /**
     * Custom rerouting overlay composable.
     * Null = default semi-transparent overlay with "Re-routing..." card.
     *
     * ```kotlin
     * reroutingContent = {
     *     Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
     *         Text("Finding a better route...", color = Color.White)
     *     }
     * }
     * ```
     */
    val reroutingContent: (@Composable () -> Unit)? = null,

    /**
     * Custom overlay composable drawn on top of the map.
     * Use this for ETA cards, order info panels, action buttons, etc.
     * Receives current tracking state for reactive updates.
     *
     * ```kotlin
     * mapOverlayContent = { trackingState ->
     *     Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
     *         Text("Stores remaining: ${trackingState.pendingStoreCount}")
     *         Text("ETA: 5 min")
     *     }
     * }
     * ```
     */
    val mapOverlayContent: (@Composable (TrackingUiState) -> Unit)? = null
)

/**
 * Public tracking state exposed to consumer's overlay composables.
 * Contains only what consumers need - no internal implementation details.
 */
data class TrackingUiState(
    /** Current rider location. Null if not yet available. */
    val riderLocation: TrackingLocation? = null,

    /** Rider heading in degrees (0-360). */
    val riderHeading: Double = 0.0,

    /** Whether the rider is currently animating/moving. */
    val isRiderMoving: Boolean = false,

    /** All stores with current pickup status. */
    val stores: List<TrackingStore> = emptyList(),

    /** Only stores that haven't been picked up yet. */
    val pendingStores: List<TrackingStore> = emptyList(),

    /** Number of pending stores. */
    val pendingStoreCount: Int = 0,

    /** Final delivery destination. */
    val destination: TrackingLocation? = null,

    /** Whether the order has arrived at destination. */
    val isOrderArrived: Boolean = false,

    /** Whether the route is currently being recalculated. */
    val isRerouting: Boolean = false,

    /** Whether the map and data have loaded. */
    val isMapReady: Boolean = false,

    /** Whether the route is visible on the map. */
    val isRouteVisible: Boolean = false,

    /** Current routing phase. */
    val phase: TrackingPhase = TrackingPhase.PRE_PICKUP
)

enum class TrackingPhase {
    /** Before any store pickup - rider heading to first store. */
    PRE_PICKUP,
    /** After at least one store pickup - rider delivering. */
    POST_PICKUP
}
