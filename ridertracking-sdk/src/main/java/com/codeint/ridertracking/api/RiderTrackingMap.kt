package com.codeint.ridertracking.api

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.codeint.ridertracking.internal.di.SdkDependencies
import com.codeint.ridertracking.internal.map.GoogleMapViewModel
import com.codeint.ridertracking.internal.map.SimpleLocation
import com.codeint.ridertracking.internal.ui.InternalGoogleMapScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Main composable for displaying a rider tracking map.
 *
 * ```kotlin
 * RiderTrackingMap(
 *     order = myOrder,
 *     appearance = RiderTrackingAppearance(
 *         activeRouteColor = Color.Green,
 *         riderIcon = R.drawable.my_rider,
 *         mapStyleJson = myStyleJson
 *     ),
 *     riderLocationUpdates = riderLocations,
 *     onEvent = { event -> /* handle */ }
 * )
 * ```
 *
 * @param order The order to track on the map.
 * @param modifier Modifier for the map container.
 * @param appearance Customize map colors, icons, labels, and style.
 * @param riderLocationUpdates Optional flow of real-time rider locations.
 * @param onEvent Callback for tracking events.
 */
@Composable
fun RiderTrackingMap(
    order: TrackingOrder,
    modifier: Modifier = Modifier,
    appearance: RiderTrackingAppearance = RiderTrackingAppearance(),
    riderLocationUpdates: Flow<TrackingLocation>? = null,
    onEvent: ((TrackingEvent) -> Unit)? = null
) {
    if (!RiderTrackingSDK.isInitialized) return

    val currentOnEvent = rememberUpdatedState(onEvent)
    val currentAppearance = rememberUpdatedState(appearance)

    val internalLocationFlow = remember(riderLocationUpdates) {
        riderLocationUpdates?.map { location ->
            SimpleLocation(latitude = location.latitude, longitude = location.longitude)
        }
    }

    val dependencies = remember(order.orderId, internalLocationFlow) {
        SdkDependencies.create(
            config = RiderTrackingSDK.config,
            order = order,
            riderLocationFlow = internalLocationFlow
        )
    }

    val viewModel = remember(order.orderId, internalLocationFlow) {
        GoogleMapViewModel(dependencies.useCase).also { vm ->
            vm.set(
                globalOrderId = order.orderId,
                batchOrderId = order.orderId,
                destination = SimpleLocation(
                    latitude = order.destination.latitude,
                    longitude = order.destination.longitude
                )
            )
        }
    }

    DisposableEffect(order.orderId, internalLocationFlow) {
        onDispose {
            viewModel.destroy()
            dependencies.cleanup()
        }
    }

    InternalGoogleMapScreen(
        viewModel = viewModel,
        appearance = currentAppearance.value,
        modifier = modifier.fillMaxSize()
    )
}
