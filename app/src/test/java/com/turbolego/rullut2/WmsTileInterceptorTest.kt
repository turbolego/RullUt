package com.turbolego.rullut2

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.map.WmsInterceptorManager
import org.junit.Assert.assertTrue
import org.junit.Test

class WmsTileInterceptorTest {

    @Test
    fun selectedLayers_areForwardedToTheWmsRequest() {
        val url = WmsInterceptorManager.transformWmsTileUrl(
            "https://wms-local/tiles/5/15/10?layers=t_vei_r,t_rullestol",
        )

        assertTrue(url.contains("&layers=t_vei_r,t_rullestol"))
        assertTrue(url.contains("&srs=EPSG:3857"))
        assertTrue(url.contains("&bbox="))
    }

    @Test
    fun missingLayerParameter_usesTheNamedDefaultLayer() {
        val url = WmsInterceptorManager.transformWmsTileUrl(
            "https://wms-local/tiles/5/15/10",
        )

        assertTrue(url.contains("&layers=${MapConfig.DEFAULT_WMS_RENDER_LAYER}"))
    }
}
