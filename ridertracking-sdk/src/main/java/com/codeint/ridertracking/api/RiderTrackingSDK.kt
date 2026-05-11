package com.codeint.ridertracking.api

/**
 * Main entry point for the RiderTracking SDK.
 *
 * Usage:
 * ```kotlin
 * RiderTrackingSDK.initialize(
 *     config = RiderTrackingConfig(useSimulation = true)
 * )
 * ```
 */
object RiderTrackingSDK {

    internal var config: RiderTrackingConfig = RiderTrackingConfig()
        private set

    internal var isInitialized: Boolean = false
        private set

    /**
     * Initialize the SDK. Call once at app startup.
     */
    fun initialize(config: RiderTrackingConfig) {
        this.config = config
        this.isInitialized = true
    }
}
