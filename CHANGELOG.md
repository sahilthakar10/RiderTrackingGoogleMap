# Changelog

All notable changes to the RiderTracking SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-05-12

### Added
- `RiderTrackingAppearance` - full visual customization (colors, icons, labels, map style)
- Slot-based composable customization (`loadingContent`, `reroutingContent`, `mapOverlayContent`)
- `TrackingUiState` exposed to consumer overlays with reactive tracking data
- `TrackingPhase` enum (PRE_PICKUP / POST_PICKUP)
- Google Routes API integration for real road-following routes
- `riderLocationUpdates` parameter for live rider location via `Flow<TrackingLocation>`
- `sample-app` module demonstrating SDK usage
- Flexible map sizing - consumers control size via modifier
- 117 unit tests

### Changed
- Smooth 60 FPS rider animation with time-based interpolation and easing
- Map style uses proper color contrast and road differentiation
- `MapProperties` and `MapUiSettings` remembered to prevent flickering
- Route pruning uses single source of truth (`remainingRoutePoints`)

### Fixed
- ViewModel lifecycle leak - uses manual `CoroutineScope` with `destroy()`
- Atomic state updates via `MutableStateFlow.update{}`
- `CancellationException` properly rethrown
- Thread-safe `RouteCache` with `ConcurrentHashMap`
- Removed `GestureHandlingOverlay` blocking native map gestures
- Gap between rider icon and route polyline

### Removed
- `animateRemainingRoutePoints` (was causing route misalignment)
- Unused `dataProvider`, `TrackingDataProvider` interface
- Hardcoded store configurations in simulation

## [1.1.0] - 2026-05-11

### Added
- SDK module (`ridertracking-sdk`) with clean public API
- `RiderTrackingSDK.initialize()` entry point
- `RiderTrackingMap()` composable
- `TrackingOrder`, `TrackingStore`, `TrackingLocation` models
- `TrackingEvent` sealed class for event callbacks
- Maven publishing via JitPack
- Koin DI setup in demo app

### Changed
- Package name from `com.example` to `com.codeint`

## [1.0.0] - 2026-05-11

### Added
- Initial release
- Real-time rider tracking on Google Maps
- Multi-stop delivery support
- Rider animation with speed control
- Route pruning and deviation detection
- Two-phase routing (pre-pickup / post-pickup)
- Destination arrival circle
- Simulation mode for testing
- Polyline decoder for Google encoded polylines
- Custom map styling
