package com.codeint.ridertracking.internal.di

import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.internal.map.GoogleMapRepository
import com.codeint.ridertracking.internal.map.GoogleMapRepositorySimulation
import com.codeint.ridertracking.internal.map.GoogleMapUseCase
import com.codeint.ridertracking.internal.map.LiveLocationRepository
import com.codeint.ridertracking.internal.map.RouteCache
import com.codeint.ridertracking.internal.map.SimpleLocation
import kotlinx.coroutines.flow.Flow

internal class SdkDependencies private constructor(
    val useCase: GoogleMapUseCase,
    private val routeCache: RouteCache
) {

    companion object {
        fun create(
            config: RiderTrackingConfig,
            riderLocationFlow: Flow<SimpleLocation>? = null
        ): SdkDependencies {
            val routeCache = RouteCache()
            val baseRepository: GoogleMapRepository = GoogleMapRepositorySimulation()

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
