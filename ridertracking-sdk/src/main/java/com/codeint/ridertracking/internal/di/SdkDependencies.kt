package com.codeint.ridertracking.internal.di

import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.internal.map.GoogleMapRepository
import com.codeint.ridertracking.internal.map.GoogleMapRepositorySimulation
import com.codeint.ridertracking.internal.map.GoogleMapUseCase
import com.codeint.ridertracking.internal.map.RouteCache
import com.codeint.ridertracking.internal.map.network.GoogleMapNetworkRepositoryImpl
import com.codeint.ridertracking.internal.map.network.RouteApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Internal dependency factory for the SDK.
 * Creates all needed dependencies without requiring Koin/Hilt from the consumer.
 */
internal class SdkDependencies private constructor(
    val useCase: GoogleMapUseCase
) {

    companion object {
        fun create(config: RiderTrackingConfig): SdkDependencies {
            val routeCache = RouteCache()

            val repository: GoogleMapRepository = if (config.useSimulation) {
                GoogleMapRepositorySimulation()
            } else {
                // For real implementation, would use a real repository
                // For now, fall back to simulation
                GoogleMapRepositorySimulation()
            }

            val useCase = GoogleMapUseCase(repository, routeCache)

            return SdkDependencies(useCase)
        }

        private fun createRetrofit(config: RiderTrackingConfig): Retrofit {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://routes.googleapis.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    fun cleanup() {
        // Cleanup resources if needed
    }
}
