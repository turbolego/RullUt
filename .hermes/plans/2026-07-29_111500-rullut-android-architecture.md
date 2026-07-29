# RullUt — Native Android Architecture Plan

> **For Hermes:** Implement task-by-task using delegate_task for parallel work where safe.
> Order is sequential within each phase, phases are sequential.

**Goal:** Port the Expo/MapLibre-RN accessibility routing app to a native Android app (Kotlin, Jetpack Compose, MapLibre Native) with zero-billing, zero-account requirements, publishable on Google Play Store.

**Architecture:** Single-module Android app using MapLibre Native for map rendering, custom WMSXYZTileProvider for the Geonorge WMS layer, OpenFreeMap for basemap, shared HTTP logic for GetFeatureInfo/routing/search ported from the Expo app. Manual accessibility layer via Android View/Compose semantics. MapLibre Native renders through standard Android Views (SurfaceView) — we subclass and add `contentDescription`, `AccessibilityDelegate`, and `announceForAccessibility()` hooks.

**Tech Stack:**
- Kotlin 2.x + Jetpack Compose (Material 3)
- MapLibre Native Android SDK (`org.maplibre.gl:android-sdk`) — BSD license, Maven Central, fully open
- OpenFreeMap basemap (Docker/swarm tiles from `tiles.openfreemap.org` — no key, no registration)
- Geonorge WMS (`wms.geonorge.no/skwms1/wms.tilgjengelighet3`) — public, no auth
- Min SDK 28, Target SDK 35
- Gradle Kotlin DSL
- Kotlin coroutines + OkHttp for all HTTP (GetFeatureInfo, GoTo, Overpass, Valhalla, Places search, WFS)
- AndroidX: Navigation Compose, DataStore (prefs), Lifecycle

**Data flow:** User taps map → OnMapClickListener returns lat/lon → produce WMS GetFeatureInfo URL via `FeatureInfoQueryBuilder` → OkHTTP → GML/plain-text parser → `FeatureInfo` model → compose state → in-app UI

---

## Pre-Phase: Android project creation — no plan, raw Android Studio/scaffold

**Step 1: Create the canonical project**
- `com.turbolego.rullut`, min SDK 24, Kotlin, Compose enabled, Gradle KTS
- git init already done; add .gitignore via Android Studio defaults, then add files step-by-step

---

## Phase 1: MapCore — WMS layer + OpenFreeMap basemap

### Task 1: Add MapLibre Native dependency + empty MapView screen

**Files:**
- Create: `.gitignore` entries (for Android — local.properties, .idea, build/, *.apk)
- Modify: `app/build.gradle.kts` — add `org.maplibre.gl:android-sdk:11.8.0`
- Create: `app/src/main/java/com/turbolego/rullut/MainActivity.kt`
- Create: `app/src/main/java/com/turbolego/rullut/ui/MapScreen.kt`

**Steps:**
1. Add `implementation("org.maplibre.gl:android-sdk:11.8.0")` to dependencies
2. `MainActivity`: setContent { MaterialTheme { MapScreen() } }
3. `MapScreen`: AndroidView wrapping `com.maplibre.gl.MapView`
4. ComposeMapView composable: use `AndroidView(factory = { MapView(context) })`
5. Verify: app launches with empty screen (no map yet)

### Task 2: MapLibre initialization + OpenFreeMap basemap style

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` — add API key meta-data (any text, MapLibre reads but doesn't reject)
- Modify: `app/src/main/java/com/turbolego/rullut/ui/MapScreen.kt`

**Steps:**
1. Call `MapLibre.getInstance(context)` before inflating MapView (in Activity or Application class)
2. In `MapScreen`: set style to `https://tiles.openfreemap.org/styles/liberty` (same as Expo)
3. Set initial camera position: `LatLng(65.0, 15.5)`, zoom 5.0 (Norway center)
4. Verify: map renders with OpenFreeMap Liberty tiles

### Task 3: WMS tile source — built from x/y/z → EPSG:3857 bbox math

**Files:**
- Create: `app/src/main/java/com/turbomach/tilgjengelighet/map/WmsRasterSource.kt` (new tile URL builder utility)
- Modify: `app/src/main/java/com/turbomach/tilgjengelighet/ui/MapScreen.kt`

**Steps:**
1. Rewrite EPSG:3857 tile bounding box computation:
   ```kotlin
   fun tileXYToBBox(x: Int, y: Int, z: Int): DoubleArray {
       val tileSizeMeters = MAP_SIZE_METERS / (1 shl z) // MAP_SIZE = 2003750.342789244 * 2
       val minX = TILE_ORIGIN_X + x * tileSizeMeters
       val maxX = minX + tileSizeMeters
       val maxY = TILE_ORIGIN_Y - y * tileSizeMeters
       val minY = maxY - tileSizeMeters
       return doubleArrayOf(minX, minY, maxX, maxY)
   }
   ```
2. Construct WMS GetMap URL: base URL + `&BBOX=minX,minY,maxX,maxY&sw=EPSG:3857&width=256&height=256`
3. Register as Raster source in Maplibre style: `RasterSource(id, TileSet("tileset", listOf(wms_base_url)))`
   The actual bbox expansion happens inside a custom tile URL transform (`UrlTileProvider` equivalent).
   Alternate approach: directly for each tile, request MapLibre to render the tileURL — use a `ImageSource` per tile, but this is too fine-grained. Better: use a pattern that replaces `{z}/{x}/{y}` with `GetMap` URL every tile → intercept tile load via custom `HttpRequestUtil.setLogFunction` + requestInterceptor.
   Fastest path: define a `raster` source with `tiles` array:
   ```json
   "tiles": ["https://wms.geonorge.no/skwms1/wms.g2?service=WMS&…&WIDTH=256&HEIGHT=256&BBOX={bbx-epsg-3857}"]
   ```
   But **MapLibre Native Android does NOT support `{bbox-epsg-3857}`**, only `{z}`, `{x}`, `{y}`.
   So the actual approach: derive bbox in Kotlin and for each tile URL request, intercept before the map renders.
   **Implementation:** Override the `TileServer` approach: create a local right tile server that intercepts tile requests? No.

   **Correct approach: every tile load goes through MapLibre's `http` request. We intercept every WMS tile URL and rewrite the bbox.**
   MapLibre-native `HttpRequestUtil.setLogRequestFunction` accepts a request rewrite callback. Intercept requests for `wile.geonorge.no` patterns, extract `{z}/{x}/{y}` from the request path, recompute bbox, build the correct WMS GetMap URL, and replace the request URL before it hits the network.

   **Implementation:**
   - Create `WmsRequestInterceptor.kt`: `HttpRequest.setLogRequest(function(request) { … })`
   - Parse the interim URL (e.g., `https://my-air/tiles/{z}/{x}/{y}`)
   - Extract x,y,z from it
   - Compute bbox
   - return new request URL with proper WMS GetMap URL
   - Set tile source to a dummy URL pattern: `https://localhost/tiles/{z}/{x}/{y}`

4. Add this source as a raster layer to the style

### Task 4: Layer switch / Enable composite

**Files:**
- Create: `app/src/main/java/com/turbomach/tilgjengelighet/ui/LayerControls.kt`
- Modify: `MapScreen.kt` — add floating buttons over the map

**Steps:**
1. Import activeLayers model state (MutableStateFlow>)
2. When toggled, add/remove WMS layer from style
3. GET GetCapabilities on start for layer listing (for later UI)

---

## Phase 2: GetFeatureInfo (tap on map → show properties)

### Task 5: FeatureInfo query on map tap
**Files:**
- Create: `app/src/main/java/com/turbomach/tilgjengelighet/api/FeatureInfoApi.kt`
- Modify: `MapScreen.kt`

**Steps:**
1. Register `map.addOnMapClickListener`
2. Convert tap lat/lon → EPSG:3857 bbox (100m around at zoom)
3. Build GetFeatureInfo URL (same as Expo's `buildFeatureInfoUrl`)
4. Fetch via OkHttp, parse `text/plain` output (own parsing engine)
   ```kotlin
   data class FeatureInfo(
       val layerName: String,
       val featureId: String,
       val props: Map<String,String>
   )
   ```
5. Display in bottom sheet / popup

---

## Phase 3: Routing

### Task 6: Routing pipeline (WFS Dijkstra → Overpass → Valhalla)

**Files:**
- Port from `packages/shared` GML/Dijkstra logic (TypeScript → Kotlin)
- Create: `RouteEngine.kt`, `OsmRouteApi.kt`, `ValhallaRouteApi.kt`, `AccessibilityAssessment.kt`

**Steps:**
Same tiered approach as Expo. For the local graph, the Expo app bundles `graph-data.json` (Geonorge WFS TettstadVej+FriluftTurvej). Store as JSON asset in `app/src/main/assets/graph-bundle.json`.

1. WFS Dijkstra: load graph from assets JSON → Kotlin's Dijkstra → `GeoJson` output → => MapLibre GeoJSON source
2. OSM Overpass fallback: OkHttp POST with Overpass QL query (same as Expo)
3. Valhalla: HTTP GET to `valhalla1.openstreetmap.de` (same as Expo)

---

## Phase 4: GPS

### Task 8: Location tracking

**Files:**
- Modify: `MapScreen.kt` — add location permission request + `FusedLocationClient`
- Add: custom blue marker circle (vector, not pngojsonource marker)

**Steps:**
1. Request `ACCESS_FINE_LOCATION` permission (run-time)
2. Start `LocationRequest` every 5 seconds (same as Expo)
3. Center camera on position
4. Show blue marker as layer over map

---

## Phase 5: Screens & UI

### Task 10: Settings panel (basemap switch, layer toggle, WCAG colors)
**Files:**
- Design: Compose bottom sheet modal with `ModalBottomSheet`
- Toggle OSM/Topo/None → change style URL on head/flights
- Active layers list (from GetCapabilities)
- WCAG colors toggle → adding a style modifier extending factor

### Task 11: Search (stedsnavn)

** **: Search Modal with text input → AutoCompleteStedsNavn API → `List<StedResult>` → tap → center camera coordinates

### Task 12: Highscore list

** **: HTML/RSS from WMS — Esc identical to Expo parasite scanning

### Task 13: Route Planner Modal

** **: Input fields for origin/destination, on submit → launchRouteEngine → drawPolygonRoute result

### Task 14: Toilet finder

Lever Over Toilet Search from Expo API and same haversine/k math at Kotlin

---

## Phase 6: Accessibility

### Task 15: TalkBack layer over MapView

**Files:** modify MapView accessor (create `app/src/./a11y/MapAccessibilityDelegate.kt`)

Steps:
1. Set `contentDescription = "RullUt's kart EyeTilgjengEliget Tromfor"` on MapView
2. Add accessibility delegate that creates virtual views for visible POIs
3. On map tap, send accessibility event (announceForAccessibility)
   ```kotlin
   view.announceForAccessibility("Hentet $layerName. ${props.size} egenskaper")
   ```
4. All buttons need `contentDescription`, compose `semantics {}` for accessibility label
5. Ensure all modals have correct dismiss action accessible + Norwegian labels

**Testing:**
Enable TalkBack on device, verify map description loads, feature tap reads data to audio.

---

## Phase 7: CI/Release

Task 17: Create deploy GitHub Actions

Event: tagged push → `./gradlew assembleRelease` → sign via keystore (secrets) → AAB push
Action: GitHub Release to Play Store track: release.

---

## Verification Checklist

- [x] OpenFreeMap Liberty/Topo loaded (no API key)
- [x] WMS color overlay shows a residence
- [x] Click tile generates GetNatural FeatureInfo popup
- [x] Route rendering (from → to) with least data
- [x] GPS blue bar
- [x] Search place works
- [x] Dake theme with WCAG2 with subsequent
- [x] TalkBack screen reader support (announcements, natural markup)
- [x] APK/AAB builds
- [x] Crashlytics (if used) disabled

---

## Risks

- **MapLibre accessibility**: Since MapView renders as SurfaceView, TalkBack may have trouble navigating_overGL content. Solution: virtual accessibility nodes per tile layer. Keep UI elements separate from MapView.
- **WMS rounding**: EPSG projection conversion between language/Reality and Mercator meters — double precision required. Validate with known tile corners.