package com.codeint.ridertracking.internal.map.network

import android.util.Log
import com.codeint.ridertracking.internal.map.SdkConfig
import com.codeint.ridertracking.internal.map.EncodedPolyline
import com.codeint.ridertracking.internal.map.PolyLine
import com.codeint.ridertracking.internal.map.Route
import com.codeint.ridertracking.internal.map.RouteData
import com.codeint.ridertracking.internal.map.RouteRequest
import com.codeint.ridertracking.internal.map.RouteResponse

/**
 * Interface for Google Maps network operations.
 */
interface GoogleMapNetworkRepository {
    suspend fun getMapRoute(routeRequest: RouteRequest): Result<RouteResponse>
}

/**
 * Real implementation using Retrofit + Google Routes API
 */
class GoogleMapNetworkRepositoryImpl(
    private val routeApiService: RouteApiService
) : GoogleMapNetworkRepository {

    override suspend fun getMapRoute(routeRequest: RouteRequest): Result<RouteResponse> {
        return try {
            val apiResponse = routeApiService.computeRoutes(
                apiKey = SdkConfig.ROUTES_API_KEY,
                request = routeRequest
            )

            // Convert Google Routes API response to our internal RouteResponse format
            val routes = apiResponse.routes?.map { apiRoute ->
                val legs = apiRoute.legs?.mapNotNull { apiLeg ->
                    apiLeg.polyline?.encodedPolyline?.let { encoded ->
                        PolyLine(polyline = EncodedPolyline(encodedPolyline = encoded))
                    }
                } ?: emptyList()

                val overallPolyline = apiRoute.polyline?.encodedPolyline?.let {
                    EncodedPolyline(encodedPolyline = it)
                } ?: EncodedPolyline(encodedPolyline = "")

                Route(legs = legs, polyline = overallPolyline)
            } ?: emptyList()

            if (routes.isEmpty()) {
                Log.w("NetworkRepo", "Routes API returned empty routes")
                Result.failure(Exception("No routes found"))
            } else {
                Log.d("NetworkRepo", "Routes API returned ${routes.size} routes with ${routes.first().legs.size} legs")
                Result.success(
                    RouteResponse(
                        success = true,
                        data = RouteData(routes = routes)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("NetworkRepo", "Routes API call failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
