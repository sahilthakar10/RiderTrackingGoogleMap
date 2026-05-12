package com.codeint.ridertracking.api

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * Customize the look and feel of the tracking map.
 *
 * Usage:
 * ```kotlin
 * RiderTrackingMap(
 *     order = myOrder,
 *     appearance = RiderTrackingAppearance(
 *         activeRouteColor = Color(0xFF4CAF50),
 *         riderIcon = R.drawable.my_rider_icon,
 *         storeLabelBackground = Color(0xFF333333),
 *         mapStyleJson = myCustomMapStyleJson
 *     )
 * )
 * ```
 */
data class RiderTrackingAppearance(

    // ================================
    // ROUTE COLORS
    // ================================

    /** Color of the active route segment (rider to next target). Default: blue */
    val activeRouteColor: Color = Color(0xFF1A73E8),

    /** Color of the inactive/future route segments. Default: light blue */
    val inactiveRouteColor: Color = Color(0xFF90CAF9),

    /** Width of route polylines in pixels. Default: 10 */
    val routeWidth: Float = 10f,

    // ================================
    // MARKER ICONS
    // ================================

    /** Custom drawable resource for the rider icon. Pass null to use default. */
    @DrawableRes val riderIcon: Int? = null,

    /** Size of the rider icon in pixels. Default: 64 */
    val riderIconSize: Int = 64,

    /** Custom drawable resource for the store marker icon. Pass null to use default. */
    @DrawableRes val storeIcon: Int? = null,

    /** Custom drawable resource for the destination marker icon. Pass null to use default. */
    @DrawableRes val destinationIcon: Int? = null,

    // ================================
    // STORE LABEL
    // ================================

    /** Background color of the store label bubble. Default: dark overlay */
    val storeLabelBackground: Color = Color(0xCC333333),

    /** Text color of store labels. Default: white */
    val storeLabelTextColor: Color = Color.White,

    /** Background color of the pickup checkmark circle. Default: green */
    val storePickupCheckColor: Color = Color(0xFF4CAF50),

    /** Whether to show store name labels on markers. Default: true */
    val showStoreLabels: Boolean = true,

    // ================================
    // ARRIVAL CIRCLE
    // ================================

    /** Fill color of the arrival circle at destination. Default: semi-transparent blue */
    val arrivalCircleFill: Color = Color(0x401A73E8),

    /** Stroke color of the arrival circle border. Default: blue */
    val arrivalCircleStroke: Color = Color(0xFF1A73E8),

    /** Radius of the arrival circle in meters. Default: 150 */
    val arrivalCircleRadiusMeters: Double = 150.0,

    // ================================
    // MAP STYLE
    // ================================

    /** Custom Google Maps style JSON. Pass null to use the SDK's default clean style. */
    val mapStyleJson: String? = null,

    /** Show/hide the loading indicator. Default: true */
    val showLoadingIndicator: Boolean = true,

    /** Loading message text. Default: "Loading delivery information..." */
    val loadingMessage: String = "Loading delivery information..."
)
