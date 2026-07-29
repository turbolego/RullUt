package com.turbolego.rullut.api

import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.model.PlaceResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Place search — Kartverket Stedsnavn API (Norwegian place names).
 *
 * API: https://ws.geonorge.no/stedsnavn/v1/navn?sok=<query>
 * Returns matching place names with coordinates and municipality.
 */
object PlaceSearchApi {

    private const val TAG = "PlaceSearchApi"
    private const val TIMEOUT_MS = 10_000L
    private const val MAX_RESULTS = 15

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Search for places matching a query string.
     * Minimum 3 characters (API requirement).
     */
    suspend fun search(query: String): List<PlaceResult> {
        if (query.length < 3) return emptyList()

        val url = buildString {
            append(MapConfig.PLACES_SEARCH_URL)
            append("?sok=${java.net.URLEncoder.encode(query, "UTF-8")}")
            append("&utkoordsys=4258") // WGS 84
            append("&treffPerSide=$MAX_RESULTS")
            append("&side=1")
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MapConfig.USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val json = response.body?.string() ?: return emptyList()
            return parseResponse(json)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * Parse Kartverket stedsnavn API response.
     * JSON structure:
     * {
     *   "stedsnavn": [{
     *     "skrivemaaate": [{"skrivemaaate": "..."}],
     *     "kommuner": [{"kommuneNavn": "..."}],
     *     "representasjonspunkt": {"nord": 123.4, "aust": 123.4}
     *   }]
     * }
     */
    private fun parseResponse(json: String): List<PlaceResult> {
        val results = mutableListOf<PlaceResult>()
        try {
            val root = JSONObject(json)
            val navnArray = root.optJSONArray("stedsnavn") ?: return emptyList()

            for (i in 0 until navnArray.length()) {
                val item = navnArray.getJSONObject(i)

                // Name (first skrivemate)
                val name = item.optJSONArray("skrivemaaater")
                    ?.optJSONObject(0)
                    ?.optString("skrivemaaate", "")
                    ?: ""

                // Municipality
                val kommune = item.optJSONArray("kommuner")
                    ?.optJSONObject(0)
                    ?.optString("kommuneNavn", "")
                    ?: ""

                // Coordinates
                val point = item.optJSONObject("representasjonspunkt")
                    ?: continue
                val lat = point.optDouble("nord", 0.0)
                val lon = point.optDouble("aust", 0.0)
                if (lat == 0.0 || lon == 0.0) continue

                results.add(PlaceResult(
                    name = name,
                    municipality = kommune,
                    lat = lat,
                    lon = lon,
                ))
            }
        } catch (_: Exception) {
        }
        return results
    }
}