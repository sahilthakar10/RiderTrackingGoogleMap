package com.codeint.ridertracking.api

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.codeint.ridertracking.internal.di.SdkDependencies
import com.codeint.ridertracking.internal.map.GoogleMapViewModel
import com.codeint.ridertracking.internal.map.SimpleLocation
import com.codeint.ridertracking.internal.ui.InternalGoogleMapScreen

/**
 * Main composable for displaying a rider tracking map.
 *
 * Usage:
 * ```kotlin
 * RiderTrackingMap(
 *     order = TrackingOrder(
 *         orderId = "order123",
 *         stores = listOf(
 *             TrackingStore("s1", "Pizza Palace", TrackingLocation(12.91, 77.67))
 *         ),
 *         destination = TrackingLocation(12.92, 77.66)
 *     ),
 *     modifier = Modifier.fillMaxSize(),
 *     onEvent = { event ->
 *         when (event) {
 *             is TrackingEvent.OrderArrived -> { /* handle */ }
 *             is TrackingEvent.StorePickedUp -> { /* handle */ }
 *             else -> {}
 *         }
 *     }
 * )
 * ```
 *
 * @param order The order to track on the map.
 * @param modifier Modifier for the map container.
 * @param onEvent Callback for tracking events (arrivals, pickups, etc.).
 */
@Composable
fun RiderTrackingMap(
    order: TrackingOrder,
    modifier: Modifier = Modifier,
    onEvent: ((TrackingEvent) -> Unit)? = null
) {
    RiderTrackingSDK.checkInitialized()

    val dependencies = remember(order.orderId) {
        SdkDependencies.create(RiderTrackingSDK.config)
    }

    val viewModel = remember(order.orderId) {
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

    DisposableEffect(order.orderId) {
        onDispose {
            dependencies.cleanup()
        }
    }

    InternalGoogleMapScreen(
        viewModel = viewModel,
        modifier = modifier.fillMaxSize()
    )
}
