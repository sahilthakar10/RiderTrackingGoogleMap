package com.codeint.ridertracking.internal.map

import kotlinx.serialization.Serializable

data class OrderResponseData(
    val batchOrderId: String,
    val isOrderArrived: Boolean,
    val stores: List<StoreLocation> = emptyList(),
    val multiStopDestination: LatLng? = null,
    val completedStops: Set<Int> = emptySet()
)

data class RouteInfo(
    val routePointsEncoded: List<LatLng>?,
    val routeSegments: List<RouteSegment> = emptyList(),
    val isMultiStop: Boolean = false,
    val totalStops: Int = 0
)

// API Response Models
@Serializable
data class RouteResponse(
    val success: Boolean,
    val data: RouteData? = null
)

@Serializable
data class RouteData(
    val routes: List<Route>
)

@Serializable
data class Route(
    val legs: List<PolyLine>,
    val polyline: EncodedPolyline
)

@Serializable
data class PolyLine(
    val polyline: EncodedPolyline
)

@Serializable
data class EncodedPolyline(
    val encodedPolyline: String
)

// Route Request Models
@Serializable
data class RouteRequest(
    val origin: Waypoint,
    val destination: Waypoint,
    val intermediates: List<Intermediates>? = null,
    val travelMode: String = "TWO_WHEELER",
    val routingPreference: String = "TRAFFIC_UNAWARE"
)

@Serializable
data class Intermediates(
    val via: Boolean = false,
    val vehicleStopover: Boolean = false,
    val location: Location
)

@Serializable
data class Waypoint(
    val location: Location
)

@Serializable
data class Location(
    val latLng: ApiLatLng
)

@Serializable
data class ApiLatLng(
    val latitude: Double,
    val longitude: Double
)

// Multi-stop delivery models
data class StoreLocation(
    val storeName: String,
    val location: LatLng,
    val isOrderPickedUp: Boolean
)

data class RouteSegment(
    val segmentId: String,
    val routePoints: List<LatLng>,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false
)

enum class SegmentType {
    STORE_TO_STORE,
    STORE_TO_DESTINATION,
    DIRECT_DELIVERY
}

// Simple location class replacing PCLocation
data class SimpleLocation(
    val latitude: Double?,
    val longitude: Double?
) {
    fun toLatLng(): LatLng? = if (latitude != null && longitude != null) {
        LatLng(latitude, longitude)
    } else {
        null
    }
}

// Response models replacing Pincode-specific ones
data class LocationResponse(
    val isOrderPickedUp: Boolean,
    val isOrderArrived: Boolean,
    val childOrderId: String? = null,
    val stores: List<StoreLocation> = emptyList(),
    val multiStopDestination: LatLng? = null
)

data class RiderLocationResponse(
    val location: SimpleLocation,
    val isOrderPickedUp: Boolean = false,
    val isOrderArrived: Boolean = false
)

data class StoreConfig(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
