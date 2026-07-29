package com.turbolego.rullut

import android.app.Application
import org.maplibre.android.MapLibre
import com.turbolego.rullut.map.WmsInterceptorManager

class RullUtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre Native must be initialized before any MapView is created.
        MapLibre.getInstance(this)

        // Install the WMS tile URL interceptor.
        // This hooks into MapLibre's native HTTP stack to intercept tile
        // requests matching our dummy `wms-local` host and rewrite them
        // into proper Geonorge WMS GetMap URLs with the correct EPSG:3857
        // bounding box computed from {z}/{x}/{y}.
        WmsInterceptorManager.install()
    }
}