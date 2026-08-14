package com.turbolego.rullut2.map

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WMS tile URL interceptor for MapLibre Native Android.
 *
 * MapLibre Native supports only `{z}/{x}/{y}` tokens in raster tile URLs, not
 * the WMS `{bbox-epsg-3857}` token. We therefore route dummy `wms-local` tile
 * URLs through this interceptor, which builds proper Geonorge GetMap requests
 * with a Web Mercator bounding box and the layers selected in Settings.
 */
object WmsInterceptorManager {

    private const val ORIGIN_X = -20037508.342789244
    private const val ORIGIN_Y = 20037508.342789244
    private const val MAP_SIZE = 40075016.685578488

    /** The dummy host used in [MapConfig.WMS_TILE_PATTERN]. */
    private val dummyHost = MapConfig.WMS_TILE_PATTERN
        .toHttpUrlOrNull()
        ?.host ?: "wms-local"

    /** Installs the custom HTTP client once after MapLibre initialization. */
    fun install() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TileInterceptor())
            .build()

        HttpRequestUtil.setOkHttpClient(client)
    }

    /** Rewrites requests made to the dummy WMS host. */
    private class TileInterceptor : okhttp3.Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()

            if (!url.contains(dummyHost)) return chain.proceed(request)

            return try {
                val newUrl = transformWmsTileUrl(url)
                val newRequest = Request.Builder()
                    .url(newUrl)
                    .header("User-Agent", MapConfig.USER_AGENT)
                    .build()
                chain.proceed(newRequest)
            } catch (_: Exception) {
                // Let MapLibre receive the normal failed request if a malformed
                // tile URL somehow reaches this interceptor.
                chain.proceed(request)
            }
        }
    }

    /**
     * Transforms a dummy XYZ tile URL into a Geonorge WMS GetMap URL.
     *
     * The optional `layers` query parameter is passed through from
     * [MapStyleBuilder]. It is intentionally read from the parsed URL rather
     * than hard-coded, so a settings toggle changes the rendered overlay.
     */
    fun transformWmsTileUrl(dummyUrl: String): String {
        val parsedUrl = dummyUrl.toHttpUrlOrNull() ?: return dummyUrl
        val tilesPrefix = "/tiles/"
        val tilesIndex = parsedUrl.encodedPath.indexOf(tilesPrefix)
        if (tilesIndex < 0) return dummyUrl

        val segments = parsedUrl.encodedPath
            .substring(tilesIndex + tilesPrefix.length)
            .split("/")
        if (segments.size != 3) return dummyUrl

        val z = segments[0].toIntOrNull() ?: return dummyUrl
        val x = segments[1].toIntOrNull() ?: return dummyUrl
        val y = segments[2].toIntOrNull() ?: return dummyUrl
        val layers = parsedUrl.queryParameter("layers")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.joinToString(",")
            .orEmpty()
            .ifEmpty { MapConfig.DEFAULT_WMS_RENDER_LAYER }

        val bbox = tileToBBox(x, y, z)
        return buildString {
            append(MapConfig.WMS_BASE_URL)
            append("?service=WMS")
            append("&request=GetMap")
            append("&version=1.1.1")
            append("&layers=$layers")
            append("&styles=")
            append("&format=image/png")
            append("&transparent=true")
            append("&srs=EPSG:3857")
            append("&width=256")
            append("&height=256")
            append("&bbox=${bbox[0]},${bbox[1]},${bbox[2]},${bbox[3]}")
        }
    }

    /** Converts XYZ tile coordinates to an EPSG:3857 bounding box. */
    private fun tileToBBox(x: Int, y: Int, z: Int): DoubleArray {
        val tileSize = MAP_SIZE / (1 shl z)
        val minX = ORIGIN_X + x * tileSize
        val maxX = minX + tileSize
        val minY = ORIGIN_Y - (y + 1) * tileSize
        val maxY = ORIGIN_Y - y * tileSize
        return doubleArrayOf(minX, minY, maxX, maxY)
    }
}
