package com.turbolego.rullut.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.map.MapStyleBuilder
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

/**
 * Main map screen. Displays an OpenFreeMap basemap with the Geonorge
 * accessibility WMS overlay. Uses MapLibre Native for Android.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberMapView(context)

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription =
                    "Tilgjengelighetskart over Norge. Viser rullestol-ruter og universell utforming."
            }
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Create and configure the MapLibre Native MapView with lifecycle handling.
 */
@Composable
private fun rememberMapView(context: Context): MapView {
    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            cameraPosition = CameraPosition.Builder()
                .target(LatLng(MapConfig.NORWAY_CENTER_LAT, MapConfig.NORWAY_CENTER_LNG))
                .zoom(MapConfig.NORWAY_ZOOM)
                .build()
        }
    }

    DisposableEffect(Unit) {
        mapView.getMapAsync { map ->
            setupMapStyle(map)
        }
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()

        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    return mapView
}

/**
 * Load the OpenFreeMap basemap style, then add the WMS raster source + layer.
 * MapLibre Native Android: raster sources support {z}/{x}/{y} tokens.
 * We use "wms-local" as a dummy host that WmsTileInterceptor rewrites.
 */
private fun setupMapStyle(map: MapLibreMap) {
    // Load the OpenFreeMap Liberty style as the basemap
    map.setStyle(MapConfig.BASEMAP_LIBERTY) { style ->
        // Once the basemap style is loaded, add the WMS raster source
        val wmsSource = MapStyleBuilder.buildWmsRasterSource()
        style.addSource(wmsSource)
        val wmsLayer = MapStyleBuilder.buildWmsRasterLayer(wmsSource.id)
        style.addLayer(wmsLayer)
    }

    // Tap → GetFeatureInfo (Phase 2)
    map.addOnMapClickListener { latLng ->
        // TODO Phase 2: queryFeatureInfoAt(latLng)
    }
}