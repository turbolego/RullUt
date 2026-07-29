package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.GraphEdge
import com.turbolego.rullut2.model.GraphNode
import com.turbolego.rullut2.model.RoutingGraph
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Overpass API routing fallback.
 * Fetches OSM road data, builds a graph, and runs Dijkstra.
 * Ported from osm-route.ts (Expo app).
 */
object OsmRouteApi {

    private const val TAG = "OsmRouteApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(MapConfig.OVERPASS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(MapConfig.OVERPASS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Fetch OSM road network and compute a route.
     * Returns null if no route found.
     */
    suspend fun computeRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Dijkstra.RoutePath? {
        val graph = fetchOsmGraph(fromLat, fromLon, toLat, toLon)
            ?: return null

        val start = Dijkstra.findNearestNode(
            graph, fromLat, fromLon,
            maxMeters = MapConfig.OSM_SEARCH_KM * 1000.0
        )
        val end = Dijkstra.findNearestNode(
            graph, toLat, toLon,
            maxMeters = MapConfig.OSM_SEARCH_KM * 1000.0
        )

        if (start < 0 || end < 0) return null

        return Dijkstra.compute(graph, start, end)
    }

    /**
     * Build a routing graph from Overpass API data.
     * Highway filter and margin same as Expo app.
     */
    private suspend fun fetchOsmGraph(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RoutingGraph? {
        val margin = 0.08
        val minLat = minOf(fromLat, toLat) - margin
        val maxLat = maxOf(fromLat, toLat) + margin
        val minLon = minOf(fromLon, toLon) - margin
        val maxLon = maxOf(fromLon, toLon) + margin

        val bbox = "$minLat,$minLon,$maxLat,$maxLon"

        // Highway filter — mirrors Expo app
        val query = buildString {
            append("[out:json][timeout:15];\n")
            append("(\n")
            append("""way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|service|track|path|footway|cycleway)$"]($bbox);""")
            append("\n);\n")
            append("(._;>;);\n")
            append("out body;")
        }

        var lastError: Exception? = null

        for (url in listOf(MapConfig.OVERPASS_URL, MapConfig.OVERPASS_FALLBACK_URL)) {
            try {
                val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
                    .toRequestBody(FORM_URLENCODED)

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", MapConfig.USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    lastError = RuntimeException("Overpass HTTP ${response.code}")
                    continue
                }
                val json = response.body?.string() ?: ""
                val graph = buildGraphFromOverpass(json, fromLat, fromLon, toLat, toLon)
                if (graph != null) return graph
            } catch (e: Exception) {
                lastError = e
            }
        }
        return null
    }

    /**
     * Parse Overpass JSON response into a RoutingGraph.
     * Same logic as buildGraphFromOverpass() in osm-route.ts.
     */
    private fun buildGraphFromOverpass(
        json: String,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): RoutingGraph? {
        try {
            val root = JSONObject(json)
            val elements = root.getJSONArray("elements")

            // Parse nodes
            val nodeMap = mutableMapOf<Long, Pair<Double, Double>>() // id -> (lat, lon)
            val wayRefs = mutableSetOf<Long>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val type = el.getString("type")
                val id = el.getLong("id")
                when (type) {
                    "node" -> {
                        if (el.has("lat")) {
                            val lat = el.getDouble("lat")
                            val lon = el.getDouble("lon")
                            nodeMap[id] = Pair(lat, lon)
                        }
                    }
                    "way" -> {
                        if (el.has("nodes")) {
                            val nodes = el.getJSONArray("nodes")
                            for (j in 0 until nodes.length()) {
                                wayRefs.add(nodes.getLong(j))
                            }
                        }
                    }
                }
            }

            // Build coordinate list from referenced nodes
            val coords = mutableListOf<GraphNode>()
            val osmIdToIndex = mutableMapOf<Long, Int>()
            for (id in wayRefs) {
                val c = nodeMap[id] ?: continue
                osmIdToIndex[id] = coords.size
                coords.add(GraphNode(lat = c.first, lon = c.second))
            }

            if (coords.size < MapConfig.MIN_OSM_NODES) return null

            // Build edges from ways + collect highway tags
            val edges = List(coords.size) { mutableListOf<GraphEdge>() }
            val highwayTagLookup = mutableMapOf<String, String>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                if (el.getString("type") != "way") continue
                if (!el.has("nodes")) continue

                val wayNodes = el.getJSONArray("nodes")
                val highway = el.optJSONObject("tags")?.optString("highway", "unclassified")
                    ?: "unclassified"

                for (j in 1 until wayNodes.length()) {
                    val u = osmIdToIndex[wayNodes.getLong(j - 1)] ?: continue
                    val v = osmIdToIndex[wayNodes.getLong(j)] ?: continue

                    val d = CoordinateUtils.haversineM(
                        coords[u].lat, coords[u].lon,
                        coords[v].lat, coords[v].lon,
                    )
                    if (d < 1.0 || d > 2000.0) continue

                    edges[u].add(GraphEdge(to = v, weight = d))
                    edges[v].add(GraphEdge(to = u, weight = d))

                    val key = if (u < v) "$u,$v" else "$v,$u"
                    if (!highwayTagLookup.containsKey(key)) {
                        highwayTagLookup[key] = highway
                    }
                }
            }

            return RoutingGraph(
                nodes = coords,
                edges = edges,
                highwayTagLookup = highwayTagLookup,
            )
        } catch (_: Exception) {
            return null
        }
    }
}