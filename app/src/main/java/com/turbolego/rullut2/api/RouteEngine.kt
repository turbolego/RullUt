package com.turbolego.rullut2.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.*

/**
 * Route engine: computes accessible routes between two known points.
 *
 * Fallback chain (first available wins):
 *   1. WFS — builds a routing graph from Geonorge tilgjengelighet road data
 *      (app:TettstedVei / app:FriluftTurvei). Same data source as the
 *      Highscore feature; no third-party dependency. [WfsRouteApi]
 *   2. Overpass OSM — live OSM roads, custom Dijkstra. [OsmRouteApi]
 *   3. Valhalla — production pedestrian routing. [ValhallaRouteApi]
 *
 * The original Expo app had a pre-compiled WFS routing graph
 * (norge-routing-graph.dat) as the first tier. We rebuild the graph on demand
 * from live WFS data instead of shipping a ~14MB file that needs regeneration.
 */
object RouteEngine {

    private const val TAG = "RouteEngine"

    /**
     * Find an accessible route between two points.
     * Tries WFS → Overpass OSM → Valhalla, then runs accessibility assessment.
     */
    suspend fun findRoute(
        context: Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RouteResult? {
        // 1) WFS road network (primary — authoritative, no third-party server)
        var path: Dijkstra.RoutePath? = null
        var source = "wfs"
        try {
            path = WfsRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
        } catch (e: Exception) {
            Log.w(TAG, "WFS routing failed", e)
        }

        // 2) Overpass OSM fallback
        if (path == null) {
            try {
                path = OsmRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
                source = "osm"
            } catch (e: Exception) {
                Log.w(TAG, "Overpass routing failed", e)
            }
        }

        // 3) Valhalla pedestrian API fallback
        if (path == null) {
            try {
                path = ValhallaRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
                source = "valhalla"
            } catch (e: Exception) {
                Log.w(TAG, "Valhalla routing failed", e)
            }
        }

        if (path == null || path.coordinates.size < 2) {
            return null
        }

        // Build GeoJSON for map rendering
        val geojson = buildGeoJson(path.coordinates)

        // Compute stats
        val physDist = path.distanceMeters
        val durationSec = physDist / MapConfig.WALKING_SPEED_MS

        // Accessibility assessment on the route
        val assessment = AccessibilityAssessment.assess(
            context = context,
            coordinates = path.coordinates,
            source = source,
        )

        return RouteResult(
            geojson = geojson,
            distanceMeters = physDist,
            durationSeconds = durationSec,
            distanceLabel = formatDistance(physDist),
            durationLabel = formatDuration(durationSec),
            segments = assessment.segments,
            accessiblePct = assessment.accessiblePct,
            partiallyAccessiblePct = assessment.partiallyAccessiblePct,
            notAccessiblePct = assessment.notAccessiblePct,
            unknownPct = assessment.unknownPct,
            routeSource = source,
        )
    }

    /**
     * Build a GeoJSON FeatureCollection string for the route path.
     */
    private fun buildGeoJson(coordinates: List<Pair<Double, Double>>): String {
        val coordsArray = org.json.JSONArray()
        for ((lng, lat) in coordinates) {
            coordsArray.put(org.json.JSONArray().apply { put(lng); put(lat) })
        }

        return org.json.JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", org.json.JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", coordsArray)
                    })
                    put("properties", org.json.JSONObject())
                })
            })
        }.toString()
    }

    /**
     * Format distance in meters to a human-readable label.
     */
    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> "%.1f km".format(meters / 1000.0)
        }
    }

    /**
     * Format duration in seconds to a human-readable label.
     */
    private fun formatDuration(seconds: Double): String {
        val totalSec = seconds.toInt()
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        return when {
            hours > 0 -> "${hours}t ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "< 1m"
        }
    }
}