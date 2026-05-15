# Contributing to RiderTracking SDK

Thanks for your interest in contributing! Here's how to get started.

## Reporting Issues

- Use [GitHub Issues](https://github.com/sahilthakar10/RiderTrackingGoogleMap/issues) for bug reports and feature requests
- Use the provided issue templates
- Include SDK version, Android version, and steps to reproduce

## Development Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/sahilthakar10/RiderTrackingGoogleMap.git
   ```

2. Add your Google Maps API key to `local.properties`:
   ```properties
   MAPS_API_KEY=your_key_here
   ROUTES_API_KEY=your_key_here
   ```

3. Open in Android Studio and sync Gradle

4. Run the sample app: select `sample-app` run configuration

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Public API classes must have KDoc with usage examples
- Internal classes should have class-level KDoc
- Use `internal` visibility for non-public SDK classes

## Pull Requests

1. Fork the repo and create a feature branch from `main`
2. Write/update tests for your changes
3. Ensure all tests pass: `./gradlew :ridertracking-sdk:test`
4. Ensure the build succeeds: `./gradlew assembleDebug`
5. Submit a PR with a clear description of the change

## Project Structure

```
├── ridertracking-sdk/     # The SDK library
│   ├── api/               # Public API (consumers use this)
│   └── internal/          # Implementation (hidden from consumers)
├── app/                   # Demo app
├── sample-app/            # Sample app showing SDK usage
```

## Versioning

We use [Semantic Versioning](https://semver.org/). Update `CHANGELOG.md` with your changes.
