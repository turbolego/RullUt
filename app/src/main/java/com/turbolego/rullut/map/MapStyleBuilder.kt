package com.turbolego.rullut.map

import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Builds MapLibre style components for the WMS layer.
 *
 * The WMS tile URL uses a dummy host pattern ("wms-local") that
 * WmsTileInterceptor.kt picks up via OkHttp interceptor. Every tile
 * request to wms-local gets its {z}/{x}/{y} extracted dynamically,
 * the EPSG:3857 bbox computed, and the URL rewritten to a real
 * Geonorge WMS GetMap URL.
 */
object MapStyleBuilder {

    const val WMS_SOURCE_ID = "geonorge-wms"
    const val WMS_LAYER_ID = "geonorge-wms-layer"

    /**
     * Build a RasterSource that points to our dummy URL pattern.
     * MapLibre Native replaces {z}/{x}/{y} with real tile coordinates.
     * WmsTileInterceptor.kt intercepts these requests and rewrites them
     * into real WMS GetMap requests with the correct bounding box.
     */
    fun buildRasterSource(): RasterSource {
        return RasterSource(
            WMS_SOURCE_ID,
            TileSet(
                "tileset",
                // TileSet tiles constructor takes a String (the tile pattern), not List<String>
                MapConfig.WMS_TILE_PATTERN
            ),
            MapConfig.TILE_SIZE
        )
    }

    /**
     * Build a RasterLayer that renders the WMS source.
     * The WMS tiles are transparent PNGs, so we lay them over the basemap.
     */
    fun buildRasterLayer(sourceId: String = WMS_SOURCE_ID): RasterLayer {
        return RasterLayer(WMS_LAYER_ID, sourceId)
    }
}