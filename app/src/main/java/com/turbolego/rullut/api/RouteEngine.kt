package com.turbolego.rullut.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.model.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

/**
 * Route engine: tries Overpass OSM → Valhalla pedestrian API.
 *
 * The original Expo app had a pre-compiled WFS routing graph (norge-routing-graph.dat)
 * as the first tier. We skip it because:
 * 1. The ~14MB file requires periodic regeneration from Geonorge WFS
 * 2. Overpass + Valhalla always use live OSM data (fresher routes)
 * 3. Users get better results without waiting for graph rebuilds
 * 4. App size stays small (<5MB)
 *
 * Fallback chain:
 *   1. Overpass API — fetches OSM roads, builds graph, runs Dijkstra
 *   2. Valhalla — production pedestrian routing with proper turn-by-turn
 */
object RouteEngine {

    private const val TAG = "RouteEngine"

    /**
     * Find an accessible route between two points.
     * Tries Overpass OSM first, then falls back to Valhalla.
     * After finding a route, runs accessibility assessment.
     */
    suspend fun findRoute(
        context: Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RouteResult? {
        // 1) Try Overpass OSM (custom Dijkstra on live OSM road data)
        var path: Dijkstra.RoutePath? = null
        var source = "osm"
        try {
            path = OsmRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
        } catch (e: Exception) {
            Log.w(TAG, "Overpass routing failed", e)
        }

        // 2) Fall back to Valhalla pedestrian API
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
        val coordsArray = JSONArray()
        for ((lng, lat) in coordinates) {
            coordsArray.put(JSONArray().apply { put(lng); put(lat) })
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", coordsArray)
                    })
                    put("properties", JSONObject())
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