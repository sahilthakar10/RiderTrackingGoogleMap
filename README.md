# RiderTracking SDK

[![](https://jitpack.io/v/sahilthakar10/RiderTrackingGoogleMap.svg)](https://jitpack.io/#sahilthakar10/RiderTrackingGoogleMap)

Real-time rider tracking SDK for Android with Google Maps. Supports multi-stop deliveries, smooth rider animation, route visualization, and auto-rerouting.

![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green)
![Platform](https://img.shields.io/badge/Platform-Android-blue)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-purple)

## Features

- **Real-time rider tracking** on Google Maps with smooth animation
- **Multi-stop delivery** support with store pickup markers
- **Route visualization** - active path (dark blue), inactive path (light blue), visited path
- **Auto-rerouting** when rider deviates from the route
- **Two-phase routing** - pre-pickup (hidden route) and post-pickup (visible route)
- **Destination arrival circle** with animation
- **Simulation mode** for testing without real data
- **Google Routes API** integration for real route computation
- **Custom map styling** - clean, minimal map theme
- **Jetpack Compose** - drop-in composable, no XML needed

## Installation

### Step 1: Add JitPack repository

In your **`settings.gradle.kts`**:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add the dependency

In your **`app/build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.github.sahilthakar10:RiderTrackingGoogleMap:1.0.0")
}
```

### Step 3: Add Google Maps API key

In your **`AndroidManifest.xml`**:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY" />
```

## Quick Start

### 1. Initialize the SDK

In your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        RiderTrackingSDK.initialize(
            context = this,
            config = RiderTrackingConfig(
                routesApiKey = "YOUR_ROUTES_API_KEY", // Optional: for real route computation
                useSimulation = true                   // Set false for production
            )
        )
    }
}
```

### 2. Add the tracking map

In your Compose UI:

```kotlin
import com.codeint.ridertracking.api.*

@Composable
fun DeliveryScreen() {
    RiderTrackingMap(
        order = TrackingOrder(
            orderId = "order_123",
            stores = listOf(
                TrackingStore(
                    id = "store1",
                    name = "Pizza Palace",
                    location = TrackingLocation(12.9170, 77.6729)
                )
            ),
            destination = TrackingLocation(12.9254, 77.6696)
        ),
        modifier = Modifier.fillMaxSize(),
        onEvent = { event ->
            when (event) {
                is TrackingEvent.StorePickedUp -> { /* Order picked up */ }
                is TrackingEvent.OrderArrived -> { /* Delivery complete */ }
                is TrackingEvent.RouteDeviation -> { /* Rider deviated */ }
                is TrackingEvent.Rerouting -> { /* Recalculating route */ }
                is TrackingEvent.MapLoaded -> { /* Map is ready */ }
            }
        }
    )
}
```

## Live Rider Tracking

Push real-time rider locations from your backend:

```kotlin
val riderLocations = MutableSharedFlow<TrackingLocation>()

RiderTrackingMap(
    order = myOrder,
    riderLocationUpdates = riderLocations
)

// Push updates whenever you receive new rider coordinates
scope.launch {
    riderLocations.emit(TrackingLocation(12.918, 77.671))
}
```

When `riderLocationUpdates` is provided, it overrides the internal simulation. Order data, routes, and pickup status still work internally.

## Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `routesApiKey` | `String?` | `null` | Google Routes API key for real route computation |
| `useSimulation` | `Boolean` | `true` | Use simulated rider movement for testing |
| `riderSpeedKmh` | `Float` | `240` | Rider animation speed in km/h |
| `locationUpdateIntervalMs` | `Long` | `3000` | Location polling interval in milliseconds |

## Multi-Stop Delivery

The SDK supports multiple store pickups before final delivery:

```kotlin
TrackingOrder(
    orderId = "multi_stop_order",
    stores = listOf(
        TrackingStore("s1", "Pizza Palace", TrackingLocation(12.917, 77.672)),
        TrackingStore("s2", "Burger Barn", TrackingLocation(12.926, 77.669)),
        TrackingStore("s3", "Dessert Depot", TrackingLocation(12.927, 77.670))
    ),
    destination = TrackingLocation(12.925, 77.669)
)
```

## Requirements

- **Min SDK**: 24 (Android 7.0)
- **Google Maps SDK for Android** enabled
- **Google Routes API** enabled (optional, for real routes)

## Google Cloud Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Enable **Maps SDK for Android**
3. Enable **Routes API** (optional)
4. Create an API key and restrict it to your app's package + SHA-1

## License

```
Copyright 2026 CodeInt

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
