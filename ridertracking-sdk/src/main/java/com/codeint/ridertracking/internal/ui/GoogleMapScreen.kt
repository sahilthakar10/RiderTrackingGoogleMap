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
import com.codeint.ridertracking.internal.map.GoogleMapConstants
import com.codeint.ridertracking.internal.map.GoogleMapConstants.DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS
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
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

// Extension functions to convert between our LatLng and Google Maps LatLng
internal fun com.codeint.ridertracking.internal.map.LatLng.toGmsLatLng(): LatLng =
    LatLng(this.latitude, this.longitude)

internal fun List<com.codeint.ridertracking.internal.map.LatLng>.toGmsLatLngList(): List<LatLng> =
    this.map { it.toGmsLatLng() }

// Color constants
internal val ActiveRouteColor = Color(0xFF1A73E8)
internal val InactiveRouteColor = Color(0xFF90CAF9)
internal val ArrivalCircleFill = Color(0x401A73E8)
internal val ArrivalCircleStroke = Color(0xFF1A73E8)
internal val OverlayBoldColor = Color(0xCC333333)
internal val PositiveColor = Color(0xFF4CAF50)
internal val InverseColor = Color(0xFFFFFFFF)
internal val TextInverseColor = Color(0xFFFFFFFF)

@Composable
internal fun InternalGoogleMapScreen(
    viewModel: GoogleMapViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRoute()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!uiState.shouldShowMap) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            GoogleMapContainer(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GoogleMapContainer(
    viewModel: GoogleMapViewModel,
    uiState: GoogleMapUiState,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(
                viewModel.getInitialCameraPosition().latitude,
                viewModel.getInitialCameraPosition().longitude
            ),
            viewModel.getInitialCameraZoom()
        )
    }

    LaunchedEffect(uiState.shouldShowDeliveryData, uiState.cameraBounds) {
        if (uiState.shouldShowDeliveryData && uiState.cameraBounds != null) {
            delay(300)
            val boundsBuilder = LatLngBounds.builder()
                .include(LatLng(uiState.cameraBounds!!.southwest.latitude, uiState.cameraBounds!!.southwest.longitude))
                .include(LatLng(uiState.cameraBounds!!.northeast.latitude, uiState.cameraBounds!!.northeast.longitude))
                .build()

            val padding = GoogleMapConstants.TIGHT_CAMERA_PADDING_PIXELS
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(boundsBuilder, padding)
            try {
                cameraPositionState.animate(cameraUpdate, 1500)
            } catch (_: Exception) {}
        }
    }

    val mapStyleOptions = remember { MapStyleOptions(GoogleMapConstants.MAP_UI_JSON) }
    val mapProperties = remember { MapProperties(isTrafficEnabled = false, mapStyleOptions = mapStyleOptions) }
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true,
            mapToolbarEnabled = false,
            indoorLevelPickerEnabled = false
        )
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                viewModel.toggleFollowRider()
            }
        ) {
            MapContentContainer(uiState = uiState)
        }

        if (uiState.isRerouting) {
            ReroutingOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun MapContentContainer(uiState: GoogleMapUiState) {
    MultiStopMapContent(uiState = uiState)
    RiderMarkerContainer(
        animatedRiderLocation = uiState.animatedRiderLocation?.toGmsLatLng(),
        riderHeading = uiState.riderHeading
    )
}

@Composable
internal fun createStoreMarkerWithLabel(
    storeName: String,
    isOrderPickedUp: Boolean,
    context: Context = LocalContext.current
): BitmapDescriptor {
    val storeIconBitmap = ContextCompat.getDrawable(context, R.drawable.ic_store_marker)?.toBitmap(48, 48)
        ?: Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

    val customMarkerBitmap = createCustomStoreMarker(
        context = context,
        storeName = storeName,
        isOrderPickedUp = isOrderPickedUp,
        storeIconBitmap = storeIconBitmap,
        overlayBoldColor = OverlayBoldColor.toArgb(),
        positiveColor = PositiveColor.toArgb(),
        inverseColor = InverseColor.toArgb(),
        textInverseColor = TextInverseColor.toArgb()
    )
    return BitmapDescriptorFactory.fromBitmap(customMarkerBitmap)
}

internal fun createCustomStoreMarker(
    context: Context,
    storeName: String,
    isOrderPickedUp: Boolean,
    storeIconBitmap: Bitmap,
    overlayBoldColor: Int,
    positiveColor: Int,
    inverseColor: Int,
    textInverseColor: Int
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

    val labelRect = RectF(labelX, labelY, labelX + labelWidth, labelY + labelHeight)
    val backgroundPaint = Paint().apply {
        color = overlayBoldColor
        isAntiAlias = true
    }
    canvas.drawRoundRect(labelRect, cornerRadius, cornerRadius, backgroundPaint)

    val arrowPath = Path().apply {
        val arrowCenterX = labelX + labelWidth / 2f
        val arrowTop = labelY + labelHeight
        val arrowBottom = arrowTop + arrowHeight
        val arrowWidth = (12 * density)
        moveTo(arrowCenterX - arrowWidth / 2, arrowTop)
        lineTo(arrowCenterX + arrowWidth / 2, arrowTop)
        lineTo(arrowCenterX, arrowBottom)
        close()
    }
    canvas.drawPath(arrowPath, backgroundPaint)

    val centerY = labelY + labelHeight / 2f

    if (isOrderPickedUp) {
        val checkmarkX = labelX + leftPadding
        val checkmarkY = centerY - checkmarkSize / 2f
        drawCheckmark(canvas, checkmarkX, checkmarkY, checkmarkSize.toFloat(), positiveColor, inverseColor)
        val textX = checkmarkX + checkmarkSize + checkmarkTextPadding
        val textY = centerY + textBounds.height() / 2f - textBounds.bottom / 2f
        canvas.drawText(storeName, textX, textY, textPaint)
    } else {
        val textX = labelX + (labelWidth - textWidth) / 2f
        val textY = centerY + textBounds.height() / 2f - textBounds.bottom / 2f
        canvas.drawText(storeName, textX, textY, textPaint)
    }

    canvas.drawBitmap(storeIconBitmap, storeIconX, storeIconY, null)
    return bitmap
}

internal fun drawCheckmark(canvas: Canvas, x: Float, y: Float, size: Float, positiveColor: Int, inverseColor: Int) {
    val circlePaint = Paint().apply {
        color = positiveColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val circleRadius = size * 0.45f
    val centerX = x + size / 2f
    val centerY = y + size / 2f
    canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

    val checkPaint = Paint().apply {
        color = inverseColor
        strokeWidth = size * 0.12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val checkPath = Path().apply {
        val checkSize = size * 0.3f
        moveTo(centerX - checkSize * 0.4f, centerY)
        lineTo(centerX - checkSize * 0.1f, centerY + checkSize * 0.3f)
        lineTo(centerX + checkSize * 0.4f, centerY - checkSize * 0.2f)
    }
    canvas.drawPath(checkPath, checkPaint)
}

@Composable
internal fun RiderMarkerContainer(
    animatedRiderLocation: LatLng?,
    riderHeading: Double
) {
    RiderMarker(animatedRiderLocation = animatedRiderLocation, riderHeading = riderHeading)
}

@Composable
internal fun RiderMarker(
    animatedRiderLocation: LatLng?,
    riderHeading: Double
) {
    val context = LocalContext.current
    val riderIcon = remember {
        val bitmap = ContextCompat.getDrawable(context, R.drawable.ic_rider)?.toBitmap(64, 64)
            ?: Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    animatedRiderLocation?.let { location ->
        Marker(
            state = MarkerState(position = location),
            icon = riderIcon,
            rotation = riderHeading.toFloat(),
            anchor = Offset(GoogleMapConstants.MARKER_ANCHOR_CENTER, GoogleMapConstants.MARKER_ANCHOR_CENTER)
        )
    }
}

@Composable
internal fun MultiStopMapContent(uiState: GoogleMapUiState) {
    key(uiState.stores.map { it.isOrderPickedUp }) {
        MultiStopStoreMarkers(stores = uiState.stores)
    }

    MultiStopDestinationMarker(destination = uiState.multiStopDestination?.toGmsLatLng())

    if (uiState.isOrderArrived) {
        DestinationArrivalCircle(destination = uiState.multiStopDestination?.toGmsLatLng())
    }

    MultiStopRouteSegments(
        activeSegment = uiState.activeSegment,
        isRerouting = uiState.isRerouting,
        visitedRoutePoints = uiState.visitedRoutePoints.toGmsLatLngList(),
        activePathSegment = uiState.activePathSegment.toGmsLatLngList(),
        inactivePathSegments = uiState.inactivePathSegments.toGmsLatLngList(),
        isRouteVisible = uiState.isRouteVisible
    )
}

@Composable
internal fun MultiStopStoreMarkers(stores: List<StoreLocation>) {
    val context = LocalContext.current

    val storeIcons = stores.associateWith { store ->
        val storeName = if (store.storeName.length > 10) {
            store.storeName.substring(0, 10).plus("..")
        } else {
            store.storeName
        }
        createStoreMarkerWithLabel(storeName = storeName, isOrderPickedUp = store.isOrderPickedUp, context = context)
    }

    stores.forEach { store ->
        val icon = storeIcons[store]
        icon?.let {
            Marker(
                state = MarkerState(position = LatLng(store.location.latitude, store.location.longitude)),
                icon = it,
                anchor = Offset(0.5f, 1.0f),
                title = store.storeName
            )
        }
    }
}

@Composable
internal fun MultiStopDestinationMarker(destination: LatLng?) {
    val context = LocalContext.current
    destination?.let { dest ->
        val destinationIcon = remember {
            val bitmap = ContextCompat.getDrawable(context, R.drawable.ic_destination_marker)?.toBitmap(48, 60)
                ?: Bitmap.createBitmap(48, 60, Bitmap.Config.ARGB_8888)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }

        Marker(
            state = MarkerState(position = dest),
            icon = destinationIcon,
            alpha = 1.0f,
            title = "Final Destination",
            snippet = "Delivering Now"
        )
    }
}

@Composable
internal fun MultiStopRouteSegments(
    activeSegment: RouteSegment?,
    isRerouting: Boolean,
    visitedRoutePoints: List<LatLng>,
    activePathSegment: List<LatLng>,
    inactivePathSegments: List<LatLng>,
    isRouteVisible: Boolean
) {
    if (visitedRoutePoints.isNotEmpty() || activePathSegment.isNotEmpty() || inactivePathSegments.isNotEmpty()) {
        // Visited route (gray, hidden)
        if (visitedRoutePoints.size > 1) {
            Polyline(
                points = visitedRoutePoints,
                color = Color.Gray.copy(alpha = 0f),
                width = GoogleMapConstants.VISITED_ROUTE_POLYLINE_WIDTH,
                pattern = listOf(
                    Dash(GoogleMapConstants.VISITED_ROUTE_DASH_LENGTH),
                    Gap(GoogleMapConstants.VISITED_ROUTE_DASH_GAP)
                )
            )
        }

        // Inactive path (light blue)
        if (inactivePathSegments.size > 1 && isRouteVisible) {
            Polyline(
                points = inactivePathSegments,
                color = InactiveRouteColor,
                width = GoogleMapConstants.ROUTE_POLYLINE_WIDTH,
                pattern = null
            )
        }

        // Active path (dark blue)
        if (activePathSegment.size > 1 && isRouteVisible) {
            Polyline(
                points = activePathSegment,
                color = ActiveRouteColor,
                width = GoogleMapConstants.ROUTE_POLYLINE_WIDTH,
                pattern = when {
                    isRerouting -> listOf(Dash(GoogleMapConstants.DASH_LENGTH), Gap(GoogleMapConstants.DASH_GAP))
                    else -> null
                }
            )
        }
    } else {
        activeSegment?.let { segment ->
            Polyline(
                points = segment.routePoints.map { LatLng(it.latitude, it.longitude) },
                color = ActiveRouteColor,
                width = GoogleMapConstants.ROUTE_POLYLINE_WIDTH,
                pattern = when {
                    isRerouting -> listOf(Dash(GoogleMapConstants.DASH_LENGTH), Gap(GoogleMapConstants.DASH_GAP))
                    else -> null
                }
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Loading delivery information...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReroutingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Re-routing...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Finding the best route",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun DestinationArrivalCircle(destination: LatLng?) {
    destination?.let { dest ->
        Circle(
            center = dest,
            radius = DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS,
            fillColor = ArrivalCircleFill,
            strokeColor = Color.Transparent,
            strokeWidth = 0f
        )
        Circle(
            center = dest,
            radius = DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS,
            fillColor = Color.Transparent,
            strokeColor = ArrivalCircleStroke,
            strokeWidth = 3f,
            strokePattern = listOf(Dash(14f), Gap(12f))
        )
    }
}
