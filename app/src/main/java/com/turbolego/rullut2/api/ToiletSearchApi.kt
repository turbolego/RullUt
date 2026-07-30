package com.turbolego.rullut2.api

import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.ToiletResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Toilet search via Overpass API.
 * Ported from toilet-search.ts (Expo app).
 *
 * Finds amenity=toilets nodes within a radius and returns
 * nearest-first results with distance from a reference point.
 *
 * Retry chain:
 *   1. Primary Overpass URL
 *   2. Fallback Overpass URL (lz4)
 */
object ToiletSearchApi {

    private const val TAG = "ToiletSearchApi"
    private const val SEARCH_RADIUS = 2000 // meters
    private const val TIMEOUT_MS = 20_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Result wrapper — distinguishes empty (no toilets found)
     * from error (API unreachable).
     */
    data class SearchResult(
        val toilets: List<ToiletResult>,
        val isError: Boolean,
    )

    /**
     * Find accessible toilets near a location.
     * Returns list sorted by distance ascending.
     */
    suspend fun findNearestToilets(
        lat: Double,
        lon: Double,
        limit: Int = 10,
    ): List<ToiletResult> {
        val result = trySearch(lat, lon, limit)
        // If primary failed but we got fallback results, use them
        if (result.toilets.isNotEmpty()) return result.toilets
        return emptyList()
    }

    /**
     * Internal search with retry chain.
     */
    private suspend fun trySearch(
        lat: Double,
        lon: Double,
        limit: Int,
    ): SearchResult {
        val bbox = buildBbox(lat, lon, SEARCH_RADIUS.toDouble())

        // Simplified query — only nodes, avoids timeout on complex queries
        val query = buildString {
            append("[out:json][timeout:15];\n")
            append("(\n")
            append("\"\"\"node[\"amenity\"=\"toilets\"]($bbox);\"\"\"\n")
            append(");\n")
            append("out center;")
        }

        // Try primary URL
        val result = executeQuery(query, MapConfig.OVERPASS_URL, lat, lon, limit)
        if (!result.isError) return result

        // Try fallback URL
        return executeQuery(query, MapConfig.OVERPASS_FALLBACK_URL, lat, lon, limit)
    }

    /**
     * Execute a single Overpass query and parse results.
     */
    private fun executeQuery(
        query: String,
        url: String,
        lat: Double,
        lon: Double,
        limit: Int,
    ): SearchResult {
        try {
            val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
                .toRequestBody(FORM_URLENCODED)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MapConfig.USER_AGENT)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w(TAG, "Overpass $url returned ${response.code}")
                return SearchResult(emptyList(), isError = true)
            }

            val json = response.body?.string() ?: return SearchResult(emptyList(), isError = true)
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return SearchResult(emptyList(), isError = false)

            val results = mutableListOf<ToiletResult>()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val elLat: Double
                val elLon: Double

                when (el.getString("type")) {
                    "node" -> {
                        elLat = el.optDouble("lat", 0.0)
                        elLon = el.optDouble("lon", 0.0)
                    }
                    "way" -> {
                        val center = el.optJSONObject("center") ?: continue
                        elLat = center.optDouble("lat", 0.0)
                        elLon = center.optDouble("lon", 0.0)
                    }
                    else -> continue
                }

                if (elLat == 0.0 && elLon == 0.0) continue

                val tags = el.optJSONObject("tags")
                val name = tags?.optString("name", "")
                    ?: tags?.optString("operator", "")
                    ?: tags?.optString("description", "")
                    ?: "Toalett"

                val distanceKm = CoordinateUtils.haversineKm(lat, lon, elLat, elLon)

                results.add(ToiletResult(
                    lat = elLat,
                    lon = elLon,
                    name = name.ifEmpty { "Toalett" },
                    distanceKm = distanceKm,
                ))
            }

            // Sort by distance, return nearest N
            return SearchResult(
                toilets = results.sortedBy { it.distanceKm }.take(limit),
                isError = false,
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Overpass query failed: ${e.message}")
            return SearchResult(emptyList(), isError = true)
        }
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
