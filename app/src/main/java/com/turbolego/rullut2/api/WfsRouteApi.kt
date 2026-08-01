package com.turbolego.rullut2.api

import android.util.Log
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.GraphEdge
import com.turbolego.rullut2.model.GraphNode
import com.turbolego.rullut2.model.RoadSegmentFeature
import com.turbolego.rullut2.model.RoutingGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust route computation between two known points using the Geonorge
 * tilgjengelighet WFS road network (app:TettstedVei / app:FriluftTurvei).
 *
 * Why this exists: the old routing chain (Overpass OSM → Valhalla) depended on
 * third-party servers that are frequently overloaded or down, so "route to
 * toilet" often failed even though both endpoints were known. This API builds
 * the routing graph from the same WFS road data the app already fetches for
 * the Highscore feature — one consistent, authoritative data source.
 *
 * Coverage note: the tilgjengelighet road survey has gaps (e.g. the Burudvann
 * lake area in Bærum has toilets but zero surveyed roads). When this API
 * returns null, [RouteEngine] falls back to Overpass OSM → Valhalla, so
 * routes still work in unsurveyed areas.
 *
 * Pipeline:
 *   1. Fetch surveyed road segments in the bbox spanning both endpoints.
 *   2. Build a routing graph: every segment vertex becomes a node, consecutive
 *      vertices of a segment become edges, and segment endpoints that lie
 *      within [CONNECT_TOLERANCE_M] of each other are joined so the graph is
 *      connected across segments.
 *   3. Dijkstra between the graph nodes nearest to the two endpoints.
 */
object WfsRouteApi {

    private const val TAG = "WfsRouteApi"

    /** Extra degrees around the endpoint bbox so the graph has room to connect. */
    private const val BBOX_MARGIN = 0.012

    /** Join segment endpoints closer than this (metres) to keep the graph connected. */
    private const val CONNECT_TOLERANCE_M = 15.0

    /** Dijkstra snap radius for the two known endpoints (metres). */
    private const val SNAP_RADIUS_M = 400.0

    /**
     * Compute a pedestrian route between two known points using WFS road data.
     * Returns null when no graph or no path exists (caller falls back).
     */
    suspend fun computeRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Dijkstra.RoutePath? = withContext(Dispatchers.IO) {
        val segments = try {
            RoadWfsApi.fetchRoadSegments(
                minLat = minOf(fromLat, toLat) - BBOX_MARGIN,
                minLon = minOf(fromLon, toLon) - BBOX_MARGIN,
                maxLat = maxOf(fromLat, toLat) + BBOX_MARGIN,
                maxLon = maxOf(fromLon, toLon) + BBOX_MARGIN,
            )
        } catch (e: Exception) {
            Log.w(TAG, "WFS road fetch failed", e)
            return@withContext null
        }

        computeRouteOnSegments(segments, fromLat, fromLon, toLat, toLon)
    }

    /**
     * Route computation on an already-fetched segment list.
     * Internal so unit tests can exercise the full pipeline offline.
     */
    internal suspend fun computeRouteOnSegments(
        segments: List<RoadSegmentFeature>,
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Dijkstra.RoutePath? = withContext(Dispatchers.Default) {
        val graph = buildGraph(segments) ?: run {
            Log.w(TAG, "No usable road segments in bbox")
            return@withContext null
        }

        val start = Dijkstra.findNearestNode(graph, fromLat, fromLon, SNAP_RADIUS_M)
        val end = Dijkstra.findNearestNode(graph, toLat, toLon, SNAP_RADIUS_M)
        if (start < 0 || end < 0) {
            Log.w(TAG, "Endpoints not near WFS road network (start=$start end=$end)")
            return@withContext null
        }

        val path = Dijkstra.compute(graph, start, end)
        if (path == null) {
            Log.w(TAG, "No WFS path between endpoints")
        }
        path
    }

    /**
     * Build a connected [RoutingGraph] from WFS road segments.
     * Internal so unit tests can exercise it directly.
     */
    internal fun buildGraph(segments: List<RoadSegmentFeature>): RoutingGraph? {
        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<MutableList<GraphEdge>>()
        val highwayLookup = mutableMapOf<String, String>()

        fun addNode(lat: Double, lon: Double): Int {
            val idx = nodes.size
            nodes.add(GraphNode(lat, lon))
            edges.add(mutableListOf())
            return idx
        }

        // Deduplicate near-identical vertices so segments sharing a point
        // (or nearly so) actually connect. Keyed by snapped coordinate.
        val vertexIndex = mutableMapOf<Pair<Long, Long>, Int>()
        fun vertexNode(lat: Double, lon: Double): Int {
            val key = Pair(
                Math.round(lat * 1e6),
                Math.round(lon * 1e6),
            )
            return vertexIndex.getOrPut(key) { addNode(lat, lon) }
        }

        for (seg in segments) {
            val pts = seg.geometry
            if (pts.size < 2) continue

            val idxs = pts.map { (lon, lat) -> vertexNode(lat, lon) }

            // Edges along the segment polyline
            for (i in 1 until idxs.size) {
                val u = idxs[i - 1]
                val v = idxs[i]
                val (lon1, lat1) = pts[i - 1]
                val (lon2, lat2) = pts[i]
                val d = CoordinateUtils.haversineM(lat1, lon1, lat2, lon2)
                if (d < 0.5 || d > 2000.0) continue
                edges[u].add(GraphEdge(v, d))
                edges[v].add(GraphEdge(u, d))
                val key = if (u < v) "$u,$v" else "$v,$u"
                highwayLookup.getOrPut(key) { seg.roadType.ifEmpty { "unclassified" } }
            }
        }

        if (nodes.size < 2) return null

        // Connect segment endpoints that are close but not exactly equal
        // (e.g. rounded coordinates) so disjoint segments form one network.
        val eps = CONNECT_TOLERANCE_M / 111_320.0
        for (i in 0 until nodes.size) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val dLat = a.lat - b.lat
                val dLon = (a.lon - b.lon) * Math.cos(Math.toRadians(a.lat))
                if (dLat * dLat + dLon * dLon > eps * eps) continue
                val d = CoordinateUtils.haversineM(a.lat, a.lon, b.lat, b.lon)
                if (d < 0.5 || d > CONNECT_TOLERANCE_M) continue
                val key = if (i < j) "$i,$j" else "$j,$i"
                if (highwayLookup.containsKey(key)) continue
                edges[i].add(GraphEdge(j, d))
                edges[j].add(GraphEdge(i, d))
                highwayLookup[key] = "unclassified"
            }
        }

        return RoutingGraph(
            nodes = nodes,
            edges = edges,
            highwayTagLookup = highwayLookup,
        )
    }
}
