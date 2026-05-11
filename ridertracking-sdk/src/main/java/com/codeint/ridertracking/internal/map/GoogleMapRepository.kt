package com.codeint.ridertracking.internal.map

import kotlinx.coroutines.flow.Flow

interface GoogleMapRepository {
    fun init(globalOrderId: String, batchOrderId: String): Flow<OrderResponseData?>
    suspend fun getRoute(
        request: RouteRequest,
        batchOrderId: String,
        isDeviated: Boolean
    ): Result<List<LatLng>?>

    fun getRiderLatestLocation(batchOrderId: String): Flow<SimpleLocation?>
    fun updateRoute(batchOrderId: String, route: List<LatLng>)
    fun getPickupStatusFlow(): Flow<List<StoreLocation>>
}
