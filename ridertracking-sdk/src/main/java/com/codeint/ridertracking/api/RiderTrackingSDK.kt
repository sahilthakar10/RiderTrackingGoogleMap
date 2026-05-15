package com.codeint.ridertracking.api

import android.util.Log

/**
 * Main entry point for the RiderTracking SDK.
 *
 * ```kotlin
 * RiderTrackingSDK.initialize(
 *     config = RiderTrackingConfig(
 *         useSimulation = true,
 *         logLevel = RiderTrackingLogLevel.DEBUG
 *     )
 * )
 * ```
 */
object RiderTrackingSDK {

    /** SDK version string */
    const val VERSION = "1.2.0"

    internal var config: RiderTrackingConfig = RiderTrackingConfig()
        private set

    internal var isInitialized: Boolean = false
        private set

    /**
     * Initialize the SDK. Call once at app startup (e.g., in Application.onCreate()).
     */
    fun initialize(config: RiderTrackingConfig) {
        this.config = config
        this.isInitialized = true
        log(RiderTrackingLogLevel.INFO, "RiderTrackingSDK v$VERSION initialized")
    }

    internal fun log(level: RiderTrackingLogLevel, message: String) {
        if (level.ordinal < config.logLevel.ordinal) return
        when (level) {
            RiderTrackingLogLevel.VERBOSE -> Log.v("RiderTrackingSDK", message)
            RiderTrackingLogLevel.DEBUG -> Log.d("RiderTrackingSDK", message)
            RiderTrackingLogLevel.INFO -> Log.i("RiderTrackingSDK", message)
            RiderTrackingLogLevel.WARN -> Log.w("RiderTrackingSDK", message)
            RiderTrackingLogLevel.ERROR -> Log.e("RiderTrackingSDK", message)
            RiderTrackingLogLevel.NONE -> {}
        }
    }
}

/**
 * Log levels for SDK debug output.
 *
 * Set via [RiderTrackingConfig.logLevel]. Default is [NONE] (silent).
 */
enum class RiderTrackingLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
}
