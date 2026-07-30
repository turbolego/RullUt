package com.turbolego.rullut2.api

import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.ToiletResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Toilet search via Geonorge WFS (Web Feature Service).
 *
 * Queries the `app:Toalett` feature type in the Tilgjengelighet WFS
 * and returns nearest-first results with distance and accessibility
 * information (rampe, breddeInngang, kontrastInngang).
 *
 * This replaces the earlier Overpass-based implementation — the toilet
 * data is from Geonorge's Tilgjengelighet datasett, not OpenStreetMap.
 * The WFS layer is the authoritative Norwegian government source for
 * accessibility-assessed public toilets.
 *
 * WFS endpoint: https://wfs.geonorge.no/skwms1/wfs.tilgjengelighet
 * Feature type: app:Toalett
 */
object ToiletSearchApi {

    private const val TAG = "ToiletSearchApi"
    private const val SEARCH_RADIUS = 2000 // meters
    private const val TIMEOUT_MS = 15_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Find accessible toilets near a location via WFS.
     * Returns list sorted by distance ascending.
     */
    suspend fun findNearestToilets(
        lat: Double,
        lon: Double,
        limit: Int = 10,
    ): List<ToiletResult> = withContext(Dispatchers.IO) {
        val bbox = buildBbox(lat, lon, SEARCH_RADIUS.toDouble())
        val url = buildWfsUrl(bbox)

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MapConfig.USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "WFS returned ${response.code}")
                return@withContext emptyList()
            }

            val xml = response.body?.string() ?: return@withContext emptyList()
            parseWfsResponse(xml, lat, lon, limit)
        } catch (e: Exception) {
            Log.w(TAG, "WFS request failed", e)
            emptyList()
        }
    }

    /**
     * Build the WFS GetFeature URL with bounding box filter.
     */
    private fun buildWfsUrl(bbox: String): String {
        return (
            MapConfig.WFS_BASE_URL +
                "?service=WFS" +
                "&version=2.0.0" +
                "&request=GetFeature" +
                "&typeNames=app:Toalett" +
                "&srsName=EPSG:4326" +
                "&bbox=$bbox" +
                "&count=50"
            )
    }

    /**
     * Parse WFS GML response and extract toilet features.
     *
     * Expected GML structure:
     * <wfs:FeatureCollection>
     *   <wfs:member>
     *     <app:Toalett>
     *       <app:navn>…</app:navn>                      (optional)
     *       <app:byggtype>Toalett</app:byggtype>
     *       <app:geometri>
     *         <gml:Point>
     *           <gml:pos>lon lat</gml:pos>
     *         </gml:Point>
     *       </app:geometri>
     *       <app:rampe>                                   (optional)
     *         <app:Rampe>
     *           <app:rampe>Ja/Nei</app:rampe>
     *           <app:rampeTilgjengelig>Tilgjengelig/…</app:rampeTilgjengelig>
     *         </app:Rampe>
     *       </app:rampe>
     *       <app:breddeInngang>88</app:breddeInngang>     (cm)
     *       <app:kontrastInngang>God/Mindre god</app:kontrastInngang>
     *       <app:trapp>…</app:trapp>
     *     </app:Toalett>
     *   </wfs:member>
     * </wfs:FeatureCollection>
     */
    private fun parseWfsResponse(
        xml: String,
        refLat: Double,
        refLon: Double,
        limit: Int,
    ): List<ToiletResult> {
        val results = mutableListOf<ToiletResult>()

        // Find all <app:Toalett> blocks using simple string matching
        val toiletRegex = Regex(
            """<app:Toalett[^>]*>(.*?)</app:Toalett>""",
            RegexOption.DOT_MATCHES_ALL,
        )

        for (match in toiletRegex.findAll(xml)) {
            val block = match.value

            // Extract coordinates from <gml:pos>lon lat</gml:pos>
            val posMatch = Regex(
                """<gml:pos[^>]*>([\d.\-]+)\s+([\d.\-]+)</gml:pos>"""
            ).find(block) ?: continue

            val wgsLon = posMatch.groupValues[1].toDoubleOrNull() ?: continue
            val wgsLat = posMatch.groupValues[2].toDoubleOrNull() ?: continue

            // Extract optional name
            val name = Regex("""<app:navn[^>]*>([^<]+)</app:navn>""")
                .find(block)?.groupValues?.get(1)?.trim()
                ?: "Toalett"

            // Extract accessibility properties
            val rampAccessible = Regex(
                """<app:rampeTilgjengelig[^>]*>([^<]+)</app:rampeTilgjengelig>"""
            ).find(block)?.groupValues?.get(1)
            val rampWidth = Regex(
                """<app:rampeBredde[^>]*>([^<]+)</app:rampeBredde>"""
            ).find(block)?.groupValues?.get(1)
            val entranceWidth = Regex(
                """<app:breddeInngang[^>]*>([^<]+)</app:breddeInngang>"""
            ).find(block)?.groupValues?.get(1)
            val entranceContrast = Regex(
                """<app:kontrastInngang[^>]*>([^<]+)</app:kontrastInngang>"""
            ).find(block)?.groupValues?.get(1)
            val stairs = Regex(
                """<app:trapp>([^<]+)</app:trapp>"""
            ).find(block)?.groupValues?.get(1)

            val accessibilityNotes = buildAccessibilitySummary(
                rampAccessible = rampAccessible,
                rampWidth = rampWidth,
                entranceWidth = entranceWidth,
                entranceContrast = entranceContrast,
                stairs = stairs,
            )

            val distanceKm = CoordinateUtils.haversineKm(refLat, refLon, wgsLat, wgsLon)

            results.add(ToiletResult(
                lat = wgsLat,
                lon = wgsLon,
                name = name,
                distanceKm = distanceKm,
                accessibilityNotes = accessibilityNotes,
            ))
        }

        return results
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * Build a human-readable accessibility summary from WFS properties.
     */
    private fun buildAccessibilitySummary(
        rampAccessible: String?,
        rampWidth: String?,
        entranceWidth: String?,
        entranceContrast: String?,
        stairs: String?,
    ): String {
        val parts = mutableListOf<String>()

        if (rampAccessible != null) {
            parts.add("Rampe: $rampAccessible")
            if (rampWidth != null) parts.add("bredde: ${rampWidth}cm")
        }
        if (entranceWidth != null) {
            parts.add("Inngang: ${entranceWidth}cm")
        }
        if (entranceContrast != null) {
            parts.add("Kontrast: $entranceContrast")
        }
        if (stairs != null) {
            parts.add("Trapp: $stairs")
        }

        return parts.joinToString(", ")
    }

    /**
     * Build a bounding box string from a center point and radius in meters.
     */
    private fun buildBbox(lat: Double, lon: Double, radiusM: Double): String {
        val latDelta = radiusM / 111_320.0
        val lonDelta = radiusM / (111_320.0 * Math.cos(Math.toRadians(lat)))
        val minLat = lat - latDelta
        val maxLat = lat + latDelta
        val minLon = lon - lonDelta
        val maxLon = lon + lonDelta
        return "$minLat,$minLon,$maxLat,$maxLon"
    }
}
