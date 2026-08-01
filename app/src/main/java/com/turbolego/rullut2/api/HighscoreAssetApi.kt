package com.turbolego.rullut2.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.RoadSegmentFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Loads the pre-generated, static highscore data bundled in the APK
 * (`assets/highscore.dat`, generated from the same Geonorge export used by
 * the GitHub Pages version).
 *
 * This mirrors `loadHighscoreFromFile()` in the web app (main.js): instead of
 * scanning WMS/WFS on every modal open — which is slow and fails when the
 * Geonorge WFS is down — we ship the full dataset in the app and parse it
 * once, caching the result in memory. The highscore modal therefore opens
 * instantly and the data is identical between layer changes.
 *
 * Format (JSON array):
 *   [{ "p": { "kommune": "1824", "gatetype": "Gangfelt", "bredde": "299",
 *             "stigning": "1.1", "segmentlengde": "72.6",
 *             "tilgjengvurderingRulleMan": "Tilgjengelig", ... },
 *      "x": <EPSG:3857>, "y": <EPSG:3857> }]
 */
object HighscoreAssetApi {

    private const val TAG = "HighscoreAssetApi"
    private const val ASSET_FILE = "highscore.dat"

    /** In-memory cache: parsed once, reused for every modal open. */
    private var cachedFeatures: List<RoadSegmentFeature>? = null
    private var cachedResult: HighscoreResult? = null

    /**
     * Load the bundled highscore data. Returns `null` if the asset is
     * missing or unparseable (caller may fall back to a live scan).
     */
    suspend fun load(context: Context): HighscoreResult? = withContext(Dispatchers.IO) {
        cachedResult ?: runCatching {
            val json = context.assets.open(ASSET_FILE)
                .bufferedReader()
                .use { it.readText() }
            val features = parseHighscoreJson(json)
            cachedFeatures = features
            buildHighscore(features).also { cachedResult = it }
        }.getOrElse { err ->
            Log.w(TAG, "Failed to load bundled highscore data", err)
            null
        }
    }

    /**
     * Parse the bundled JSON array into [RoadSegmentFeature]s.
     * `x`/`y` are EPSG:3857 (Web Mercator) — converted to WGS-84 lat/lon.
     */
    internal fun parseHighscoreJson(json: String): List<RoadSegmentFeature> {
        val root = JSONObject("{\"items\": $json}")
        val items = root.getJSONArray("items")
        val out = ArrayList<RoadSegmentFeature>(items.length())

        for (i in 0 until items.length()) {
            val entry = items.getJSONObject(i)
            val p = entry.getJSONObject("p")

            val x = entry.optDouble("x", Double.NaN)
            val y = entry.optDouble("y", Double.NaN)
            if (x.isNaN() || y.isNaN()) continue

            val (lon, lat) = CoordinateUtils.mercatorToLonLat(x, y)

            out.add(
                RoadSegmentFeature(
                    objid = "asset-$i",
                    sourceLayer = "asset",
                    roadType = p.optString("veitype").ifBlank { p.optString("gatetype") },
                    widthCm = p.optDouble("bredde", Double.NaN).let { if (it.isNaN()) null else it },
                    slopePercent = p.optDouble("stigning", Double.NaN).let { if (it.isNaN()) null else it },
                    municipality = p.optString("kommune"),
                    surfaceMaterial = "",
                    surfaceCondition = p.optString("dekkeTilstand"),
                    comment = "",
                    estimatedLengthMetres = p.optDouble("segmentlengde", Double.NaN)
                        .let { if (it.isNaN()) null else it },
                    centerLat = lat,
                    centerLon = lon,
                    geometry = emptyList(),
                )
            )
        }
        return out
    }
}
