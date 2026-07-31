package com.turbolego.rullut2

import com.turbolego.rullut2.api.HighscoreCategory
import com.turbolego.rullut2.api.RoadWfsApi
import com.turbolego.rullut2.api.buildHighscore
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the WFS road segment parsing that powers the Highscore feature.
 *
 * Uses a real Geonorge WFS response captured from the Trondheim sentrum area
 * (50 road segments, all with bredde/stigning/segmentLengde).
 */
class RoadWfsApiTest {

    private fun loadResource(name: String): String {
        val loader = requireNotNull(javaClass.classLoader) { "No classloader" }
        val stream = loader.getResourceAsStream(name)
            ?: throw IllegalStateException("Missing test resource: $name")
        return stream.bufferedReader().readText()
    }

    @Test
    fun `parse real geonorge wfs response`() {
        val xml = loadResource("geonorge_wfs_roads.xml")

        val roads = RoadWfsApi.parseWfsRoads(xml)

        assertEquals("Should parse all 50 members", 50, roads.size)
        val first = roads.first()
        assertTrue("objid is a UUID", first.objid.contains("-"))
        assertEquals("roadType", "Fortau", first.roadType)
        assertEquals("municipality", "5001", first.municipality)
        assertEquals("surfaceMaterial", "Asfalt", first.surfaceMaterial)
        assertEquals("surfaceCondition", "Jevnt", first.surfaceCondition)
        assertNotNull("widthCm parsed", first.widthCm)
        assertNotNull("slopePercent parsed", first.slopePercent)
        assertNotNull("length parsed", first.estimatedLengthMetres)
    }

    @Test
    fun `every parsed road has geometry and measurements`() {
        val xml = loadResource("geonorge_wfs_roads.xml")
        val roads = RoadWfsApi.parseWfsRoads(xml)

        assertTrue(roads.isNotEmpty())
        for (road in roads) {
            assertNotNull("centerLat set for ${road.objid}", road.centerLat)
            assertNotNull("centerLon set for ${road.objid}", road.centerLon)
            assertTrue("center in Trondheim area (${road.centerLat}, ${road.centerLon})",
                road.centerLat!! in 63.0..64.0 && road.centerLon!! in 10.0..11.0)
        }
        // Every segment in the capture has all three ranking measurements
        assertTrue(roads.all { it.widthCm != null && it.slopePercent != null && it.estimatedLengthMetres != null })
    }

    @Test
    fun `empty response yields no roads`() {
        assertTrue(RoadWfsApi.parseWfsRoads("<wfs:FeatureCollection></wfs:FeatureCollection>").isEmpty())
        assertTrue(RoadWfsApi.parseWfsRoads("").isEmpty())
    }

    @Test
    fun `non-road feature types are skipped`() {
        val xml = """
            <wfs:FeatureCollection>
              <wfs:member>
                <app:Toalett>
                  <app:lokalId>abc-123</app:lokalId>
                  <app:navn>Toalett</app:navn>
                </app:Toalett>
              </wfs:member>
            </wfs:FeatureCollection>
        """.trimIndent()

        assertTrue(RoadWfsApi.parseWfsRoads(xml).isEmpty())
    }

    // ── End-to-end: WFS roads → highscore ───────────────────────────────

    @Test
    fun `highscore built from real wfs roads has entries`() {
        val xml = loadResource("geonorge_wfs_roads.xml")
        val roads = RoadWfsApi.parseWfsRoads(xml)

        val result = buildHighscore(roads)

        assertEquals("segmentsFound", 50, result.segmentsFound)
        assertTrue("totalKm > 0", result.totalKm > 0.0)
        assertTrue("longest has entries", result.longest.isNotEmpty())
        assertTrue("steepest has entries", result.steepest.isNotEmpty())
        assertTrue("widest has entries", result.widest.isNotEmpty())
        assertTrue("flattest has entries", result.flattest.isNotEmpty())

        // Sorted correctly
        val longestValues = result.longest.map {
            it.feature.estimatedLengthMetres!!
        }
        assertEquals("longest sorted desc",
            longestValues.sortedDescending(), longestValues)

        val flattestValues = result.flattest.map { it.feature.slopePercent!! }
        assertEquals("flattest sorted asc",
            flattestValues.sorted(), flattestValues)

        // Every category exposes its entries via entriesFor
        for (cat in HighscoreCategory.entries) {
            assertTrue("${cat.name} via entriesFor", result.entriesFor(cat).isNotEmpty())
        }
    }
}
