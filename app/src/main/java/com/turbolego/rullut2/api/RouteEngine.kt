package com.turbolego.rullut2.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.RouteResult

/** Accessible routing facade that ranks candidates by accessibility first. */
object RouteEngine {
    private const val TAG = "RouteEngine"

    /** Legacy convenience API: returns the best wheelchair route. */
    suspend fun findRoute(
        context: Context, fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
    ): RouteResult? = findRoutes(context, fromLat, fromLon, toLat, toLon).firstOrNull()

    /**
     * Computes every available candidate. Accessibility is assessed before
     * sorting, so a longer green route beats a shorter inaccessible route.
     */
    suspend fun findRoutes(
        context: Context, fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
    ): List<RouteResult> {
        val candidates = mutableListOf<RouteResult>()
        val sources = listOf(
            "wfs" to suspend { WfsRouteApi.computeRoute(fromLat, fromLon, toLat, toLon) },
            "osm" to suspend { OsmRouteApi.computeRoute(fromLat, fromLon, toLat, toLon) },
            "valhalla" to suspend { ValhallaRouteApi.computeRoute(fromLat, fromLon, toLat, toLon) },
        )
        for ((source, compute) in sources) {
            try {
                val path = compute()
                if (path != null && path.coordinates.size >= 2) {
                    candidates += assessPath(context, path, source)
                }
            } catch (e: Exception) {
                Log.w(TAG, "$source routing failed", e)
            }
        }
        return candidates.distinctBy { it.routeSource }.sortedWith(
            compareByDescending<RouteResult> { it.accessiblePct }
                .thenByDescending { it.partiallyAccessiblePct }
                .thenBy { it.notAccessiblePct }
                .thenBy { it.distanceMeters },
        )
    }

    private suspend fun assessPath(
        context: Context, path: Dijkstra.RoutePath, source: String,
    ): RouteResult {
        val distance = path.distanceMeters
        val duration = distance / MapConfig.WALKING_SPEED_MS
        val assessment = AccessibilityAssessment.assess(context, path.coordinates, source)
        return RouteResult(
            geojson = buildGeoJson(path.coordinates),
            distanceMeters = distance,
            durationSeconds = duration,
            distanceLabel = formatDistance(distance),
            durationLabel = formatDuration(duration),
            segments = assessment.segments,
            accessiblePct = assessment.accessiblePct,
            partiallyAccessiblePct = assessment.partiallyAccessiblePct,
            notAccessiblePct = assessment.notAccessiblePct,
            unknownPct = assessment.unknownPct,
            routeSource = source,
        )
    }

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

    private fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        else -> "%.1f km".format(meters / 1000.0)
    }

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
