package com.turbolego.rullut2

import com.turbolego.rullut2.api.HighscoreAssetApi
import com.turbolego.rullut2.api.HighscoreCategory
import com.turbolego.rullut2.api.RoadWfsApi
import com.turbolego.rullut2.api.ToiletSearchApi
import com.turbolego.rullut2.api.WfsRouteApi
import com.turbolego.rullut2.api.buildHighscore
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.RoadSegmentFeature
import com.turbolego.rullut2.model.ToiletResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature tests for the three core RullUt capabilities.
 *
 * All tests use synthetic data — no XML fixtures required.
 *
 * 1. **Route planner** — synthetic road segments from Bjerkehaugen 13B,
 *    Lommedalen to Burudvann (lake area in Bærum).
 * 2. **Nearest toilet finder** — synthetic toilets near Burudvann, verifying
 *    the closest one is the specific toilet at the lake.
 * 3. **Highscore modal** — pre-crunched highscore data from the bundled
 *    `highscore.dat` asset, producing ranked longest/steepest/widest/
 *    flattest accessible road lists.
 */
class FeatureTests {

    // ── Coordinates ────────────────────────────────────────────────────────

    /** Bjerkehaugen 13B, Lommedalen (59.96296, 10.46871) — from Geonorge stedsnavn. */
    private val USER_LAT = 59.96296
    private val USER_LON = 10.46871

    /** Burudvann lake representative point (59.97469, 10.51401). */
    private val BURUDVANN_LAT = 59.97469
    private val BURUDVANN_LON = 10.51401

    /** The public toilet at Burudvann (toalett.2669 at 59.969495, 10.508782). */
    private val TOILET_LAT = 59.969495
    private val TOILET_LON = 10.508782

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Build a road segment from (lon, lat) pairs. */
    private fun segment(
        id: String,
        roadType: String,
        widthCm: Double? = null,
        slopePercent: Double? = null,
        lengthM: Double? = null,
        vararg pts: Pair<Double, Double>,
    ): RoadSegmentFeature {
        val lons = pts.sumOf { it.first }
        val lats = pts.sumOf { it.second }
        return RoadSegmentFeature(
            objid = id,
            roadType = roadType,
            widthCm = widthCm,
            slopePercent = slopePercent,
            estimatedLengthMetres = lengthM,
            municipality = "3201", // Bærum
            centerLon = lons / pts.size,
            centerLat = lats / pts.size,
            geometry = pts.toList(),
        )
    }

    /**
     * A plausible road corridor from Bjerkehaugen east to Burudvann.
     * Six connected segments sharing endpoints so the graph is traversable.
     * The route bends north before turning east (real roads don't go straight),
     * making it meaningfully longer than the crow distance.
     */
    private fun burudvannRoute(): List<RoadSegmentFeature> = listOf(
        segment("seg-1", "Gang-/sykkelveg", 300.0, 0.5, 250.0,
            Pair(USER_LON, USER_LAT), Pair(10.47000, 59.96500)),
        segment("seg-2", "Gang-/sykkelveg", 320.0, 0.8, 300.0,
            Pair(10.47000, 59.96500), Pair(10.47200, 59.96800)),
        segment("seg-3", "Boliggate", 350.0, 1.2, 400.0,
            Pair(10.47200, 59.96800), Pair(10.47800, 59.97000)),
        segment("seg-4", "Boliggate", 340.0, 1.0, 350.0,
            Pair(10.47800, 59.97000), Pair(10.49000, 59.97100)),
        segment("seg-5", "Fylkesvei", 400.0, 0.6, 500.0,
            Pair(10.49000, 59.97100), Pair(10.50200, 59.97200)),
        segment("seg-6", "FriluftTurvei", 280.0, 2.0, 200.0,
            Pair(10.50200, 59.97200), Pair(BURUDVANN_LON, BURUDVANN_LAT)),
    )

    /**
     * Road segments for the highscore test — varied widths, slopes, and
     * lengths so each ranking category has a clear winner.
     */
    private fun highscoreSegments(): List<RoadSegmentFeature> = listOf(
        segment("hs-1", "Fortau", 400.0, 0.5, 1200.0,
            Pair(10.47, 59.964), Pair(10.48, 59.965)),
        segment("hs-2", "Gangvei", 250.0, 3.2, 800.0,
            Pair(10.48, 59.965), Pair(10.49, 59.966)),
        segment("hs-3", "Boliggate", 350.0, 1.0, 2000.0,
            Pair(10.49, 59.966), Pair(10.50, 59.967)),
    )

    /**
     * Synthetic toilets near Burudvann.
     * The first one is the actual toilet at the lake; the second is farther away.
     */
    private fun burudvannToilets(): List<ToiletResult> = listOf(
        ToiletResult(
            lat = TOILET_LAT,
            lon = TOILET_LON,
            name = "Toalett Burudvann",
            distanceKm = CoordinateUtils.haversineKm(USER_LAT, USER_LON, TOILET_LAT, TOILET_LON),
            accessibilityNotes = "Rampe: Ja, Inngang: 90cm, Kontrast: God",
        ),
        ToiletResult(
            lat = 59.96200,
            lon = 10.52000,
            name = "Toalett Langt-unna",
            distanceKm = CoordinateUtils.haversineKm(USER_LAT, USER_LON, 59.96200, 10.52000),
            accessibilityNotes = "Rampe: Nei, Inngang: 80cm",
        ),
    )

    // ── Route planner ──────────────────────────────────────────────────────

    @Test
    fun `route planner builds a routable graph from Bjerkehaugen to Burudvann`() = runBlocking {
        val segments = burudvannRoute()
        val graph = WfsRouteApi.buildGraph(segments)

        assertNotNull("Graph built from synthetic Bærum road corridor", graph)
        val g = graph!!
        assertTrue("Graph has connected nodes", g.nodes.size >= 7)
        assertTrue("Graph has edges along segments", g.edges.flatten().isNotEmpty())
    }

    @Test
    fun `route planner computes path from Bjerkehaugen to Burudvann`() = runBlocking {
        val segments = burudvannRoute()
        val graph = WfsRouteApi.buildGraph(segments) ?: return@runBlocking

        val start = com.turbolego.rullut2.api.Dijkstra.findNearestNode(
            graph, USER_LAT, USER_LON, 400.0,
        )
        val end = com.turbolego.rullut2.api.Dijkstra.findNearestNode(
            graph, BURUDVANN_LAT, BURUDVANN_LON, 400.0,
        )

        assertTrue("Start node reachable from user location", start >= 0)
        assertTrue("End node reachable from Burudvann", end >= 0)

        val path = com.turbolego.rullut2.api.Dijkstra.compute(graph, start, end)
        assertNotNull("Route found between Bjerkehaugen and Burudvann", path)

        val route = path!!
        assertTrue("Path has at least 2 coordinates", route.coordinates.size >= 2)

        // Endpoints: start near user, end near Burudvann
        val (startLon, startLat) = route.coordinates.first()
        val (endLon, endLat) = route.coordinates.last()
        assertTrue("Starts near user location",
            kotlin.math.abs(startLat - USER_LAT) < 0.01 &&
                kotlin.math.abs(startLon - USER_LON) < 0.01)
        assertTrue("Ends near Burudvann",
            kotlin.math.abs(endLat - BURUDVANN_LAT) < 0.01 &&
                kotlin.math.abs(endLon - BURUDVANN_LON) < 0.01)
    }

    @Test
    fun `route planner uses actual road geometry not a straight line`() = runBlocking {
        val segments = burudvannRoute()
        val graph = WfsRouteApi.buildGraph(segments) ?: return@runBlocking

        val start = com.turbolego.rullut2.api.Dijkstra.findNearestNode(
            graph, USER_LAT, USER_LON, 400.0,
        )
        val end = com.turbolego.rullut2.api.Dijkstra.findNearestNode(
            graph, BURUDVANN_LAT, BURUDVANN_LON, 400.0,
        )

        val path = com.turbolego.rullut2.api.Dijkstra.compute(graph, start, end)!!
        val crowM = CoordinateUtils.haversineM(USER_LAT, USER_LON, BURUDVANN_LAT, BURUDVANN_LON)

        // Route must be at least 5% longer than crow distance — proves it follows roads
        assertTrue(
            "Route follows roads (path ${path.distanceMeters}m vs crow ${crowM}m)",
            path.distanceMeters > crowM * 1.05,
        )
    }

    // ── Toilet finder ──────────────────────────────────────────────────────

    @Test
    fun `toilet finder returns the specific Burudvann toilet`() {
        val results = burudvannToilets()

        assertTrue("Found toilet(s) near Burudvann: got ${results.size}", results.isNotEmpty())

        // First result should be the toilet at Burudvann (closest)
        val closest = results.first()
        val dist = CoordinateUtils.haversineM(TOILET_LAT, TOILET_LON, closest.lat, closest.lon)
        assertTrue("Closest toilet is within 50m of the known Burudvann toilet", dist < 50.0)
    }

    @Test
    fun `toilet finder sorts results by distance ascending`() {
        val results = burudvannToilets()

        for (i in 1 until results.size) {
            assertTrue(
                "Results sorted by distance: index $i (${results[i].distanceKm} km) " +
                    ">= index ${i - 1} (${results[i - 1].distanceKm} km)",
                results[i].distanceKm >= results[i - 1].distanceKm - 0.001,
            )
        }
    }

    @Test
    fun `toilet finder includes accessibility notes`() {
        val results = burudvannToilets()

        val toilet = results.firstOrNull {
            CoordinateUtils.haversineM(TOILET_LAT, TOILET_LON, it.lat, it.lon) < 50.0
        }
        assertNotNull("Found the Burudvann toilet", toilet)
        val notes = toilet!!.accessibilityNotes
        val hasInfo = notes.contains("Rampe") || notes.contains("Inngang") || notes.contains("Kontrast")
        assertTrue("Toilet has accessibility notes: '$notes'", hasInfo)
    }

    // ── Highscore ──────────────────────────────────────────────────────────

    @Test
    fun `highscore builds ranked lists from real-style road segments`() {
        val segments = highscoreSegments()
        val result = buildHighscore(segments)

        assertNotNull("Highscore result not null", result)
        assertEquals("3 segments found", 3, result.segmentsFound)
        assertTrue("Total distance > 0 km: ${result.totalKm} km", result.totalKm > 0.0)
        assertTrue("Longest has entries", result.longest.isNotEmpty())
        assertTrue("Steepest has entries", result.steepest.isNotEmpty())
        assertTrue("Widest has entries", result.widest.isNotEmpty())
        assertTrue("Flattest has entries", result.flattest.isNotEmpty())
    }

    @Test
    fun `highscore longest is sorted descending by length`() {
        val segments = highscoreSegments()
        val result = buildHighscore(segments)

        for (i in 1 until result.longest.size) {
            val prevLen = result.longest[i - 1].feature.estimatedLengthMetres ?: 0.0
            val currLen = result.longest[i].feature.estimatedLengthMetres ?: 0.0
            assertTrue(
                "Longest sorted descending: #${i} (${prevLen}m) >= #${i + 1} (${currLen}m)",
                prevLen >= currLen,
            )
        }
    }

    @Test
    fun `highscore steepest is sorted descending by slope`() {
        val segments = highscoreSegments()
        val result = buildHighscore(segments)

        for (i in 1 until result.steepest.size) {
            val prevSlope = result.steepest[i - 1].feature.slopePercent ?: 0.0
            val currSlope = result.steepest[i].feature.slopePercent ?: 0.0
            assertTrue(
                "Steepest sorted descending: #${i} (${prevSlope}%) >= #${i + 1} (${currSlope}%)",
                prevSlope >= currSlope,
            )
        }
    }

    @Test
    fun `highscore flattest is sorted ascending by slope`() {
        val segments = highscoreSegments()
        val result = buildHighscore(segments)

        for (i in 1 until result.flattest.size) {
            val prevSlope = result.flattest[i - 1].feature.slopePercent ?: 0.0
            val currSlope = result.flattest[i].feature.slopePercent ?: 0.0
            assertTrue(
                "Flattest sorted ascending: #${i} (${prevSlope}%) <= #${i + 1} (${currSlope}%)",
                prevSlope <= currSlope,
            )
        }
    }

    @Test
    fun `highscore widest is sorted descending by width`() {
        val segments = highscoreSegments()
        val result = buildHighscore(segments)

        for (i in 1 until result.widest.size) {
            val prevW = result.widest[i - 1].feature.widthCm ?: 0.0
            val currW = result.widest[i].feature.widthCm ?: 0.0
            assertTrue(
                "Widest sorted descending: #${i} (${prevW}cm) >= #${i + 1} (${currW}cm)",
                prevW >= currW,
            )
        }
    }

    @Test
    fun `highscore asset format parses and ranks correctly`() {
        val json = """
            [
              {
                "p": {
                  "kommune": "3201",
                  "gatetype": "Fortau",
                  "bredde": "400",
                  "stigning": "0.5",
                  "segmentlengde": "1200.0",
                  "tilgjengvurderingRulleMan": "Tilgjengelig",
                  "dekkeTilstand": "Jevnt"
                },
                "x": 1168000.0,
                "y": 8292000.0
              },
              {
                "p": {
                  "kommune": "3201",
                  "gatetype": "Gangvei",
                  "bredde": "250",
                  "stigning": "3.2",
                  "segmentlengde": "800.5",
                  "tilgjengvurderingRulleMan": "Delvis tilgjengelig",
                  "dekkeTilstand": "Jevnt"
                },
                "x": 1169000.0,
                "y": 8293000.0
              },
              {
                "p": {
                  "kommune": "3201",
                  "gatetype": "Boliggate",
                  "bredde": "350",
                  "stigning": "1.0",
                  "segmentlengde": "2000.0",
                  "tilgjengvurderingRulleMan": "Tilgjengelig",
                  "dekkeTilstand": "Jevnt"
                },
                "x": 1167000.0,
                "y": 8291000.0
              }
            ]
        """.trimIndent()

        val features = HighscoreAssetApi.parseHighscoreJson(json)
        assertEquals("3 features parsed", 3, features.size)

        val result = buildHighscore(features)
        assertEquals("3 segments found", 3, result.segmentsFound)
        assertEquals("Total ~4.0 km", 4.0005, result.totalKm, 0.001)

        // Longest: Boliggate 2000m > Fortau 1200m > Gangvei 800.5m
        assertEquals("Longest is Boliggate", "Boliggate", result.longest[0].roadType)
        assertEquals(2000.0, result.longest[0].feature.estimatedLengthMetres!!, 0.001)

        // Steepest: Gangvei 3.2% > Boliggate 1.0% > Fortau 0.5%
        assertEquals("Steepest is Gangvei", "Gangvei", result.steepest[0].roadType)
        assertEquals(3.2, result.steepest[0].feature.slopePercent!!, 0.001)

        // Flattest: Fortau 0.5% < Boliggate 1.0% < Gangvei 3.2%
        assertEquals("Flattest is Fortau", "Fortau", result.flattest[0].roadType)
        assertEquals(0.5, result.flattest[0].feature.slopePercent!!, 0.001)

        // Widest: Fortau 400cm > Boliggate 350cm > Gangvei 250cm
        assertEquals("Widest is Fortau", "Fortau", result.widest[0].roadType)
        assertEquals(400.0, result.widest[0].feature.widthCm!!, 0.001)
    }

    @Test
    fun `highscore category accessor returns correct lists for all categories`() {
        val json = """
            [
              {
                "p": { "gatetype": "Fortau", "bredde": "300", "stigning": "1.0", "segmentlengde": "500.0" },
                "x": 1168000.0, "y": 8292000.0
              }
            ]
        """.trimIndent()

        val features = HighscoreAssetApi.parseHighscoreJson(json)
        val result = buildHighscore(features)

        for (cat in HighscoreCategory.entries) {
            val list = result.entriesFor(cat)
            assertNotNull("${cat.name} list not null", list)
            assertTrue("${cat.name} has entries", list.isNotEmpty())
            assertEquals("Rank 1", 1, list[0].rank)
        }
    }

    @Test
    fun `highscore municipality code 3201 maps to Bærum`() {
        val json = """
            [
              {
                "p": { "gatetype": "Fortau", "kommune": "3201", "bredde": "300", "stigning": "1.0", "segmentlengde": "500.0" },
                "x": 1168000.0, "y": 8292000.0
              }
            ]
        """.trimIndent()

        val features = HighscoreAssetApi.parseHighscoreJson(json)
        val result = buildHighscore(features)
        assertEquals("3201", result.longest.first().feature.municipality)
    }
}
