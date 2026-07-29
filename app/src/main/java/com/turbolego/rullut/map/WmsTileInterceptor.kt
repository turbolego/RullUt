package com.turbolego.rullut.map

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
 * MapLibre Native supports only `{z}/{x}/{y}` tokens in raster tile URLs,
 * NOT the `{bbox-epsg-3857}` token used by `@maplibre/maplibre-react-native`.
 * We set a custom OkHttpClient as MapLibre's HTTP engine that intercepts
 * tile requests matching our dummy `wms-local` host and rewrites them into
 * real Geonorge WMS GetMap URLs with the correct EPSG:3857 bounding box.
 *
 * Usage: Call `WmsInterceptorManager.install()` once after MapLibre.getInstance().
 */
object WmsInterceptorManager {

    // EPSG:3857 / Web Mercator constants
    private const val ORIGIN_X = -20037508.342789244
    private const val ORIGIN_Y = 20037508.342789244
    private const val MAP_SIZE = 40075016.685578488

    /** The dummy host used in MapConfig.WMS_TILE_PATTERN. */
    private val dummyHost = MapConfig.WMS_TILE_PATTERN
        .toHttpUrlOrNull()
        ?.host ?: "wms-local"

    /**
     * Install the interceptor by registering a custom OkHttpClient
     * with MapLibre's HTTP engine.
     */
    fun install() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TileInterceptor())
            .build()

        HttpRequestUtil.setOkHttpClient(client)
    }

    /**
     * OkHttp interceptor that rewrites wms-local tile URLs to real WMS URLs.
     */
    private class TileInterceptor : okhttp3.Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()

            // Only intercept requests to our dummy WMS host
            if (!url.contains(dummyHost)) {
                return chain.proceed(request)
            }

            try {
                val newUrl = transformWmsTileUrl(url)
                val newRequest = Request.Builder()
                    .url(newUrl)
                    .header("User-Agent", MapConfig.USER_AGENT)
                    .build()
                return chain.proceed(newRequest)
            } catch (e: Exception) {
                // If transformation fails, proceed with original (will 404)
                return chain.proceed(request)
            }
        }
    }

    /**
     * Transform a dummy tile URL into a real Geonorge WMS GetMap URL.
     *
     * Input:  "https://wms-local/tiles/{z}/{x}/{y}"
     *                  → MapLibre fills in → "https://wms-local/tiles/5/15/10"
     * Output: "https://wms.geonorge.no/...&BBOX=...,...,...,..."
     */
    fun transformWmsTileUrl(dummyUrl: String): String {
        // Extract the path after "/tiles/"
        val tilesPrefix = "/tiles/"
        val tilesIndex = dummyUrl.indexOf(tilesPrefix)
        if (tilesIndex < 0) return dummyUrl

        val segmentPart = dummyUrl.substring(tilesIndex + tilesPrefix.length)
        val parts = segmentPart.split("/")
        if (parts.size != 3) return dummyUrl

        val z = parts[0].toIntOrNull() ?: return dummyUrl
        val x = parts[1].toIntOrNull() ?: return dummyUrl
        val y = parts[2].toIntOrNull() ?: return dummyUrl

        val bbox = tileToBBox(x, y, z)

        return buildString {
            append(MapConfig.WMS_BASE_URL)
            append("?service=WMS")
            append("&request=GetMap")
            append("&version=1.1.1")
            append("&layers=tilgjengelighet3")
            append("&styles=")
            append("&format=image/png")
            append("&transparent=true")
            append("&srs=EPSG:3857")
            append("&width=256")
            append("&height=256")
            append("&bbox=${bbox[0]},${bbox[1]},${bbox[2]},${bbox[3]}")
        }
    }

    /**
     * Convert tile coordinates to EPSG:3857 bounding box.
     * Uses standard Web Mercator math for XYZ tiles.
     */
    private fun tileToBBox(x: Int, y: Int, z: Int): DoubleArray {
        val tileSize = MAP_SIZE / (1 shl z)
        val minX = ORIGIN_X + x * tileSize
        val maxX = minX + tileSize
        val minY = ORIGIN_Y - (y + 1) * tileSize
        val maxY = ORIGIN_Y - y * tileSize
        return doubleArrayOf(minX, minY, maxX, maxY)
    }
}