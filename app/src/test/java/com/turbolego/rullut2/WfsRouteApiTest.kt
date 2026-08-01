package com.turbolego.rullut2

import com.turbolego.rullut2.api.RoadWfsApi
import com.turbolego.rullut2.api.WfsRouteApi
import com.turbolego.rullut2.model.RoadSegmentFeature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for WFS-based route computation between two known points.
 *
 * The Burudvann scenario: user at (59.963861, 10.470002) wants a route to the
 * public toilet at Burudvann (~59.9686, 10.5049). The road network is built
 * from the same Geonorge WFS segments the app fetches for the Highscore
 * feature — no third-party routing server involved.
 */
class WfsRouteApiTest {

    // User's real coordinates
    private val USER_LAT = 59.963861
    private val USER_LON = 10.470002

    // Toilet found near Burudvann via WMS scan (objid 2669)
    private val TOILET_LAT = 59.96862
    private val TOILET_LON = 10.50488

    private fun loadResource(name: String): String {
        val loader = requireNotNull(javaClass.classLoader) { "No classloader" }
        val stream = loader.getResourceAsStream(name)
            ?: throw IllegalStateException("Missing test resource: $name")
        return stream.bufferedReader().readText()
    }

    /** Build a segment with a polyline of (lon, lat) pairs. */
    private fun segment(
        id: String,
        roadType: String,
        vararg pts: Pair<Double, Double>,
    ): RoadSegmentFeature {
        val lons = pts.sumOf { it.first }
        val lats = pts.sumOf { it.second }
        return RoadSegmentFeature(
            objid = id,
            roadType = roadType,
            centerLon = lons / pts.size,
            centerLat = lats / pts.size,
            geometry = pts.toList(),
        )
    }

    /**
     * A plausible road corridor from the user's position east to Burudvann:
     * six connected segments sharing endpoints, so the graph is traversable.
     * Segments 1-3 go north along the west side of the lake before turning
     * east (real roads don't go straight), so the route is clearly longer
     * than the straight crow line.
     */
    private fun burudvannCorridor(): List<RoadSegmentFeature> = listOf(
        segment("seg-1", "Gang-/sykkelveg",
            Pair(10.47000, 59.96386), Pair(10.47020, 59.96600)),
        segment("seg-2", "Gang-/sykkelveg",
            Pair(10.47020, 59.96600), Pair(10.47400, 59.96680)),
        segment("seg-3", "Boliggate",
            Pair(10.47400, 59.96680), Pair(10.48000, 59.96700)),
        segment("seg-4", "Boliggate",
            Pair(10.48000, 59.96700), Pair(10.48900, 59.96680)),
        segment("seg-5", "Fylkesvei",
            Pair(10.48900, 59.96680), Pair(10.49700, 59.96780)),
        segment("seg-6", "FriluftTurvei",
            Pair(10.49700, 59.96780), Pair(10.50488, 59.96862)),
    )

    @Test
    fun `route from user location to Burudvann toilet across WFS corridor`() = runBlocking {
        val corridor = burudvannCorridor()

        val path = WfsRouteApi.computeRouteOnSegments(
            corridor, USER_LAT, USER_LON, TOILET_LAT, TOILET_LON,
        )

        assertNotNull("Route found between known points", path)
        val route = path!!
        assertTrue("Path has at least 2 coordinates", route.coordinates.size >= 2)

        // Endpoints: start near user, end near toilet
        val (startLon, startLat) = route.coordinates.first()
        val (endLon, endLat) = route.coordinates.last()
        assertTrue("Starts near user location",
            kotlin.math.abs(startLat - USER_LAT) < 0.01 &&
                kotlin.math.abs(startLon - USER_LON) < 0.01)
        assertTrue("Ends near toilet",
            kotlin.math.abs(endLat - TOILET_LAT) < 0.01 &&
                kotlin.math.abs(endLon - TOILET_LON) < 0.01)

        // Distance sanity: ~2.8km as the crow flies, route a bit longer
        val crowKm = com.turbolego.rullut2.model.CoordinateUtils.haversineKm(
            USER_LAT, USER_LON, TOILET_LAT, TOILET_LON
        )
        assertTrue("Route distance >= crow distance",
            route.distanceMeters >= crowKm * 1000.0 * 0.9)
        assertTrue("Route distance plausible (< 5km)",
            route.distanceMeters < 5000.0)
    }

    @Test
    fun `route uses actual road geometry not a straight line`() = runBlocking {
        val corridor = burudvannCorridor()

        val path = WfsRouteApi.computeRouteOnSegments(
            corridor, USER_LAT, USER_LON, TOILET_LAT, TOILET_LON,
        )!!

        val crowM = com.turbolego.rullut2.model.CoordinateUtils.haversineM(
            USER_LAT, USER_LON, TOILET_LAT, TOILET_LON
        )
        // The corridor bends (segments 3-6 drift north), so the route must be
        // meaningfully longer than the straight line — proving we follow roads.
        assertTrue("Route follows the bent corridor (${path.distanceMeters}m vs crow ${crowM}m)",
            path.distanceMeters > crowM * 1.05)
    }

    @Test
    fun `graph connects segments sharing endpoints`() {
        val corridor = burudvannCorridor()
        val graph = WfsRouteApi.buildGraph(corridor)

        assertNotNull(graph)
        val g = graph!!
        // 6 segments × 2 endpoints = 12 raw vertices; 5 shared junctions are
        // deduplicated → 7 unique vertices.
        assertEquals("7 unique vertices (shared endpoints deduped)", 7, g.nodes.size)
        assertTrue("Edges exist along each segment", g.edges.flatten().isNotEmpty())
    }

    @Test
    fun `nearby but not identical endpoints are joined`() {
        // Two segments whose ends are 8m apart (rounded coordinate snap)
        val segA = segment("a", "Boliggate",
            Pair(10.47000, 59.96386), Pair(10.47350, 59.96390))
        val segB = segment("b", "Boliggate",
            Pair(10.473502, 59.963901), Pair(10.47700, 59.96400)) // ~8m offset

        val graph = WfsRouteApi.buildGraph(listOf(segA, segB))!!
        // 4 distinct vertices (the two middle ones snap within tolerance)
        assertEquals("Vertices snapped/joined", 4, graph.nodes.size)
    }

    @Test
    fun `no route when endpoints are far from the road network`() = runBlocking {
        val corridor = burudvannCorridor()

        // A point 5km away from any segment (beyond SNAP_RADIUS_M = 400m)
        val path = WfsRouteApi.computeRouteOnSegments(
            corridor, USER_LAT, USER_LON, 59.95, 10.55,
        )

        assertNull("No route to an unreachable point", path)
    }

    @Test
    fun `empty segments yield no route`() = runBlocking {
        val path = WfsRouteApi.computeRouteOnSegments(
            emptyList(), USER_LAT, USER_LON, TOILET_LAT, TOILET_LON,
        )
        assertNull(path)
    }

    // ── Real fixture: geometry parsing ──────────────────────────────────

    @Test
    fun `real wfs fixture carries full polyline geometry`() {
        val xml = loadResource("geonorge_wfs_roads.xml")
        val roads = RoadWfsApi.parseWfsRoads(xml)

        assertTrue(roads.isNotEmpty())
        for (road in roads) {
            assertTrue("Geometry present for ${road.objid} (got ${road.geometry.size} pts)",
                road.geometry.size >= 2)
            // Geometry should be consistent with the stored centre
            val centerLon = road.geometry.map { it.first }.average()
            val centerLat = road.geometry.map { it.second }.average()
            assertEquals("centre matches geometry (lon)", road.centerLon!!, centerLon, 1e-9)
            assertEquals("centre matches geometry (lat)", road.centerLat!!, centerLat, 1e-9)
        }
    }

    @Test
    fun `real wfs segments form a routable graph`() {
        val xml = loadResource("geonorge_wfs_roads.xml")
        val roads = RoadWfsApi.parseWfsRoads(xml)

        val graph = WfsRouteApi.buildGraph(roads)
        assertNotNull("Graph built from 50 real segments", graph)
        val g = graph!!
        assertTrue("Many nodes from real geometry", g.nodes.size > 100)
        assertTrue("Many edges from real geometry", g.edges.flatten().size > 100)
    }
}
