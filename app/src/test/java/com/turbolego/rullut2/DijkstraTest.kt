package com.turbolego.rullut2

import com.turbolego.rullut2.api.Dijkstra
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.GraphEdge
import com.turbolego.rullut2.model.GraphNode
import com.turbolego.rullut2.model.RoutingGraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Dijkstra routing algorithm.
 * Verifies correctness of shortest-path computation on
 * synthetic small graphs.
 */
class DijkstraTest {

    @Test
    fun `shortest path on simple triangle`() {
        // Triangle: A(0,0) - B(1,0) - C(0,1) with edges:
        // A→B (111km approximate), B→C (111km), C→A (111km)
        val nodes = listOf(
            GraphNode(59.0, 10.0), // A: Oslo
            GraphNode(59.5, 10.5), // B: ~55km NE
            GraphNode(59.3, 10.8), // C: ~40km SE
        )

        val edges = listOf(
            mutableListOf(GraphEdge(to = 1, weight = 55000.0), GraphEdge(to = 2, weight = 40000.0)),
            mutableListOf(GraphEdge(to = 0, weight = 55000.0), GraphEdge(to = 2, weight = 35000.0)),
            mutableListOf(GraphEdge(to = 0, weight = 40000.0), GraphEdge(to = 1, weight = 35000.0)),
        )

        val graph = RoutingGraph(nodes = nodes, edges = edges)

        val result = Dijkstra.compute(graph, 0, 1)
        assertNotNull("Route should exist", result)
        assertEquals("Distance should be correct", 55000.0, result!!.distanceMeters, 1.0)
        assertTrue("Path should have at least 2 coordinates", result.coordinates.size >= 2)
    }

    @Test
    fun `shortest path via intermediary`() {
        // A→B→C should be shorter than direct A→C (synthetic weights)
        val nodes = listOf(
            GraphNode(59.0, 10.0), // A
            GraphNode(59.5, 10.5), // B
            GraphNode(60.0, 11.0), // C
        )

        val edges = listOf(
            mutableListOf(GraphEdge(to = 1, weight = 100.0)),
            mutableListOf(GraphEdge(to = 0, weight = 100.0), GraphEdge(to = 2, weight = 100.0)),
            mutableListOf(GraphEdge(to = 1, weight = 100.0)),
        )

        val graph = RoutingGraph(nodes = nodes, edges = edges)

        val result = Dijkstra.compute(graph, 0, 2)
        assertNotNull("Route via B should exist", result)
        assertEquals("Distance should be 200 (A→B→C)", 200.0, result!!.distanceMeters, 0.1)
        assertEquals("Path should have 3 nodes", 3, result.coordinates.size)
    }

    @Test
    fun `no path returns null`() {
        val nodes = listOf(
            GraphNode(59.0, 10.0),
            GraphNode(60.0, 11.0),
        )

        val edges = listOf(
            mutableListOf<GraphEdge>(),
            mutableListOf<GraphEdge>(),
        )

        val graph = RoutingGraph(nodes = nodes, edges = edges)

        val result = Dijkstra.compute(graph, 0, 1)
        assertNull("No path should exist", result)
    }

    @Test
    fun `same start and end returns null`() {
        val nodes = listOf(
            GraphNode(59.0, 10.0),
            GraphNode(60.0, 11.0),
        )

        val edges = listOf(
            mutableListOf(GraphEdge(to = 1, weight = 100.0)),
            mutableListOf(GraphEdge(to = 0, weight = 100.0)),
        )

        val graph = RoutingGraph(nodes = nodes, edges = edges)

        val result = Dijkstra.compute(graph, 0, 0)
        assertNull("Same node should return null", result)
    }

    @Test
    fun `invalid start index returns null`() {
        val graph = RoutingGraph(
            nodes = listOf(GraphNode(59.0, 10.0)),
            edges = listOf(mutableListOf<GraphEdge>()),
        )

        val result = Dijkstra.compute(graph, -1, 0)
        assertNull("Negative index should return null", result)
    }

    @Test
    fun `findNearestNode returns closest`() {
        val graph = RoutingGraph(
            nodes = listOf(
                GraphNode(59.0, 10.0),
                GraphNode(59.5, 10.5),
                GraphNode(60.0, 11.0),
            ),
            edges = listOf(
                mutableListOf(),
                mutableListOf(),
                mutableListOf(),
            ),
        )

        val idx = Dijkstra.findNearestNode(graph, 59.0, 10.0, maxMeters = 50000.0)
        assertEquals("Should find index 0", 0, idx)
    }
}

/**
 * Tests for CoordinateUtils.
 */
class CoordinateUtilsTest {

    @Test
    fun `haversineKm returns approximately correct distance`() {
        // Oslo → Bergen ~300km
        val d = CoordinateUtils.haversineKm(59.9, 10.7, 60.3, 5.3)
        assertEquals("Oslo→Bergen should be ~300km", 300.0, d, 10.0)
    }

    @Test
    fun `haversineM returns meters`() {
        val d = CoordinateUtils.haversineM(59.9, 10.7, 59.9, 10.7)
        assertEquals("Same point should be 0m", 0.0, d, 0.001)
    }

    @Test
    fun `lonLatToMercator transforms correctly`() {
        val (x, y) = CoordinateUtils.lonLatToMercator(10.0, 60.0)
        assertEquals("X should be positive for east", true, x > 0)
        assertEquals("Y should be positive for north", true, y > 0)
    }
}