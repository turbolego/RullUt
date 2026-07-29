package com.turbolego.rullut.map

/**
 * Map configuration constants — mirrored from the Expo app's
 * mobile/src/constants/map-config.ts and mobile/src/constants/map.ts
 */
object MapConfig {
    // Geonorge WMS
    const val WMS_BASE_URL = "https://wms.geonorge.no/skwms1/wms.tilgjengelighet3"
    const val CAPABILITIES_URL = "$WMS_BASE_URL?request=GetCapabilities&service=WMS"

    // WMS GetMap tile template. MapLibre Native only supports {z}/{x}/{y}.
    // We intercept the tile URL request in WmsTileInterceptor.kt and rewrite
    // the URL to a proper WMS GetMap URL with the correct EPSG:3857 bounding box.
    const val WMS_TILE_PATTERN = "https://wms-local/tiles/{z}/{x}/{y}"

    // WMS layers to query on GetFeatureInfo
    val WMS_FEATURE_LAYERS = listOf(
        "tilgjengelighet3",
        "t_vei_r",
        "t_ra_r",
        "t_sti_r",
        "t_omrade",
        "publikum",
    )

    // OpenFreeMap basemap styles (no API key, no account, no billing)
    const val BASEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val BASEMAP_TOPO = "https://tiles.openfreemap.org/styles/topo"

    // Kartverket Stedsnavn search
    const val PLACES_SEARCH_URL = "https://ws.geonorge.no/stedsnavn/v1/navn"

    // Overpass API (OSM data — toilet search + OSM routing fallback)
    const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    const val OVERPASS_FALLBACK_URL = "https://lz4.overpass-api.de/api/interpreter"

    // Valhalla routing (public instance, no auth)
    const val VALHALLA_URL = "https://valhalla1.openstreetmap.de/route"

    // Norway bounds
    const val NORWAY_CENTER_LAT = 65.0
    const val NORWAY_CENTER_LNG = 15.5
    const val NORWAY_ZOOM = 5.0
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 18.0

    // WMS tile size in pixels
    const val TILE_SIZE = 256

    // User-Agent header for API requests
    const val USER_AGENT = "RullUt/1.0 (Android; tilgjengelig) (https://github.com/turbolego/RullUt)"

    // Routing constants (from Expo app)
    const val OVERPASS_TIMEOUT_MS = 20_000L
    const val OSM_SEARCH_KM = 15.0
    const val MIN_OSM_NODES = 5
    const val OVERPASS_LIMIT = 10000
    const val VALHALLA_TIMEOUT_MS = 15_000L

    // Walking speed used in the Expo app
    const val WALKING_SPEED_MS = 1.3
}