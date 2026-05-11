package com.codeint.ridertracking.internal.di

import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.api.TrackingOrder
import com.codeint.ridertracking.internal.map.GoogleMapRepository
import com.codeint.ridertracking.internal.map.GoogleMapRepositorySimulation
import com.codeint.ridertracking.internal.map.GoogleMapUseCase
import com.codeint.ridertracking.internal.map.LatLng
import com.codeint.ridertracking.internal.map.LiveLocationRepository
import com.codeint.ridertracking.internal.map.RouteCache
import com.codeint.ridertracking.internal.map.SimpleLocation
import com.codeint.ridertracking.internal.map.StoreConfig
import kotlinx.coroutines.flow.Flow

internal class SdkDependencies private constructor(
    val useCase: GoogleMapUseCase,
    private val routeCache: RouteCache
) {

    companion object {
        fun create(
            config: RiderTrackingConfig,
            order: TrackingOrder,
            riderLocationFlow: Flow<SimpleLocation>? = null
        ): SdkDependencies {
            val routeCache = RouteCache()

            val simulation = GoogleMapRepositorySimulation()
            // Configure simulation with the consumer's order data
            simulation.configure(
                stores = order.stores.map { store ->
                    StoreConfig(
                        id = store.id,
                        name = store.name,
                        latitude = store.location.latitude,
                        longitude = store.location.longitude
                    )
                },
                destination = LatLng(order.destination.latitude, order.destination.longitude)
            )

            val baseRepository: GoogleMapRepository = simulation

            val repository: GoogleMapRepository = if (riderLocationFlow != null) {
                LiveLocationRepository(baseRepository, riderLocationFlow)
            } else {
                baseRepository
            }

            val useCase = GoogleMapUseCase(repository, routeCache)
            return SdkDependencies(useCase, routeCache)
        }
    }

    fun cleanup() {
        routeCache.clearAllCache()
    }
}
