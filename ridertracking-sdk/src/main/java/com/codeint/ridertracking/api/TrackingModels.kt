package com.codeint.ridertracking.api

/**
 * Represents a location coordinate.
 */
data class TrackingLocation(
    val latitude: Double,
    val longitude: Double
)

/**
 * Represents a store/pickup point in a delivery.
 *
 * @param id Unique identifier for this store.
 * @param name Display name shown on the map marker.
 * @param location Store coordinates.
 * @param isPickedUp Whether the order has been picked up from this store.
 */
data class TrackingStore(
    val id: String,
    val name: String,
    val location: TrackingLocation,
    val isPickedUp: Boolean = false
)

/**
 * Represents an order to track on the map.
 *
 * @param orderId Unique order identifier.
 * @param stores List of stores where pickup happens (supports multi-stop).
 * @param destination Final delivery destination.
 * @param isArrived Whether the order has already been delivered.
 */
data class TrackingOrder(
    val orderId: String,
    val stores: List<TrackingStore>,
    val destination: TrackingLocation,
    val isArrived: Boolean = false
)

/**
 * Events emitted by the tracking map.
 */
sealed class TrackingEvent {
    /** Rider has picked up order from a store */
    data class StorePickedUp(val store: TrackingStore) : TrackingEvent()

    /** Rider has arrived at the destination */
    object OrderArrived : TrackingEvent()

    /** Rider has deviated from the route */
    data class RouteDeviation(val distanceMeters: Double) : TrackingEvent()

    /** Route is being recalculated */
    object Rerouting : TrackingEvent()

    /** Map has loaded */
    object MapLoaded : TrackingEvent()
}

/**
 * Interface for providing real-time tracking data.
 * Implement this to supply live rider location and order updates.
 * Not needed when using simulation mode.
 */
interface TrackingDataProvider {
    /** Provide the latest rider location. Return null if unavailable. */
    suspend fun getRiderLocation(orderId: String): TrackingLocation?

    /** Provide the latest order state (stores pickup status, arrival, etc.) */
    suspend fun getOrderUpdate(orderId: String): TrackingOrder?
}
