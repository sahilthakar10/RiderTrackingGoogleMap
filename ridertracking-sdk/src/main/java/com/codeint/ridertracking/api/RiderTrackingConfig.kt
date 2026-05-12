package com.codeint.ridertracking.api

/**
 * Configuration for the RiderTracking SDK.
 *
 * @param useSimulation When true, uses simulated rider movement for testing.
 *                      When false, provide live locations via [RiderTrackingMap]'s riderLocationUpdates parameter.
 * @param routesApiKey Google Routes API key for road-following route visualization.
 *                     Without this, routes will be straight lines between points.
 */
data class RiderTrackingConfig(
    val useSimulation: Boolean = true,
    val routesApiKey: String? = null
)
