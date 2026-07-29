package com.turbolego.rullut2.api

import com.turbolego.rullut2.model.RoutingGraph
import com.turbolego.rullut2.model.CoordinateUtils

/**
 * Dijkstra shortest-path algorithm on a RoutingGraph.
 * Ported from graph-utils.ts (Expo app).
 */
object Dijkstra {

    data class RoutePath(
        val coordinates: List<Pair<Double, Double>>, // (lng, lat)
        val distanceMeters: Double,
        val highwayTags: List<String>? = null,
    )

    /**
     * Find nearest node index within maxMeters.
     */
    fun findNearestNode(
        graph: RoutingGraph,
        lat: Double,
        lon: Double,
        maxMeters: Double = 20_000.0,
    ): Int {
        var bestIdx = -1
        var bestDist = maxMeters
        for (i in graph.nodes.indices) {
            val n = graph.nodes[i]
            val d = CoordinateUtils.haversineM(lat, lon, n.lat, n.lon)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    /**
     * Compute shortest path from startIdx to endIdx.
     * Returns null if no path exists.
     */
    fun compute(
        graph: RoutingGraph,
        startIdx: Int,
        endIdx: Int,
    ): RoutePath? {
        if (startIdx < 0 || endIdx < 0) return null
        if (startIdx == endIdx) return null

        val n = graph.edges.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val visited = BooleanArray(n)
        dist[startIdx] = 0.0

        // Binary heap (priority queue): [distance, nodeIndex]
        val heap = mutableListOf<Pair<Double, Int>>()
        heap.add(Pair(0.0, startIdx))

        fun heapPush(d: Double, node: Int) {
            var i = heap.size
            heap.add(Pair(d, node))
            while (i > 0) {
                val p = (i - 1) shr 1
                if (heap[p].first <= heap[i].first) break
                val tmp = heap[p]; heap[p] = heap[i]; heap[i] = tmp
                i = p
            }
        }

        fun heapPop(): Pair<Double, Int>? {
            if (heap.isEmpty()) return null
            val top = heap[0]
            val last = heap.removeAt(heap.lastIndex)
            if (heap.isNotEmpty()) {
                heap[0] = last
                var i = 0
                val nn = heap.size
                while (true) {
                    var smallest = i
                    val left = (i shl 1) + 1
                    val right = left + 1
                    if (left < nn && heap[left].first < heap[smallest].first) smallest = left
                    if (right < nn && heap[right].first < heap[smallest].first) smallest = right
                    if (smallest == i) break
                    val tmp = heap[i]; heap[i] = heap[smallest]; heap[smallest] = tmp
                    i = smallest
                }
            }
            return top
        }

        while (heap.isNotEmpty()) {
            val (d, u) = heapPop() ?: break
            if (visited[u]) continue
            visited[u] = true
            if (u == endIdx) break

            for (edge in graph.edges[u]) {
                val v = edge.to
                if (visited[v]) continue
                val nd = d + edge.weight
                if (nd < dist[v]) {
                    dist[v] = nd
                    prev[v] = u
                    heapPush(nd, v)
                }
            }
        }

        if (dist[endIdx] == Double.MAX_VALUE) return null

        // Reconstruct path
        val pathIdx = mutableListOf<Int>()
        var cur = endIdx
        while (cur != -1) {
            pathIdx.add(cur)
            cur = prev[cur]
        }
        pathIdx.reverse()

        // Build coordinates
        val coordinates = pathIdx.map { idx ->
            val node = graph.nodes[idx]
            Pair(node.lon, node.lat)
        }

        // Compute physical distance along path
        var physDist = 0.0
        val highwayTags = mutableListOf<String>()
        val lookup = graph.highwayTagLookup

        for (i in 1 until coordinates.size) {
            val (lng1, lat1) = coordinates[i - 1]
            val (lng2, lat2) = coordinates[i]
            physDist += CoordinateUtils.haversineM(lat1, lng1, lat2, lng2)

            if (lookup != null) {
                val u = pathIdx[i - 1]
                val v = pathIdx[i]
                val key = if (u < v) "$u,$v" else "$v,$u"
                highwayTags.add(lookup[key] ?: "unclassified")
            }
        }

        return RoutePath(
            coordinates = coordinates,
            distanceMeters = dist[endIdx],
            highwayTags = if (highwayTags.isNotEmpty()) highwayTags else null,
        )
    }
}