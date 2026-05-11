package com.codeint.ridertracking.internal.map

import com.codeint.ridertracking.api.RiderTrackingSDK

/**
 * Internal config accessor - reads from SDK initialization.
 * Replaces hardcoded BuildConfig references.
 */
internal object SdkConfig {
    val ROUTES_API_KEY: String
        get() = RiderTrackingSDK.config.routesApiKey ?: ""
}
