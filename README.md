# RiderTracking SDK

[![](https://jitpack.io/v/sahilthakar10/RiderTrackingGoogleMap.svg)](https://jitpack.io/#sahilthakar10/RiderTrackingGoogleMap)
[![CI](https://github.com/sahilthakar10/RiderTrackingGoogleMap/actions/workflows/ci.yml/badge.svg)](https://github.com/sahilthakar10/RiderTrackingGoogleMap/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green)
![Platform](https://img.shields.io/badge/Platform-Android-blue)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Ready-purple)

Real-time rider tracking SDK for Android with Google Maps. Drop-in Jetpack Compose component with smooth 60 FPS animation, multi-stop delivery support, and full visual customization.

## Demo

<video src="https://github.com/sahilthakar10/RiderTrackingGoogleMap/raw/main/ridertracking_demo.mp4" width="300" controls></video>

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Live Rider Tracking](#live-rider-tracking)
- [Customization](#customization)
- [Configuration](#configuration)
- [Multi-Stop Delivery](#multi-stop-delivery)
- [Version Compatibility](#version-compatibility)
- [Google Cloud Setup](#google-cloud-setup)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Real-time rider tracking** on Google Maps with smooth 60 FPS animation
- **Multi-stop delivery** support with store pickup markers
- **Route visualization** - active path, inactive path, visited path with road-following routes
- **Auto-rerouting** when rider deviates from the route
- **Two-phase routing** - pre-pickup (hidden route) and post-pickup (visible route)
- **Destination arrival circle** with animation
- **Simulation mode** for testing without real data
- **Google Routes API** integration for real road-following routes
- **Full visual customization** - colors, icons, labels, map style, custom composables
- **Slot-based composables** - custom loading, rerouting, and map overlay UIs
- **Flexible sizing** - works in full screen, cards, split views
- **Jetpack Compose** - single composable, no XML

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
    implementation("com.github.sahilthakar10:RiderTrackingGoogleMap:1.2.0")
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

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RiderTrackingSDK.initialize(
            config = RiderTrackingConfig(
                useSimulation = true,
                routesApiKey = "YOUR_ROUTES_API_KEY",
                logLevel = RiderTrackingLogLevel.DEBUG // for development
            )
        )
    }
}
```

### 2. Add the tracking map

```kotlin
import com.codeint.ridertracking.api.*

@Composable
fun DeliveryScreen() {
    RiderTrackingMap(
        order = TrackingOrder(
            orderId = "order_123",
            stores = listOf(
                TrackingStore("s1", "Pizza Palace", TrackingLocation(12.9170, 77.6729))
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

## Customization

### Visual Appearance

```kotlin
RiderTrackingMap(
    order = myOrder,
    appearance = RiderTrackingAppearance(
        // Route colors
        activeRouteColor = Color(0xFF4CAF50),
        inactiveRouteColor = Color(0xFFA5D6A7),
        routeWidth = 12f,

        // Custom icons
        riderIcon = R.drawable.my_bike_icon,
        riderIconSize = 72,
        storeIcon = R.drawable.my_store_icon,
        destinationIcon = R.drawable.my_pin_icon,

        // Store labels
        storeLabelBackground = Color(0xFF212121),
        storeLabelTextColor = Color.Yellow,
        showStoreLabels = true,

        // Map style
        mapStyleJson = myCustomStyleJson
    )
)
```

### Custom Composables

```kotlin
RiderTrackingMap(
    order = myOrder,
    appearance = RiderTrackingAppearance(
        // Custom loading screen
        loadingContent = { ShimmerPlaceholder() },

        // Custom rerouting overlay
        reroutingContent = { MyReroutingDialog() },

        // Overlay on the map (ETA card, info panel, etc.)
        mapOverlayContent = { trackingState ->
            Column(Modifier.padding(16.dp)) {
                Text("${trackingState.pendingStoreCount} stops remaining")
                if (trackingState.isOrderArrived) Text("Delivered!")
            }
        }
    )
)
```

### Flexible Sizing

```kotlin
// Full screen
RiderTrackingMap(order = myOrder, modifier = Modifier.fillMaxSize())

// Fixed height card
RiderTrackingMap(order = myOrder, modifier = Modifier.fillMaxWidth().height(300.dp))

// Half screen
RiderTrackingMap(order = myOrder, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f))
```

## Configuration

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `useSimulation` | `Boolean` | `true` | Simulated rider movement for testing |
| `routesApiKey` | `String?` | `null` | Google Routes API key for road-following routes |
| `logLevel` | `RiderTrackingLogLevel` | `NONE` | SDK log verbosity (VERBOSE, DEBUG, INFO, WARN, ERROR, NONE) |

## Multi-Stop Delivery

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

## Version Compatibility

| SDK Version | Min Android | Compose BOM | Maps Compose | Kotlin |
|-------------|-------------|-------------|--------------|--------|
| 1.2.0 | API 24 (7.0) | 2026.02.01 | 4.3.3 | 2.2.10 |
| 1.1.0 | API 24 (7.0) | 2026.02.01 | 4.3.3 | 2.2.10 |

## Google Cloud Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Enable **Maps SDK for Android**
3. Enable **Routes API** (optional, for road-following routes)
4. Create an API key
5. **Restrict the key** to your app's package name + SHA-1 fingerprint

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Map shows blank/gray | Enable "Maps SDK for Android" in Google Cloud Console |
| Routes are straight lines | Provide `routesApiKey` in config and enable "Routes API" |
| Map style flickers on zoom | Update to SDK 1.2.0+ (fixed) |
| Rider not moving | Check that simulation is enabled or `riderLocationUpdates` is emitting |
| App crashes on launch | Call `RiderTrackingSDK.initialize()` in `Application.onCreate()` before using `RiderTrackingMap` |
| API key not working | Check key restrictions in Cloud Console - must allow your package + SHA-1 |

## SDK Version

```kotlin
// Check SDK version at runtime
val version = RiderTrackingSDK.VERSION // "1.2.0"
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history.

## License

```
Copyright 2026 CodeInt

Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for the full text.
