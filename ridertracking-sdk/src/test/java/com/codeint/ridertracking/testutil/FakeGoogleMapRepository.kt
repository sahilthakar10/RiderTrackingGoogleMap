package com.codeint.ridertracking.testutil

import com.codeint.ridertracking.internal.map.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class FakeGoogleMapRepository : GoogleMapRepository {

    var orderResponseToEmit: OrderResponseData? = null
    var routeToReturn: List<LatLng>? = null
    var locationToEmit: SimpleLocation? = null
    var shouldThrowOnInit = false
    var shouldThrowOnGetRoute = false

    private val _pickupStatusFlow = MutableSharedFlow<List<StoreLocation>>(replay = 1)

    override fun init(globalOrderId: String, batchOrderId: String): Flow<OrderResponseData?> = flow {
        if (shouldThrowOnInit) throw RuntimeException("init failed")
        emit(orderResponseToEmit)
    }

    override suspend fun getRoute(request: RouteRequest, batchOrderId: String, isDeviated: Boolean): Result<List<LatLng>?> {
        if (shouldThrowOnGetRoute) return Result.failure(RuntimeException("route failed"))
        return Result.success(routeToReturn)
    }

    override fun getRiderLatestLocation(batchOrderId: String): Flow<SimpleLocation?> = flow {
        emit(locationToEmit)
    }

    override fun updateRoute(batchOrderId: String, route: List<LatLng>) {}

    override fun getPickupStatusFlow(): Flow<List<StoreLocation>> = _pickupStatusFlow

    suspend fun emitPickupStatus(stores: List<StoreLocation>) {
        _pickupStatusFlow.emit(stores)
    }
}
