# RullUt — Tilgjengelig ruteplanlegger

**Rull Ut** is a native Android app for finding wheelchair-accessible routes using Kartverket's Geonorge WMS accessibility data. Built with MapLibre Native, Jetpack Compose, and Kotlin.

## Features

- **WMS tilgjengelighet overlay** — Geonorge `tilgjengelighet3` WMS layer rendered as raster tiles via custom OkHttp URL interceptor
- **Free basemap** — OpenFreeMap Liberty/Topo (no API key, no billing)
- **GetFeatureInfo** — Tap on map to query WMS feature data (accessibility ratings)
- **2-tier routing** — Overpass OSM (custom Dijkstra) → Valhalla pedestrian API
- **Accessibility assessment** — Scores route segments (0-3) based on WMS `t_vei_r` data
- **GPS tracking** — FusedLocationProviderClient with live position flow
- **Place search** — Kartverket stedsnavn API for Norwegian place names
- **Toilet finder** — Overpass API for nearby accessible toilets
- **TalkBack support** — Content descriptions on all interactive elements, WCAG 2.1 AA
- **Dark theme** — WCAG-compliant amber-on-ink color scheme (≥5.65:1 contrast)

## Architecture

```
com.turbolego.rullut/
├── a11y/          — Accessibility utilities (TalkBack announcements)
├── api/           — Network layer (FeatureInfo, routing, search, location)
├── map/           — MapLibre Native setup (WMS interceptor, style builder,
│                    config constants)
├── model/         — Data classes (FeatureInfo, RouteResult, RoutingGraph)
└── ui/            — Jetpack Compose screens (MapScreen, FeaturePopup,
                    RoutePlanner, Settings, Search)
```

### Key pattern: WMS tile interception

MapLibre Native Android only supports `{z}/{x}/{y}` tokens, not `{bbox-epsg-3857}`.  
Solution: Use a dummy `wms-local` host in the tile URL. A custom `OkHttpClient` interceptor registered via `HttpRequestUtil.setOkHttpClient()` rewrites each request into a proper Geonorge WMS GetMap URL with the correct EPSG:3857 bounding box.

### Routing fallback

1. **Overpass API** — Fetches live OSM roads within a bounding box, builds a graph in memory, runs Dijkstra shortest-path
2. **Valhalla** — Public pedestrian routing API at `valhalla1.openstreetmap.de` (no auth required)

The original Expo app used a pre-compiled WFS graph (14MB). We skip that tier to keep the app small and use always-fresh OSM data.

## Building

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented UI + accessibility tests
```

### Release build (Play Store)

```bash
./gradlew bundleRelease        # Generate AAB for Play Store upload
```

The AAB will be at `app/build/outputs/bundle/release/app-release.aab`.

## Publishing to Google Play Store

### Prerequisites

1. **Google Play Developer account** — one-time fee of **$25 USD** (~250 NOK)
   - Sign up at https://play.google.com/console/signup
   - This is the **only cost** — publishing is free after this
   
2. **Keystore** — already generated at `app/keystore/rullut-upload-keystore.jks`
   - **BACK THIS UP** — losing the keystore means you cannot publish updates
   - Password: stored in `app/keystore.properties` (gitignored — safe)

### Steps

1. Open the [Google Play Console](https://play.google.com/console)
2. Create a new app → "RullUt" → select "App" → name "RullUt"
3. Go to **Release > Testing > Open testing** (or Production)
4. Upload `app/build/outputs/bundle/release/app-release.aab`
5. Google Play App Signing will ask you to opt in — **this is free and recommended**
   - Your upload key is the keystore we generated
   - Google manages the production signing key automatically
6. Fill in the store listing:
   - Description: Norwegian accessibility mapping app
   - Screenshots: 2+ phone screenshots (use emulator)
   - Category: Maps & Navigation
   - Content rating: Everyone
7. Complete the "App content" questionnaire
8. Set pricing: **Free** (no cost to users)

### Ongoing costs: **$0/year**

| Item | Cost |
|---|---|
| Google Play Developer account | **$25 once** |
| MapLibre Native (BSD) | **$0** |
| OpenFreeMap basemap | **$0** |
| Geonorge WMS (public data) | **$0** |
| Overpass API (public) | **$0** |
| Valhalla (public instance) | **$0** |
| Hosting for APK/AAB | **$0** (on GitHub) |
| **Total** | **$25 one-time, then $0 forever** |

## Required setup

- No API keys or billing accounts for maps (MapLibre BSD + OpenFreeMap)
- Google Cloud billing-account-bound API key is **NOT** required
- Google Play Services for location only (comes with every Android phone)

## Testing

- **Unit tests** (JVM): Dijkstra routing, FeatureInfo parser, polyline decode
- **Instrumented tests** (device/emulator): Compose UI semantics (TalkBack), accessibility content descriptions
- **Accessibility**: WCAG 2.1 AA contrast ratio, min 48dp touch targets, TalkBack descriptions on all controls

## License

MIT (pending) — built from open-source components (MapLibre Native BSD, OpenFreeMap, Geonorge data)
