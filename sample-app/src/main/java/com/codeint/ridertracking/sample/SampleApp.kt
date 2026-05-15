package com.codeint.ridertracking.sample

import android.app.Application
import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.api.RiderTrackingSDK

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RiderTrackingSDK.initialize(
            config = RiderTrackingConfig(
                useSimulation = true,
                routesApiKey = "" // Add your Google Routes API key here
            )
        )
    }
}
