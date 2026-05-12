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
import com.codeint.ridertracking.internal.map.network.RouteApiService
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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

            // Inject Routes API for real road-following routes
            try {
                val retrofit = createRetrofit()
                val routeApiService = retrofit.create(RouteApiService::class.java)
                simulation.setRouteApiService(routeApiService, config.routesApiKey ?: "")
            } catch (_: Exception) {
                // No route API - simulation will use straight lines
            }

            simulation.configure(
                stores = order.stores.map { store ->
                    StoreConfig(id = store.id, name = store.name, latitude = store.location.latitude, longitude = store.location.longitude)
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

        private fun createRetrofit(): Retrofit {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://routes.googleapis.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    fun cleanup() {
        routeCache.clearAllCache()
    }
}
