package com.turbolego.rullut2.map

import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * Builds MapLibre style components for the WMS overlay.
 *
 * The WMS tile URL uses a dummy host ("wms-local") that [WmsInterceptorManager]
 * rewrites to Geonorge GetMap requests. The selected WMS layer names are retained
 * as a query parameter on that dummy URL, allowing the interceptor to request the
 * same layers the user enabled in Settings.
 */
object MapStyleBuilder {

    const val WMS_SOURCE_ID = "geonorge-wms"
    const val WMS_LAYER_ID = "geonorge-wms-layer"

    /**
     * Builds a raster source for the supplied renderable WMS layers.
     *
     * Callers must only add this source when [layers] is non-empty. Empty layer
     * selections intentionally produce no overlay instead of silently restoring
     * the default WMS layer.
     */
    fun buildRasterSource(layers: Collection<String>): RasterSource {
        val selectedLayers = layers
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .joinToString(",")

        require(selectedLayers.isNotEmpty()) { "At least one WMS layer is required" }

        return RasterSource(
            WMS_SOURCE_ID,
            TileSet(
                "tileset",
                "${MapConfig.WMS_TILE_PATTERN}?layers=$selectedLayers",
            ),
            MapConfig.TILE_SIZE,
        )
    }

    /** Builds the transparent raster layer that is drawn over the active basemap. */
    fun buildRasterLayer(sourceId: String = WMS_SOURCE_ID): RasterLayer {
        return RasterLayer(WMS_LAYER_ID, sourceId)
    }
}
