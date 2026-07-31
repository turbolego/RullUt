package com.turbolego.rullut2.api

import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.RoadSegmentFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Road segment fetch via Geonorge WFS (Web Feature Service).
 *
 * Queries the `app:TettstedVei` and `app:FriluftTurvei` feature types in the
 * Tilgjengelighet WFS with a bbox filter and returns every surveyed road
 * segment inside the viewport, with full attributes (bredde, stigning,
 * segmentLengde, gatetype, dekkeMateriale, kommune) and the real segment
 * geometry.
 *
 * This is the authoritative source for the Highscore feature. It replaces
 * the earlier approach of grid-scanning WMS GetFeatureInfo with 1×1 pixel
 * images, which silently returns nothing on the Geonorge MapServer
 * (sub-pixel geometry is dropped during rasterization).
 *
 * WFS endpoint: https://wfs.geonorge.no/skwms1/wfs.tilgjengelighet
 * Feature types: app:TettstedVei (urban roads), app:FriluftTurvei (trails)
 */
object RoadWfsApi {

    private const val TAG = "RoadWfsApi"
    private const val TIMEOUT_MS = 15_000L
    private const val FEATURE_TYPES = "app:TettstedVei,app:FriluftTurvei"
    private const val MAX_FEATURES = 200

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Fetch all surveyed road segments within the given WGS-84 bounds.
     * Returns a list of [RoadSegmentFeature] (unsorted).
     */
    suspend fun fetchRoadSegments(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): List<RoadSegmentFeature> = withContext(Dispatchers.IO) {
        val bbox = "$minLat,$minLon,$maxLat,$maxLon"
        val url = (
            MapConfig.WFS_BASE_URL +
                "?service=WFS" +
                "&version=2.0.0" +
                "&request=GetFeature" +
                "&typeNames=$FEATURE_TYPES" +
                "&srsName=EPSG:4326" +
                "&bbox=$bbox" +
                "&count=$MAX_FEATURES"
            )

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
            parseWfsRoads(xml)
        } catch (e: Exception) {
            Log.w(TAG, "WFS request failed", e)
            emptyList()
        }
    }

    /**
     * Parse the WFS GML response into [RoadSegmentFeature] objects.
     *
     * Expected GML structure (WFS 2.0):
     * <wfs:FeatureCollection>
     *   <wfs:member>
     *     <app:TettstedVei gml:id="tettstedvei.94804">
     *       <app:identifikasjon>
     *         <app:Identifikasjon>
     *           <app:lokalId>38549347-…</app:lokalId>
     *           <app:navnerom>…</app:navnerom>
     *           <app:versjonId>…</app:versjonId>
     *         </app:Identifikasjon>
     *       </app:identifikasjon>
     *       <app:kommune>5001</app:kommune>
     *       <app:geometri>
     *         <gml:LineString srsName="EPSG:4326">
     *           <gml:posList>lon lat lon lat …</gml:posList>
     *         </gml:LineString>
     *       </app:geometri>
     *       <app:gatetype>Fortau</app:gatetype>
     *       <app:dekkeMateriale>Asfalt</app:dekkeMateriale>
     *       <app:dekkeTilstand>Jevnt</app:dekkeTilstand>
     *       <app:bredde>350</app:bredde>          (cm)
     *       <app:stigning>1.0</app:stigning>      (%)
     *       <app:segmentLengde>72.6</app:segmentLengde>  (m)
     *       <app:tilgjengvurderingRulleMan>…</app:tilgjengvurderingRulleMan>
     *     </app:TettstedVei>
     *   </wfs:member>
     * </wfs:FeatureCollection>
     */
    internal fun parseWfsRoads(xml: String): List<RoadSegmentFeature> {
        val results = mutableListOf<RoadSegmentFeature>()
        val memberRegex = Regex("<wfs:member>(.*?)</wfs:member>", RegexOption.DOT_MATCHES_ALL)
        for (match in memberRegex.findAll(xml)) {
            val block = match.value

            // Skip non-road feature types that might slip in
            if (!block.contains("<app:TettstedVei") && !block.contains("<app:FriluftTurvei")) {
                continue
            }

            // Unique ID (lokalId UUID); skip members without one
            val objid = Regex("<app:lokalId[^>]*>([^<]+)</app:lokalId>")
                .find(block)?.groupValues?.get(1)?.trim() ?: continue

            // Segment geometry: "lon lat lon lat ..." in EPSG:4326
            val posList = Regex("<gml:posList[^>]*>([\\d.\\-\\s]+)</gml:posList>")
                .find(block)?.groupValues?.get(1) ?: continue
            val coords = posList.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
            if (coords.size < 4) continue // need at least one full point pair

            var sumLon = 0.0
            var sumLat = 0.0
            var i = 0
            while (i + 1 < coords.size) {
                sumLon += coords[i]
                sumLat += coords[i + 1]
                i += 2
            }
            val pointCount = coords.size / 2
            val centerLon = sumLon / pointCount
            val centerLat = sumLat / pointCount

            fun tag(name: String): String? =
                Regex("<app:$name[^>]*>([^<]+)</app:$name>")
                    .find(block)?.groupValues?.get(1)?.trim()

            results.add(
                RoadSegmentFeature(
                    objid = objid,
                    sourceLayer = "wfs",
                    roadType = tag("gatetype") ?: "",
                    widthCm = tag("bredde")?.toDoubleOrNull(),
                    slopePercent = tag("stigning")?.toDoubleOrNull(),
                    municipality = tag("kommune") ?: "",
                    surfaceMaterial = tag("dekkeMateriale") ?: "",
                    surfaceCondition = tag("dekkeTilstand") ?: "",
                    comment = tag("kommentar") ?: "",
                    estimatedLengthMetres = tag("segmentLengde")?.toDoubleOrNull(),
                    centerLat = centerLat,
                    centerLon = centerLon,
                )
            )
        }

        return results
    }
}
