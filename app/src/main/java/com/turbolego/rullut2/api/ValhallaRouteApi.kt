package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Valhalla routing API wrapper — final fallback in the routing chain.
 * Ported from valhalla-route.ts (Expo app).
 *
 * Uses the public Valhalla instance at valhalla1.openstreetmap.de
 * for free, no-auth pedestrian routing on OSM data.
 */
object ValhallaRouteApi {

    private const val TAG = "ValhallaRouteApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(MapConfig.VALHALLA_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(MapConfig.VALHALLA_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Fetch a pedestrian route via Valhalla.
     * Returns decoded polyline shape + summary, or null on failure.
     */
    suspend fun computeRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Dijkstra.RoutePath? {
        val requestBody = JSONObject().apply {
            put("locations", org.json.JSONArray().apply {
                put(JSONObject().apply { put("lat", fromLat); put("lon", fromLon) })
                put(JSONObject().apply { put("lat", toLat); put("lon", toLon) })
            })
            put("costing", "pedestrian")
            put("directions_options", JSONObject().apply {
                put("units", "kilometers")
            })
        }.toString()

        val url = "${MapConfig.VALHALLA_URL}?json=${java.net.URLEncoder.encode(requestBody, "UTF-8")}"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MapConfig.USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = response.body?.string() ?: return null
            val root = JSONObject(json)
            val trip = root.optJSONObject("trip") ?: return null
            val leg = trip.optJSONArray("legs")?.optJSONObject(0) ?: return null
            val summary = trip.optJSONObject("summary") ?: return null

            val encodedShape = leg.optString("shape", "")
            if (encodedShape.isEmpty()) return null

            val coordinates = decodePolyline(encodedShape)
            if (coordinates.size < 2) return null

            val distanceKm = summary.optDouble("length", 0.0)
            val durationSec = summary.optDouble("time", 0.0)

            // Compute physical distance (Valhalla's summary.length is already km)
            var physDist = 0.0
            for (i in 1 until coordinates.size) {
                val (lng1, lat1) = coordinates[i - 1]
                val (lng2, lat2) = coordinates[i]
                physDist += com.turbolego.rullut2.model.CoordinateUtils.haversineM(lat1, lng1, lat2, lng2)
            }

            return Dijkstra.RoutePath(
                coordinates = coordinates,
                distanceMeters = if (physDist > 0) physDist else distanceKm * 1000.0,
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Decode Google Encoded Polyline to (lng, lat)[].
     * Same algorithm as decodePolyline() in valhalla-route.ts.
     */
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val coords = mutableListOf<Pair<Double, Double>>()
        var idx = 0
        var lat = 0
        var lng = 0

        while (idx < encoded.length) {
            // Latitude
            var shift = 0
            var result = 0
            var byte: Int
            do {
                byte = encoded[idx++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            val dlat = if (result and 1 == 1) (result shr 1).inv() else result shr 1
            lat += dlat

            // Longitude
            shift = 0
            result = 0
            do {
                byte = encoded[idx++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            val dlng = if (result and 1 == 1) (result shr 1).inv() else result shr 1
            lng += dlng

            coords.add(Pair(lng * 1e-6, lat * 1e-6))
        }

        return coords
    }
}