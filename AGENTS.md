# RullUt — Agent Instruction Context

Repository for the RullUt native Android app: a zero-billing, privacy-first
wheelchair-accessible route planner for Norway. This file is the entrypoint
for AI coding agents working on this repository (Codex, Claude Code, Gemini
CLI, Hermes and similar agents that load Markdown instructions).

- Read this file first. It is the source of truth for how to work here.
- Read only the sections relevant to your task. Do not load everything.
- When in doubt about actual behaviour, inspect the current source, tests and
  configuration — never trust an older commit, plan or discussion as fact.

## Instruction hierarchy

1. This repository's `AGENTS.md` establishes project-specific rules.
2. An explicit current user instruction takes precedence over these defaults
   unless it conflicts with platform-level or security requirements.
3. Current source code, configuration and reproducible results determine what
   is actually true about the app. Documentation may be stale.

## Project overview

Native Android app (Kotlin, Jetpack Compose, Material 3) that finds
wheelchair-accessible routes across Norway using Kartverket's Geonorge
accessibility data. Built on MapLibre Native + OpenFreeMap (no API keys, no
accounts, no billing). Published to Google Play Store as
`com.turbolego.rullut3`.

Users: wheelchair users and accessibility-conscious pedestrians in Norway.
Scope is strictly map-based accessibility routing for Norway. There is no
backend, no account system, no tracking and no personal-data collection.

## Architecture

```
com.turbolego.rullut2/
├── a11y/     Accessibility utilities (TalkBack announcements)
├── api/      Network layer (FeatureInfo, routing, search, location, WFS/WMS)
├── i18n/     Language and string management (Norsk Bokmål + English)
├── map/      MapLibre setup (config constants, style builder, WMS interceptor)
├── model/    Data classes (FeatureInfo, RouteResult, RoutingGraph, ViewportFeature)
└── ui/       Jetpack Compose screens and modals
```

### Key pattern 1 — WMS tile interception

MapLibre Native only supports `{z}/{x}/{y}` tile URL tokens, not WMS `{bbox-epsg-3857}`.
`app/src/main/java/com/turbolego/rullut2/map/WmsTileInterceptor.kt` registers a
custom `OkHttpClient` via `HttpRequestUtil.setOkHttpClient()` and rewrites each
dummy `wms-local` tile URL into a proper Geonorge WMS GetMap URL with the
correct EPSG:3857 bounding box. The active render layer is passed as the
`layers` query parameter by `MapStyleBuilder`. Do not bypass or re-register this
interceptor casually; a second registration replaces the first.

### Key pattern 2 — routing fallback chain

`api/RouteEngine.kt` tries route sources in order and uses the first that
succeeds (each wrapped independently in try/catch, logging a warning on
failure):

1. **WFS** — builds a routing graph on demand from Geonorge `tilgjengelighet`
   road data (`app:TettstedVei` / `app:FriluftTurvei`). Same data source as the
   Highscore feature; no third-party routing dependency.
2. **Overpass OSM** — live OSM roads within a bounding box, custom in-memory
   `Dijkstra` shortest-path (`api/Dijkstra.kt`).
3. **Valhalla** — public pedestrian routing at `valhalla1.openstreetmap.de`
   (`api/ValhallaRouteApi.kt`), polyline-decoded (`(lng, lat)` pairs).

The result is then run through `api/AccessibilityAssessment.kt` to produce
colour-coded segment scores (0-3) and accessible/partially/not/unknown
percentages. Keep the fallback ordering stable unless the product explicitly
changes it.

### Key pattern 3 — dependency-free public APIs via MapConfig

All external endpoints and constants (WMS/WFS base URLs, Overpass, Valhalla,
OpenFreeMap basemaps, Norway bounds, timeouts, grid-scan constants, User-Agent)
live in `app/src/main/java/com/turbolego/rullut2/map/MapConfig.kt`. There are
no private credentials or API keys anywhere in the repository. If you introduce
a new external service or constant, put it in `MapConfig.kt` and set a
descriptive app `User-Agent` header on requests.

## Build, test and release commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew test                 # Run JVM unit tests
./gradlew connectedAndroidTest # Run instrumented UI + accessibility tests (needs device/emulator)
./gradlew bundleRelease        # Build signed AAB for Play Store
```

- `versionCode` and `versionName` are derived from CI env vars
  (`GITHUB_RUN_NUMBER`, `GITHUB_REF_NAME`); they default to `1` / `1.0.0`
  locally.
- Release minification (R8) is enabled; keep keep-rules in `app/proguard-rules.pro`.
- Some tests (e.g. `FeatureInfoParserTest`, `WmsTileInterceptorTest`) require
  the real `XmlPullParser` dependency (kxml) and `unitTests.isReturnDefaultValues`
  — both are already configured. Do not remove them.
- A note in `app/build.gradle.kts` documents that connected tests are
  configured with `doNotTrackState` because UTP lock files can make output
  snapshots unreadable.

## Security and secrets

- Never commit the upload keystore (`app/keystore/rullut-upload-keystore.jks`)
  or `app/keystore.properties`. They are gitignored.
- Release signing in CI reads `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD` from GitHub Actions secrets; locally it falls back to the
  gitignored keystore file.
- The app deliberately collects no personal data and sends no analytics.
  Preserve this: do not add tracking, crash-reporting SDKs, or network
  telemetry without an explicit product decision.
- GPS position is processed locally on device only.

## Accessibility (WCAG 2.1 AA)

Accessibility is a first-class product requirement, not an afterthought:

- All interactive elements must have TalkBack content descriptions.
- Maintain the amber-on-ink dark theme with ≥5.65:1 contrast (`ui/Theme.kt`).
- Touch targets ≥ 48dp.
- Instrumented accessibility tests live in `app/src/androidTest/`. When you
  add or change UI, verify content descriptions and semantics still hold and
  add tests where behaviour changes.

## i18n / localization

The app supports Norsk Bokmål and English via `i18n/LanguageManager.kt` and
`i18n/Strings.kt`. New or changed user-visible strings must be added to both
languages. Do not hard-code user-facing text inline in composables.

## Testing expectations

- Unit tests (JVM): `app/src/test/java/com/turbolego/rullut2/` — Dijkstra
  routing, FeatureInfo parser, polyline decode, WMS tile URL transform, WFS
  route computation (Burudvann scenario).
- Instrumented tests (device/emulator): `app/src/androidTest/java/` — Compose
  UI semantics and accessibility content descriptions.
- Add focused tests when you change parsing, routing or URL-building logic.
- Prefer module-scoped tasks first (e.g. `./gradlew :app:testDebugUnitTest`),
  then broader checks only when needed.

## CI / dependency management

- `.github/workflows/build-signed-aab.yml` builds a signed AAB on version tags
  (`v*`) or manual dispatch and uploads to Google Play internal track.
- `renovate.json` enables Renovate's recommended config for automated
  dependency updates. Keep dependency bumps grouped and verify the build after
  meaningful upgrades (MapLibre, Compose BOM, Kotlin).

## Pitfalls and preserved decisions

- **WMS interceptor is single-instance.** `WmsInterceptorManager` sets the
  global MapLibre `OkHttpClient` via `HttpRequestUtil.setOkHttpClient()`.
  A second call replaces the first; the interceptor only rewrites URLs whose
  host matches the dummy `wms-local` host, so non-WMS traffic passes through.
- **WMS feature query layers** (used for GetFeatureInfo) are defined separately
  from the default *render* layer (`tilgjengelighet3`). See
  `WMS_FEATURE_LAYERS` vs `DEFAULT_WMS_RENDER_LAYER` in `MapConfig.kt`.
- **The original Expo app shipped a pre-compiled WFS routing graph (~14MB).**
  This native app deliberately rebuilds the graph on demand from live WFS data
  instead, to keep the app small and the data fresh. Do not reintroduce a
  bundled binary graph.
- **Public third-party APIs have no SLA.** Map data, routing and search all come
  from free public APIs. The app must degrade gracefully (return null / fall back)
  when these are slow or down — never crash on an external failure.
- **`applicationId` is `com.turbolego.rullut3`** while the Kotlin package and
  `namespace` is `com.turbolego.rullut2`. This mismatch is intentional and must
  not be "fixed" accidentally.

## Handover

Last verified baseline: `main` at commit `0670147` (Merge pull request #22).
The project is active with automated dependency updates merged regularly. If you
are continuing work from a previous session, check the latest CI status and
`gh pr list` on the repository before assuming the above status is current.