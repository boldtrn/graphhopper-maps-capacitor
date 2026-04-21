# CLAUDE.md

## Project Overview

Capacitor-based Android app wrapping **GraphHopper Maps** (React/OpenLayers web app) with a native **turn-by-turn navigation** screen built on MapLibre GL and the MapLibre Navigation SDK.

## Architecture

```
src/app.js                          -- Capacitor bridge: wires native APIs (TTS, clipboard, file, navigation)
                                       into window globals, then loads graphhopper-maps bundle
graphhopper-maps/                   -- git submodule (graphhopper/graphhopper-maps), React/OpenLayers web app
capacitor-maplibre-navigation/      -- Capacitor plugin: TypeScript definitions + Android Kotlin implementation
android/                            -- Main Android app shell (Capacitor host)
```

### Flow: Web -> Native Navigation

1. `graphhopper-maps` UI calls `window.ghNativeNavigation.start(url, body, onClose, miles)`
2. `src/app.js` bridges this to `MapLibreNavigation.startNavigation(...)` (Capacitor plugin call)
3. `MapLibreNavigationPlugin.kt` receives the call, launches `NavigationActivity`
4. On close, broadcast -> plugin -> JS `navigationClosed` event -> `onClose` callback

## capacitor-maplibre-navigation Plugin

### TypeScript API (`src/definitions.ts`)

```typescript
interface StartNavigationOptions {
  navigateUrl: string        // GraphHopper /navigate endpoint URL with API key
  requestBody: string        // JSON POST body
  showDistanceInMiles?: boolean
}
interface MapLibreNavigationPlugin {
  startNavigation(options): Promise<void>
  stopNavigation(): Promise<void>
  addListener('navigationClosed', () => void): Promise<PluginListenerHandle>
  removeAllListeners(): Promise<void>
}
```

### Android Source Files (`android/src/main/java/com/graphhopper/navigationplugin/`)

| File | Purpose |
|------|---------|
| `MapLibreNavigationPlugin.kt` | Capacitor plugin bridge: receives JS calls, launches/stops NavigationActivity via intents and broadcasts |
| `NavigationActivity.kt` | Main navigation screen: MapView + Compose overlay, GPS tracking, route display, voice instructions, off-route rerouting |
| `NavigationOverlay.kt` | Jetpack Compose UI: instruction bar, speed panel, speed limit sign, ETA bar, recenter/mute/stop buttons |
| `MapRouteArrow.kt` | Draws upcoming-maneuver arrow on map (shaft line + arrowhead icon). Kotlin port from maplibre-navigation-android |
| `GraphHopperRouteFetcher.kt` | Fetches routes from GraphHopper /navigate endpoint. Handles initial fetch and rerouting with waypoint skipping |
| `Converters.kt` | Formatting utilities: distance, duration, speed, time |

### Key Implementation Details

- **Route layer ordering**: Route is drawn below arrow layers (checked via `arrowCasingLayerId`), arrow below puck shadow
- **Voice**: `NavigationSpeechPlayer` from navigation-ui-android handles TTS; custom `SpeechAnnouncement.builder().announcement(text).build()` for ad-hoc announcements
- **FAKE_GPS**: `NavigationActivity.FAKE_GPS` constant can be toggled for simulation (but Lockito is preferred)

## graphhopper-maps Submodule

- **Repo**: `git@github.com:graphhopper/graphhopper-maps.git`
- **Purpose**: React/TypeScript web mapping app with routing, search, and custom model support built on OpenLayers
- **Navigation interface** (`src/NativeNavigation.ts`): `{ start(url, body, onClose, miles?), stop() }` -- implemented by the Capacitor bridge in `src/app.js`
- **Navigation trigger**: `src/sidebar/RoutingResults.tsx` reads `window.ghNativeNavigation` and offers a "Navigate" button when available

## Dependencies & Versions

| Dependency | Version | Notes |
|------------|---------|-------|
| MapLibre GL Native | 12.3.0 | Excludes old geojson/turf modules |
| MapLibre Navigation Core | 5.0.0-pre12 | KMP version, provides geojson-jvm & turf-jvm 7.0.0-pre0 |
| MapLibre Navigation UI Android | 5.0.0-pre12 | Voice (SpeechPlayer), SpeechAnnouncement |
| Capacitor Core | ^8.0.0 | |
| Jetpack Compose BOM | 2025.01.01 | |
| Android compileSdk / minSdk | 36 / 24 | Java/Kotlin target: 17 |

### Building locally with navigation SDK changes

```bash
# In maplibre-navigation-android repo:
VERSION=5.1.0-SNAPSHOT ./gradlew :maplibre-navigation-core:publishToMavenLocal :libandroid-navigation-ui:publishToMavenLocal
# Then add mavenLocal() in android/build.gradle allprojects repositories
```

## MapLibre Navigation SDK Notes

- The SDK is a **KMP rewrite** (5.x) with very little documentation
- **Primary reference**: https://github.com/maplibre/maplibre-navigation-android (check `sample/android` for usage examples)
- **Do NOT use Mapbox APIs or documentation** -- the package names still contain `mapbox` in some legacy classes (e.g. `mapbox-location-shadow-layer`, `SpeechAnnouncement` from `com.mapbox.services`) but these are all MapLibre forks
- Key classes with `mapbox` legacy naming:
  - Layer IDs: `mapbox-location-shadow-layer`, `mapbox-navigation-arrow-*`
  - `SpeechAnnouncement` (AutoValue, builder with `.announcement(String)` and `.ssmlAnnouncement(String)`)
  - `VoiceInstructionMilestone`
- `snapToRoute` is disabled for now (to fix it see https://github.com/maplibre/maplibre-navigation-android/issues/67)

## Git Structure

- **Main branch**: `main`
- **Current feature branch**: `navigation-plugin`
- **Submodule**: `graphhopper-maps` (clone with `--recursive`)
