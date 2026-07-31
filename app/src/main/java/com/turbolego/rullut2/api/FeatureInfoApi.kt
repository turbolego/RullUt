package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.FeatureInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches WMS GetFeatureInfo for a tapped coordinate.
 *
 * Transforms lat/lng → EPSG:3857 → WMS GetFeatureInfo URL
 * with the same pixel-based query geometry used in the Expo app.
 */
object FeatureInfoApi {

    private const val TAG = "FeatureInfoApi"
    private const val QUERY_WIDTH = 256
    private const val QUERY_HEIGHT = 256
    private const val INFO_FORMAT = "text/plain"
    private const val TIMEOUT_MS = 10_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Query GetFeatureInfo at a given lat/lng on a specific WMS layer.
     * Returns raw text response from the WMS server.
     */
    suspend fun queryFeatureInfo(
        lat: Double,
        lng: Double,
        layer: String = MapConfig.WMS_FEATURE_LAYERS.first(),
        srs: String = "EPSG:3857",
        width: Int = QUERY_WIDTH,
        height: Int = QUERY_HEIGHT,
    ): String {
        val (x, y) = CoordinateUtils.lonLatToMercator(lng, lat)

        // Build the GetMap portion to get a small image
        val mapBbox = "${x - 10},${y - 10},${x + 10},${y + 10}"

        // Pixel position: center of the query image
        val i = width / 2
        val j = height / 2

        val url = buildString {
            append(MapConfig.WMS_BASE_URL)
            append("?service=WMS")
            append("&request=GetFeatureInfo")
            append("&version=1.1.1")
            append("&layers=$layer")
            append("&query_layers=$layer")
            append("&styles=")
            append("&srs=$srs")
            append("&bbox=$mapBbox")
            append("&width=$width")
            append("&height=$height")
            append("&format=image/png")
            append("&transparent=true")
            append("&info_format=$INFO_FORMAT")
            append("&feature_count=10")
            append("&x=$i")
            append("&y=$j")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MapConfig.USER_AGENT)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("GetFeatureInfo HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string() ?: ""
    }

    /**
     * Fetch and parse GetFeatureInfo for all configured layers.
     */
    suspend fun queryAllLayers(
        lat: Double,
        lng: Double,
    ): List<FeatureInfo> {
        val results = mutableListOf<FeatureInfo>()
        for (layer in MapConfig.WMS_FEATURE_LAYERS) {
            try {
                val raw = queryFeatureInfo(lat, lng, layer)
                val parsed = FeatureInfoParser.parseGetFeatureInfo(raw, layer)
                results.addAll(parsed)
            } catch (_: Exception) {
                // Skip layers that error (no data, server timeout, etc.)
            }
        }
        return results
    }

    /**
     * Bbox-based GetFeatureInfo query.
     * Uses a 1×1 pixel image covering the full bbox to harvest all features.
     * Returns raw text/plain GML response.
     */
    suspend fun fetchBboxFeatures(
        bboxMinX: Double,
        bboxMinY: Double,
        bboxMaxX: Double,
        bboxMaxY: Double,
        queryLayers: String = MapConfig.WMS_FEATURE_LAYERS.first(),
        featureCount: Int = 50,
    ): String {
        val bbox = "${listOf(bboxMinX, bboxMinY, bboxMaxX, bboxMaxY).joinToString(",")}"

        val url = buildString {
            append(MapConfig.WMS_BASE_URL)
            append("?service=WMS")
            append("&request=GetFeatureInfo")
            append("&version=1.1.1")
            append("&layers=$queryLayers")
            append("&query_layers=$queryLayers")
            append("&styles=")
            append("&srs=EPSG:3857")
            append("&crs=EPSG:3857")
            append("&bbox=$bbox")
            append("&width=256")
            append("&height=256")
            append("&x=128")
            append("&y=128")
            append("&format=image/png")
            append("&transparent=true")
            append("&feature_count=$featureCount")
            append("&info_format=text/plain")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MapConfig.USER_AGENT)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("GetFeatureInfo HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string() ?: ""
    }
}