package com.codeint.ridertracking.internal.map.network

import com.codeint.ridertracking.internal.map.RouteRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service interface for Google Routes API.
 * Base URL: https://routes.googleapis.com/
 */
interface RouteApiService {

    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "routes.legs.polyline.encodedPolyline,routes.polyline.encodedPolyline",
        @Body request: RouteRequest
    ): RoutesApiResponse
}

/**
 * Direct response from Google Routes API (different structure from our internal RouteResponse)
 */
data class RoutesApiResponse(
    val routes: List<RoutesApiRoute>? = null
)

data class RoutesApiRoute(
    val legs: List<RoutesApiLeg>? = null,
    val polyline: RoutesApiPolyline? = null
)

data class RoutesApiLeg(
    val polyline: RoutesApiPolyline? = null
)

data class RoutesApiPolyline(
    val encodedPolyline: String? = null
)
