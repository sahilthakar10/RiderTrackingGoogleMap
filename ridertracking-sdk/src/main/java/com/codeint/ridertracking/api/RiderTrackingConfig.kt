package com.codeint.ridertracking.api

/**
 * Configuration for the RiderTracking SDK.
 *
 * @param useSimulation When true, uses simulated rider movement for testing.
 *                      When false, provide live locations via [RiderTrackingMap]'s riderLocationUpdates parameter.
 */
data class RiderTrackingConfig(
    val useSimulation: Boolean = true
)
