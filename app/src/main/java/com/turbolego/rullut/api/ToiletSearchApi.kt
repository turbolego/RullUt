package com.turbolego.rullut.api

import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.model.CoordinateUtils
import com.turbolego.rullut.model.ToiletResult
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
 * Finds amenity=toilets nodes/ways within a radius and returns
 * nearest-first results with distance from a reference point.
 */
object ToiletSearchApi {

    private const val TAG = "ToiletSearchApi"
    private const val SEARCH_RADIUS = 2000 // meters
    private const val TIMEOUT_MS = 15_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Find accessible toilets near a location.
     * Returns list sorted by distance ascending.
     */
    suspend fun findNearestToilets(
        lat: Double,
        lon: Double,
        limit: Int = 10,
    ): List<ToiletResult> {
        val bbox = buildBbox(lat, lon, SEARCH_RADIUS)

        val query = buildString {
            append("[out:json][timeout:15];\n")
            append("(\n")
            append("""node["amenity"="toilets"]($bbox);""")
            append("\n")
            append("""node["amenity"="toilet"]($bbox);""")
            append("\n")
            append("""way["amenity"="toilets"]($bbox);""")
            append("\n")
            append("""way["amenity"="toilet"]($bbox);""")
            append("\n);\n")
            append("out center;")
        }

        try {
            val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
                .toRequestBody(FORM_URLENCODED)

            val request = Request.Builder()
                .url(MapConfig.OVERPASS_URL)
                .header("User-Agent", MapConfig.USER_AGENT)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val json = response.body?.string() ?: return emptyList()
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return emptyList()

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
            return results
                .sortedBy { it.distanceKm }
                .take(limit)
        } catch (_: Exception) {
            return emptyList()
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