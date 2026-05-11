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
 * **Simulation mode** (default):
 * ```kotlin
 * RiderTrackingMap(
 *     order = TrackingOrder(
 *         orderId = "order123",
 *         stores = listOf(TrackingStore("s1", "Pizza Palace", TrackingLocation(12.91, 77.67))),
 *         destination = TrackingLocation(12.92, 77.66)
 *     )
 * )
 * ```
 *
 * **Live rider tracking** (push location updates manually):
 * ```kotlin
 * val riderLocations = MutableSharedFlow<TrackingLocation>()
 *
 * RiderTrackingMap(
 *     order = myOrder,
 *     riderLocationUpdates = riderLocations,
 *     onEvent = { event -> /* handle */ }
 * )
 *
 * // Push updates whenever you get new rider coordinates
 * riderLocations.emit(TrackingLocation(12.91, 77.67))
 * ```
 *
 * @param order The order to track on the map.
 * @param modifier Modifier for the map container.
 * @param riderLocationUpdates Optional flow of real-time rider locations.
 *        When provided, overrides the internal simulation/polling.
 *        Emit new [TrackingLocation] values as the rider moves.
 * @param onEvent Callback for tracking events (arrivals, pickups, etc.).
 */
@Composable
fun RiderTrackingMap(
    order: TrackingOrder,
    modifier: Modifier = Modifier,
    riderLocationUpdates: Flow<TrackingLocation>? = null,
    onEvent: ((TrackingEvent) -> Unit)? = null
) {
    if (!RiderTrackingSDK.isInitialized) return

    val currentOnEvent = rememberUpdatedState(onEvent)

    val internalLocationFlow = remember(riderLocationUpdates) {
        riderLocationUpdates?.map { location ->
            SimpleLocation(latitude = location.latitude, longitude = location.longitude)
        }
    }

    val dependencies = remember(order.orderId, internalLocationFlow) {
        SdkDependencies.create(
            config = RiderTrackingSDK.config,
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
        modifier = modifier.fillMaxSize()
    )
}
