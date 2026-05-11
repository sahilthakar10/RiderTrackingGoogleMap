package com.codeint.ridertracking.api

import android.content.Context

/**
 * Main entry point for the RiderTracking SDK.
 *
 * Usage:
 * ```kotlin
 * // In Application.onCreate() or Activity
 * RiderTrackingSDK.initialize(
 *     context = applicationContext,
 *     config = RiderTrackingConfig(
 *         routesApiKey = "YOUR_ROUTES_API_KEY",
 *         useSimulation = false
 *     )
 * )
 * ```
 *
 * Then in Compose:
 * ```kotlin
 * RiderTrackingMap(
 *     order = TrackingOrder(
 *         orderId = "123",
 *         stores = listOf(TrackingStore("s1", "Pizza Palace", TrackingLocation(12.91, 77.67))),
 *         destination = TrackingLocation(12.92, 77.66)
 *     ),
 *     onEvent = { event -> /* handle events */ }
 * )
 * ```
 */
object RiderTrackingSDK {

    internal var config: RiderTrackingConfig = RiderTrackingConfig()
        private set

    internal var isInitialized: Boolean = false
        private set

    internal var dataProvider: TrackingDataProvider? = null
        private set

    /**
     * Initialize the SDK. Call once at app startup.
     *
     * @param context Application context.
     * @param config SDK configuration.
     * @param dataProvider Optional provider for real-time tracking data.
     *                     Required when [RiderTrackingConfig.useSimulation] is false.
     */
    fun initialize(
        context: Context,
        config: RiderTrackingConfig,
        dataProvider: TrackingDataProvider? = null
    ) {
        this.config = config
        this.dataProvider = dataProvider
        this.isInitialized = true
    }

    /**
     * Check if SDK has been initialized.
     */
    fun checkInitialized() {
        check(isInitialized) {
            "RiderTrackingSDK not initialized. Call RiderTrackingSDK.initialize() first."
        }
    }
}
