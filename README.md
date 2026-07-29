# RullUt — Tilgjengelig ruteplanlegger

**Rull Ut** is a native Android app for finding wheelchair-accessible routes using Kartverket's Geonorge WMS accessibility data. Built with MapLibre Native, Jetpack Compose, and Kotlin.

## Features

- **WMS tilgjengelighet overlay** — Geonorge `tilgjengelighet3` WMS layer rendered as raster tiles via custom OkHttp URL interceptor
- **Free basemap** — OpenFreeMap Liberty/Topo (no API key, no billing)
- **GetFeatureInfo** — Tap on map to query WMS feature data (accessibility ratings)
- **3-tier routing** — WFS local graph (Dijkstra) → Overpass OSM → Valhalla pedestrian API
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

## Building

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented UI + accessibility tests
```

## Required setup

1. No API keys or billing accounts needed (MapLibre BSD + OpenFreeMap + public Geonorge/Overpass/Valhalla APIs)
2. A Google Cloud billing-account-bound API key is **NOT** required (we use MapLibre, not Google Maps SDK)

## Testing

- **Unit tests** (JVM): Dijkstra routing, FeatureInfo parser, polyline decode
- **Instrumented tests** (device/emulator): Compose UI semantics (TalkBack), accessibility content descriptions
- **Accessibility**: WCAG 2.1 AA contrast ratio, min 48dp touch targets, TalkBack descriptions on all controls

## License

MIT (pending) — built from open-source components (MapLibre Native BSD, OpenFreeMap, Geonorge data)
