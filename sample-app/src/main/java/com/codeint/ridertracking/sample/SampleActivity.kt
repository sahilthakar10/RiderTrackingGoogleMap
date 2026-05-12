package com.codeint.ridertracking.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeint.ridertracking.api.RiderTrackingMap
import com.codeint.ridertracking.api.TrackingEvent
import com.codeint.ridertracking.api.TrackingLocation
import com.codeint.ridertracking.api.TrackingOrder
import com.codeint.ridertracking.api.TrackingStore

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SampleScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SampleScreen(modifier: Modifier = Modifier) {
    var selectedScenario by remember { mutableIntStateOf(0) }

    val scenarios = listOf(
        SampleScenario(
            name = "Single Store",
            order = TrackingOrder(
                orderId = "single_store_001",
                stores = listOf(
                    TrackingStore("s1", "Pizza Palace", TrackingLocation(12.9170491, 77.6729254))
                ),
                destination = TrackingLocation(12.925499916077, 77.66960144043)
            )
        )
    )

    Column(modifier = modifier.fillMaxSize()) {
        // Scenario selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scenario:", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            scenarios.forEachIndexed { index, scenario ->
                Button(
                    onClick = { selectedScenario = index },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedScenario == index) Color(0xFF1A73E8) else Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(scenario.name, fontSize = 12.sp)
                }
            }
        }

        // Map
        Box(modifier = Modifier.fillMaxSize()) {
            RiderTrackingMap(
                order = scenarios[selectedScenario].order,
                modifier = Modifier.fillMaxSize(),
                onEvent = { event ->
                    when (event) {
                        is TrackingEvent.StorePickedUp -> Log.d("Sample", "Picked up from: ${event.store.name}")
                        is TrackingEvent.OrderArrived -> Log.d("Sample", "Order arrived!")
                        is TrackingEvent.RouteDeviation -> Log.d("Sample", "Deviated: ${event.distanceMeters}m")
                        is TrackingEvent.Rerouting -> Log.d("Sample", "Rerouting...")
                        is TrackingEvent.MapLoaded -> Log.d("Sample", "Map loaded")
                    }
                }
            )

            // Info overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        "RiderTracking SDK Sample",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "${scenarios[selectedScenario].order.stores.size} store(s) - Simulation mode",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

data class SampleScenario(
    val name: String,
    val order: TrackingOrder
)
