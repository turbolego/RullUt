package com.turbolego.rullut2

import com.turbolego.rullut2.api.HighscoreAssetApi
import com.turbolego.rullut2.api.buildHighscore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the bundled highscore data loader — the Android equivalent of
 * the web app's `loadHighscoreFromFile` (main.js). The highscore.dat asset
 * format is: JSON array of { "p": {...}, "x": <EPSG:3857>, "y": <EPSG:3857> }.
 */
class HighscoreAssetApiTest {

    private val sampleJson = """
        [
          {
            "p": {
              "kommune": "1824",
              "gatetype": "Gangfelt",
              "bredde": "299",
              "stigning": "1.1",
              "segmentlengde": "72.6",
              "tilgjengvurderingElRull": "Tilgjengelig",
              "tilgjengvurderingSyn": "Tilgjengelig",
              "tilgjengvurderingRulleAuto": "Tilgjengelig",
              "tilgjengvurderingRulleMan": "Tilgjengelig",
              "veitype": "Gangfelt",
              "dekkeTilstand": "Jevnt"
            },
            "x": 1165665.77,
            "y": 8291476.69
          },
          {
            "p": {
              "kommune": "3201",
              "gatetype": "Fortau",
              "bredde": "350",
              "stigning": "2.4",
              "segmentlengde": "1800.5",
              "veitype": "Fortau"
            },
            "x": 1165665.77,
            "y": 8291476.69
          }
        ]
    """.trimIndent()

    @Test
    fun `parseHighscoreJson maps props and converts mercator to latlon`() {
        val features = HighscoreAssetApi.parseHighscoreJson(sampleJson)

        assertEquals(2, features.size)

        val first = features[0]
        assertEquals("asset-0", first.objid)
        assertEquals("Gangfelt", first.roadType)
        assertEquals(299.0, first.widthCm!!, 0.001)
        assertEquals(1.1, first.slopePercent!!, 0.001)
        assertEquals(72.6, first.estimatedLengthMetres!!, 0.001)
        assertEquals("1824", first.municipality)
        assertEquals("Jevnt", first.surfaceCondition)

        // x=1165665.77, y=8291476.69 in EPSG:3857 → near lon 10.47, lat 59.96
        assertTrue(first.centerLon!! > 10.0 && first.centerLon!! < 11.0)
        assertTrue(first.centerLat!! > 59.0 && first.centerLat!! < 61.0)

        // Road type falls back to gatetype when veitype missing
        val second = features[1]
        assertEquals("Fortau", second.roadType)
        assertEquals("3201", second.municipality)
    }

    @Test
    fun `parseHighscoreJson skips entries without valid coords`() {
        val broken = """
            [
              { "p": { "veitype": "Gate" } },
              { "p": { "veitype": "Sti" }, "x": 100.0, "y": 200.0 }
            ]
        """.trimIndent()
        val features = HighscoreAssetApi.parseHighscoreJson(broken)
        assertEquals(1, features.size)
        assertEquals("Sti", features[0].roadType)
    }

    @Test
    fun `buildHighscore over asset features produces ranked lists`() {
        val features = HighscoreAssetApi.parseHighscoreJson(sampleJson)
        val result = buildHighscore(features)

        assertNotNull(result)
        assertEquals(2, result.segmentsFound)
        // Longest first: Fortau 1800.5 m > Gangfelt 72.6 m
        assertEquals("Fortau", result.longest.first().roadType)
        assertEquals(1800.5, result.longest.first().feature.estimatedLengthMetres!!, 0.001)
        // Steepest: Fortau 2.4% > Gangfelt 1.1%
        assertEquals("Fortau", result.steepest.first().roadType)
        // Total: (72.6 + 1800.5) m = 1873.1 m = 1.8731 km
        assertEquals(1.8731, result.totalKm, 0.0001)
    }
}
