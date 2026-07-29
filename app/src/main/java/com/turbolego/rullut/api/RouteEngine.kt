package com.turbolego.rullut.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Route engine: tries WFS graph → Overpass → Valhalla.
 * Ported from the Expo app's routing pipeline:
 *   graph-utils.ts -> osm-route.ts -> valhalla-route.ts
 */
object RouteEngine {

    private const val TAG = "RouteEngine"
    private var cachedGraph: RoutingGraph? = null

    /**
     * Find an accessible route between two points.
     * Tries WFS (local graph) first, then OSM Overpass, then Valhalla.
     * After finding a route, runs accessibility assessment.
     */
    suspend fun findRoute(
        context: Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RouteResult? {
        // 1) Try local WFS graph
        var path: Dijkstra.RoutePath? = try {
            routeOnLocalGraph(context, fromLat, fromLon, toLat, toLon)
        } catch (_: Exception) { null }

        var source = "wfs"

        // 2) Fall back to Overpass OSM
        if (path == null) {
            try {
                path = OsmRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
                source = "osm"
            } catch (_: Exception) { null }
        }

        // 3) Final fallback to Valhalla
        if (path == null) {
            try {
                path = ValhallaRouteApi.computeRoute(fromLat, fromLon, toLat, toLon)
                source = "valhalla"
            } catch (_: Exception) { null }
        }

        if (path == null || path.coordinates.size < 2) {
            return null
        }

        // Build GeoJSON
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
     * Route on the local WFS graph (loaded from assets).
     * The graph is a compact JSON file: { la: int[], lo: int[], e: int[] }
     */
    private suspend fun routeOnLocalGraph(
        context: Context,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Dijkstra.RoutePath? {
        val graph = loadLocalGraph(context) ?: return null

        val start = Dijkstra.findNearestNode(graph, fromLat, fromLon)
        val end = Dijkstra.findNearestNode(graph, toLat, toLon)
        if (start < 0 || end < 0) return null

        return Dijkstra.compute(graph, start, end)
    }

    /**
     * Load the compact routing graph from assets JSON.
     * Format: { la: int[], lo: int[], e: int[] } (lat/lon × 10000, flat edges)
     */
    private fun loadLocalGraph(context: Context): RoutingGraph? {
        if (cachedGraph != null) return cachedGraph

        try {
            val json = context.assets.open("norge-routing-graph.dat")
                .bufferedReader().use { it.readText() }
            val raw = org.json.JSONObject(json)

            val la = raw.getJSONArray("la")
            val lo = raw.getJSONArray("lo")
            val e = raw.getJSONArray("e")

            val nodeCount = la.length()
            val nodes = List(nodeCount) { i ->
                GraphNode(
                    lat = la.getInt(i) / 10000.0,
                    lon = lo.getInt(i) / 10000.0,
                )
            }
            val edges = List(nodeCount) { mutableListOf<GraphEdge>() }
            for (i in 0 until e.length() step 3) {
                val from = e.getInt(i)
                val to = e.getInt(i + 1)
                val dist = e.getInt(i + 2)
                if (from >= 0 && from < nodeCount && to >= 0 && to < nodeCount) {
                    edges[from].add(GraphEdge(to = to, weight = dist.toDouble()))
                }
            }

            val graph = RoutingGraph(nodes = nodes, edges = edges)
            cachedGraph = graph
            return graph
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local graph", e)
            return null
        }
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
            hours > 0 -> "${h}t ${m}m"
            else -> "${m}m"
        }
    }

    /**
     * Internal struct for passing route coordinate data.
     */
    data class RouteSegmentData(
        val coordinates: List<Pair<Double, Double>>,
        val distanceMeters: Double,
        val routeSource: String,
    )
}