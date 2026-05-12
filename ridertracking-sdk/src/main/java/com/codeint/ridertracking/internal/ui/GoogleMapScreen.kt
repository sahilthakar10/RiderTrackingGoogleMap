package com.codeint.ridertracking.internal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.codeint.ridertracking.R
import com.codeint.ridertracking.api.RiderTrackingAppearance
import com.codeint.ridertracking.api.TrackingLocation
import com.codeint.ridertracking.api.TrackingPhase
import com.codeint.ridertracking.api.TrackingStore
import com.codeint.ridertracking.api.TrackingUiState
import com.codeint.ridertracking.internal.map.GoogleMapConstants
import com.codeint.ridertracking.internal.map.GoogleMapUiState
import com.codeint.ridertracking.internal.map.GoogleMapViewModel
import com.codeint.ridertracking.internal.map.RouteSegment
import com.codeint.ridertracking.internal.map.StoreLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

internal fun com.codeint.ridertracking.internal.map.LatLng.toGmsLatLng(): LatLng =
    LatLng(this.latitude, this.longitude)

internal fun List<com.codeint.ridertracking.internal.map.LatLng>.toGmsLatLngList(): List<LatLng> =
    this.map { it.toGmsLatLng() }

/** Convert internal UI state to public TrackingUiState for consumer overlays */
internal fun GoogleMapUiState.toTrackingUiState(): TrackingUiState {
    return TrackingUiState(
        riderLocation = animatedRiderLocation?.let { TrackingLocation(it.latitude, it.longitude) },
        riderHeading = riderHeading,
        isRiderMoving = isAnimating,
        stores = stores.map { TrackingStore(id = it.storeName, name = it.storeName, location = TrackingLocation(it.location.latitude, it.location.longitude), isPickedUp = it.isOrderPickedUp) },
        pendingStores = visibleStores.map { TrackingStore(id = it.storeName, name = it.storeName, location = TrackingLocation(it.location.latitude, it.location.longitude)) },
        pendingStoreCount = visibleStores.size,
        destination = multiStopDestination?.let { TrackingLocation(it.latitude, it.longitude) },
        isOrderArrived = isOrderArrived,
        isRerouting = isRerouting,
        isMapReady = shouldShowMap,
        isRouteVisible = isRouteVisible,
        phase = if (routingPhase == com.codeint.ridertracking.internal.map.RoutingPhase.POST_PICKUP) TrackingPhase.POST_PICKUP else TrackingPhase.PRE_PICKUP
    )
}

@Composable
internal fun InternalGoogleMapScreen(
    viewModel: GoogleMapViewModel,
    appearance: RiderTrackingAppearance = RiderTrackingAppearance(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRoute()
    }

    Box(modifier = modifier.defaultMinSize(minHeight = 200.dp)) {
        if (!uiState.shouldShowMap) {
            // Custom or default loading
            if (appearance.loadingContent != null) {
                appearance.loadingContent.invoke()
            } else {
                LoadingIndicator(message = appearance.loadingMessage, modifier = Modifier.align(Alignment.Center))
            }
        } else {
            GoogleMapContainer(viewModel = viewModel, uiState = uiState, appearance = appearance, modifier = Modifier.fillMaxSize()) // fills the parent Box

            // Custom map overlay (ETA cards, info panels, etc.)
            appearance.mapOverlayContent?.invoke(uiState.toTrackingUiState())
        }
    }
}

@Composable
private fun GoogleMapContainer(
    viewModel: GoogleMapViewModel,
    uiState: GoogleMapUiState,
    appearance: RiderTrackingAppearance,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(viewModel.getInitialCameraPosition().latitude, viewModel.getInitialCameraPosition().longitude),
            viewModel.getInitialCameraZoom()
        )
    }

    LaunchedEffect(uiState.shouldShowDeliveryData, uiState.cameraBounds) {
        if (uiState.shouldShowDeliveryData && uiState.cameraBounds != null) {
            delay(300)
            val bounds = LatLngBounds.builder()
                .include(LatLng(uiState.cameraBounds!!.southwest.latitude, uiState.cameraBounds!!.southwest.longitude))
                .include(LatLng(uiState.cameraBounds!!.northeast.latitude, uiState.cameraBounds!!.northeast.longitude))
                .build()
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, GoogleMapConstants.TIGHT_CAMERA_PADDING_PIXELS), 1500)
            } catch (_: Exception) {}
        }
    }

    val styleJson = appearance.mapStyleJson ?: GoogleMapConstants.MAP_UI_JSON
    val mapStyleOptions = remember(styleJson) { MapStyleOptions(styleJson) }
    val mapProperties = remember(mapStyleOptions) { MapProperties(isTrafficEnabled = false, mapStyleOptions = mapStyleOptions) }
    val mapUiSettings = remember(appearance.showCompass) {
        MapUiSettings(
            zoomControlsEnabled = false, compassEnabled = appearance.showCompass, myLocationButtonEnabled = false,
            rotationGesturesEnabled = true, scrollGesturesEnabled = true, tiltGesturesEnabled = true,
            zoomGesturesEnabled = true, mapToolbarEnabled = false, indoorLevelPickerEnabled = false
        )
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = { viewModel.toggleFollowRider() }
        ) {
            MapContentContainer(uiState = uiState, appearance = appearance)
        }

        if (uiState.isRerouting) {
            if (appearance.reroutingContent != null) {
                appearance.reroutingContent.invoke()
            } else {
                ReroutingOverlay(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun MapContentContainer(uiState: GoogleMapUiState, appearance: RiderTrackingAppearance) {
    MultiStopMapContent(uiState = uiState, appearance = appearance)
    RiderMarker(
        animatedRiderLocation = uiState.animatedRiderLocation?.toGmsLatLng(),
        riderHeading = uiState.riderHeading,
        appearance = appearance
    )
}

@Composable
internal fun RiderMarker(animatedRiderLocation: LatLng?, riderHeading: Double, appearance: RiderTrackingAppearance) {
    val context = LocalContext.current
    val riderIcon = remember(appearance.riderIcon, appearance.riderIconSize) {
        val drawableRes = appearance.riderIcon ?: R.drawable.ic_rider
        val size = appearance.riderIconSize
        val bitmap = ContextCompat.getDrawable(context, drawableRes)?.toBitmap(size, size)
            ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    animatedRiderLocation?.let { location ->
        Marker(
            state = MarkerState(position = location),
            icon = riderIcon,
            rotation = riderHeading.toFloat(),
            anchor = Offset(0.5f, 0.5f)
        )
    }
}

@Composable
internal fun MultiStopMapContent(uiState: GoogleMapUiState, appearance: RiderTrackingAppearance) {
    key(uiState.stores.map { it.isOrderPickedUp }) {
        MultiStopStoreMarkers(stores = uiState.stores, appearance = appearance)
    }

    MultiStopDestinationMarker(destination = uiState.multiStopDestination?.toGmsLatLng(), appearance = appearance)

    if (uiState.isOrderArrived && appearance.showArrivalCircle) {
        DestinationArrivalCircle(destination = uiState.multiStopDestination?.toGmsLatLng(), appearance = appearance)
    }

    MultiStopRouteSegments(
        activeSegment = uiState.activeSegment,
        isRerouting = uiState.isRerouting,
        visitedRoutePoints = uiState.visitedRoutePoints.toGmsLatLngList(),
        activePathSegment = uiState.activePathSegment.toGmsLatLngList(),
        inactivePathSegments = uiState.inactivePathSegments.toGmsLatLngList(),
        isRouteVisible = uiState.isRouteVisible,
        appearance = appearance
    )
}

@Composable
internal fun MultiStopStoreMarkers(stores: List<StoreLocation>, appearance: RiderTrackingAppearance) {
    val context = LocalContext.current

    val storeIcons = stores.associateWith { store ->
        val storeName = if (store.storeName.length > 10) store.storeName.substring(0, 10).plus("..") else store.storeName
        createStoreMarkerBitmap(
            context = context,
            storeName = storeName,
            isOrderPickedUp = store.isOrderPickedUp,
            appearance = appearance
        )
    }

    stores.forEach { store ->
        storeIcons[store]?.let { icon ->
            Marker(
                state = MarkerState(position = LatLng(store.location.latitude, store.location.longitude)),
                icon = icon,
                anchor = Offset(0.5f, 1.0f),
                title = store.storeName
            )
        }
    }
}

@Composable
internal fun createStoreMarkerBitmap(
    context: Context,
    storeName: String,
    isOrderPickedUp: Boolean,
    appearance: RiderTrackingAppearance
): BitmapDescriptor {
    val storeDrawableRes = appearance.storeIcon ?: R.drawable.ic_store_marker
    val size = appearance.storeIconSize
    val storeIconBitmap = ContextCompat.getDrawable(context, storeDrawableRes)?.toBitmap(size, size)
        ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    val showLabel = appearance.showStoreLabels
    if (!showLabel) {
        return BitmapDescriptorFactory.fromBitmap(storeIconBitmap)
    }

    val bitmap = createCustomStoreMarker(
        context = context,
        storeName = storeName,
        isOrderPickedUp = isOrderPickedUp,
        storeIconBitmap = storeIconBitmap,
        overlayBoldColor = appearance.storeLabelBackground.toArgb(),
        positiveColor = appearance.storePickupCheckColor.toArgb(),
        inverseColor = Color.White.toArgb(),
        textInverseColor = appearance.storeLabelTextColor.toArgb()
    )
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal fun createCustomStoreMarker(
    context: Context, storeName: String, isOrderPickedUp: Boolean,
    storeIconBitmap: Bitmap, overlayBoldColor: Int, positiveColor: Int, inverseColor: Int, textInverseColor: Int
): Bitmap {
    val density = context.resources.displayMetrics.density
    val labelHeight = (20 * density).toInt()
    val leftPadding = (4 * density).toInt()
    val rightPadding = (6 * density).toInt()
    val checkmarkTextPadding = (2 * density).toInt()
    val arrowHeight = (8 * density).toInt()
    val checkmarkSize = (12 * density).toInt()
    val cornerRadius = (4 * density)

    val textPaint = Paint().apply {
        color = textInverseColor
        textSize = 10 * context.resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    val textBounds = Rect()
    textPaint.getTextBounds(storeName, 0, storeName.length, textBounds)
    val textWidth = textBounds.width()
    val checkmarkSpace = if (isOrderPickedUp) checkmarkSize + checkmarkTextPadding else 0
    val labelWidth = maxOf(textWidth + leftPadding + rightPadding + checkmarkSpace, (50 * density).toInt())
    val totalWidth = maxOf(labelWidth, storeIconBitmap.width)
    val totalHeight = labelHeight + arrowHeight + storeIconBitmap.height

    val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val labelX = (totalWidth - labelWidth) / 2f
    val labelY = 0f
    val storeIconX = (totalWidth - storeIconBitmap.width) / 2f
    val storeIconY = labelHeight + arrowHeight.toFloat()

    val backgroundPaint = Paint().apply { color = overlayBoldColor; isAntiAlias = true }
    canvas.drawRoundRect(RectF(labelX, labelY, labelX + labelWidth, labelY + labelHeight), cornerRadius, cornerRadius, backgroundPaint)

    val arrowPath = Path().apply {
        val cx = labelX + labelWidth / 2f; val top = labelY + labelHeight; val w = 12 * density
        moveTo(cx - w / 2, top); lineTo(cx + w / 2, top); lineTo(cx, top + arrowHeight); close()
    }
    canvas.drawPath(arrowPath, backgroundPaint)

    val centerY = labelY + labelHeight / 2f
    if (isOrderPickedUp) {
        val checkX = labelX + leftPadding; val checkY = centerY - checkmarkSize / 2f
        drawCheckmark(canvas, checkX, checkY, checkmarkSize.toFloat(), positiveColor, inverseColor)
        canvas.drawText(storeName, checkX + checkmarkSize + checkmarkTextPadding, centerY + textBounds.height() / 2f - textBounds.bottom / 2f, textPaint)
    } else {
        canvas.drawText(storeName, labelX + (labelWidth - textWidth) / 2f, centerY + textBounds.height() / 2f - textBounds.bottom / 2f, textPaint)
    }

    canvas.drawBitmap(storeIconBitmap, storeIconX, storeIconY, null)
    return bitmap
}

internal fun drawCheckmark(canvas: Canvas, x: Float, y: Float, size: Float, positiveColor: Int, inverseColor: Int) {
    canvas.drawCircle(x + size / 2f, y + size / 2f, size * 0.45f, Paint().apply { color = positiveColor; style = Paint.Style.FILL; isAntiAlias = true })
    val s = size * 0.3f; val cx = x + size / 2f; val cy = y + size / 2f
    canvas.drawPath(Path().apply {
        moveTo(cx - s * 0.4f, cy); lineTo(cx - s * 0.1f, cy + s * 0.3f); lineTo(cx + s * 0.4f, cy - s * 0.2f)
    }, Paint().apply { color = inverseColor; strokeWidth = size * 0.12f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true })
}

@Composable
internal fun MultiStopDestinationMarker(destination: LatLng?, appearance: RiderTrackingAppearance) {
    val context = LocalContext.current
    destination?.let { dest ->
        val icon = remember(appearance.destinationIcon, appearance.destinationIconWidth, appearance.destinationIconHeight) {
            val drawableRes = appearance.destinationIcon ?: R.drawable.ic_destination_marker
            val w = appearance.destinationIconWidth; val h = appearance.destinationIconHeight
            val bitmap = ContextCompat.getDrawable(context, drawableRes)?.toBitmap(w, h)
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        Marker(state = MarkerState(position = dest), icon = icon, alpha = 1.0f, title = "Destination")
    }
}

@Composable
internal fun MultiStopRouteSegments(
    activeSegment: RouteSegment?, isRerouting: Boolean,
    visitedRoutePoints: List<LatLng>, activePathSegment: List<LatLng>,
    inactivePathSegments: List<LatLng>, isRouteVisible: Boolean,
    appearance: RiderTrackingAppearance
) {
    val routeWidth = appearance.routeWidth

    if (visitedRoutePoints.isNotEmpty() || activePathSegment.isNotEmpty() || inactivePathSegments.isNotEmpty()) {
        if (visitedRoutePoints.size > 1) {
            Polyline(points = visitedRoutePoints, color = appearance.visitedRouteColor, width = routeWidth * 0.6f,
                pattern = listOf(Dash(10f), Gap(5f)))
        }
        if (inactivePathSegments.size > 1 && isRouteVisible) {
            Polyline(points = inactivePathSegments, color = appearance.inactiveRouteColor, width = routeWidth)
        }
        if (activePathSegment.size > 1 && isRouteVisible) {
            Polyline(points = activePathSegment, color = appearance.activeRouteColor, width = routeWidth,
                pattern = if (isRerouting && appearance.reroutingDashPattern != null) listOf(Dash(appearance.reroutingDashPattern[0]), Gap(appearance.reroutingDashPattern[1])) else null)
        }
    } else {
        activeSegment?.let { segment ->
            Polyline(points = segment.routePoints.map { LatLng(it.latitude, it.longitude) },
                color = appearance.activeRouteColor, width = routeWidth,
                pattern = if (isRerouting && appearance.reroutingDashPattern != null) listOf(Dash(appearance.reroutingDashPattern[0]), Gap(appearance.reroutingDashPattern[1])) else null)
        }
    }
}

@Composable
private fun LoadingIndicator(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReroutingOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Re-routing...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Finding the best route", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun DestinationArrivalCircle(destination: LatLng?, appearance: RiderTrackingAppearance) {
    destination?.let { dest ->
        val radius = appearance.arrivalCircleRadiusMeters
        Circle(center = dest, radius = radius, fillColor = appearance.arrivalCircleFill, strokeColor = Color.Transparent, strokeWidth = 0f)
        Circle(center = dest, radius = radius, fillColor = Color.Transparent, strokeColor = appearance.arrivalCircleStroke, strokeWidth = appearance.arrivalCircleStrokeWidth, strokePattern = listOf(Dash(14f), Gap(12f)))
    }
}
