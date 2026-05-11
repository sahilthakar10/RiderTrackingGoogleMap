package com.codeint.ridertrackinggooglemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.codeint.ridertracking.api.RiderTrackingMap
import com.codeint.ridertracking.api.TrackingLocation
import com.codeint.ridertracking.api.TrackingOrder
import com.codeint.ridertracking.api.TrackingStore
import com.codeint.ridertrackinggooglemap.ui.theme.RiderTrackingGoogleMapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RiderTrackingGoogleMapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RiderTrackingMap(
                        order = TrackingOrder(
                            orderId = "order_001",
                            stores = listOf(
                                TrackingStore(
                                    id = "store1",
                                    name = "Pizza Palace",
                                    location = TrackingLocation(12.9170491, 77.6729254)
                                )
                            ),
                            destination = TrackingLocation(12.925499916077, 77.66960144043)
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onEvent = { event ->
                            // Handle tracking events
                        }
                    )
                }
            }
        }
    }
}
