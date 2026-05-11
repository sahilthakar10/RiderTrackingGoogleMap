package com.codeint.ridertracking.internal.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository that wraps a consumer-provided rider location Flow
 * while delegating everything else to the base repository (simulation or real).
 *
 * This lets consumers push their own real-time rider locations
 * while the SDK handles order init, routes, pickup status internally.
 */
internal class LiveLocationRepository(
    private val baseRepository: GoogleMapRepository,
    private val externalLocationFlow: Flow<SimpleLocation>
) : GoogleMapRepository {

    override fun init(globalOrderId: String, batchOrderId: String): Flow<OrderResponseData?> =
        baseRepository.init(globalOrderId, batchOrderId)

    override suspend fun getRoute(request: RouteRequest, batchOrderId: String, isDeviated: Boolean): Result<List<LatLng>?> =
        baseRepository.getRoute(request, batchOrderId, isDeviated)

    override fun getRiderLatestLocation(batchOrderId: String): Flow<SimpleLocation?> =
        externalLocationFlow.map { it } // Use the consumer's flow instead of base

    override fun updateRoute(batchOrderId: String, route: List<LatLng>) =
        baseRepository.updateRoute(batchOrderId, route)

    override fun getPickupStatusFlow(): Flow<List<StoreLocation>> =
        baseRepository.getPickupStatusFlow()
}
