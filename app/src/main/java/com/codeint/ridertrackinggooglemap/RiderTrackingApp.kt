package com.codeint.ridertrackinggooglemap

import android.app.Application
import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.api.RiderTrackingSDK

class RiderTrackingApp : Application() {

    override fun onCreate() {
        super.onCreate()

        RiderTrackingSDK.initialize(
            config = RiderTrackingConfig(
                useSimulation = true,
                routesApiKey = BuildConfig.ROUTES_API_KEY
            )
        )
    }
}
