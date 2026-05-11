package com.codeint.ridertracking.api

/**
 * Configuration for the RiderTracking SDK.
 *
 * @param routesApiKey Google Routes API key for real route computation.
 *                     If null, fallback interpolated routes will be used.
 * @param useSimulation When true, uses simulated rider movement for testing.
 *                      When false, expects real location data via [TrackingDataProvider].
 * @param riderSpeedKmh Animation speed for the rider icon in km/h. Default 240 (fast for demo).
 * @param locationUpdateIntervalMs How often to poll for rider location updates. Default 3000ms.
 */
data class RiderTrackingConfig(
    val routesApiKey: String? = null,
    val useSimulation: Boolean = true,
    val riderSpeedKmh: Float = 240f,
    val locationUpdateIntervalMs: Long = 3000L
)
