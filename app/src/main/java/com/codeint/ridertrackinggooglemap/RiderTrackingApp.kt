package com.codeint.ridertrackinggooglemap

import android.app.Application
import com.codeint.ridertrackinggooglemap.BuildConfig
import com.codeint.ridertracking.api.RiderTrackingConfig
import com.codeint.ridertracking.api.RiderTrackingSDK

class RiderTrackingApp : Application() {

    override fun onCreate() {
        super.onCreate()

        RiderTrackingSDK.initialize(
            context = this,
            config = RiderTrackingConfig(
                routesApiKey = BuildConfig.ROUTES_API_KEY,
                useSimulation = true
            )
        )
    }
}
