package com.codeint.ridertracking.api

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class RiderTrackingSDKTest {

    @Before
    fun setUp() {
        val field: Field = RiderTrackingSDK::class.java.getDeclaredField("isInitialized")
        field.isAccessible = true
        field.setBoolean(RiderTrackingSDK, false)
    }

    @Test
    fun `isInitialized - before init - is false`() {
        assertFalse(RiderTrackingSDK.isInitialized)
    }

    @Test
    fun `initialize - sets config and isInitialized`() {
        val config = RiderTrackingConfig(useSimulation = true)
        RiderTrackingSDK.initialize(config = config)
        assertTrue(RiderTrackingSDK.isInitialized)
        assertTrue(RiderTrackingSDK.config.useSimulation)
    }
}
