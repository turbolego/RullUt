package com.turbolego.rullut.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.turbolego.rullut.api.*
import com.turbolego.rullut.i18n.Lang
import com.turbolego.rullut.i18n.LanguageManager
import com.turbolego.rullut.i18n.Strings
import com.turbolego.rullut.map.MapConfig
import com.turbolego.rullut.map.MapStyleBuilder
import com.turbolego.rullut.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Main screen — MapLibre map with overlay UI controls.
 *
 * Integrates map, GPS, feature info, settings, routing, search, toilets.
 * All text is localised via [Strings] — switch language in settings.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── Language state ──
    var currentLang by remember { mutableStateOf(Lang.NB) }

    // Load persisted language on first composition
    LaunchedEffect(Unit) {
        val code = LanguageManager.getLanguage(context)
        currentLang = LanguageManager.langFromCode(code)
    }

    // ── Map state ──
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var currentLocation by remember { mutableStateOf<android.location.Location?>(null) }

    var showFeaturePopup by remember { mutableStateOf(false) }
    var featureLoading by remember { mutableStateOf(false) }
    var featureTitle by remember { mutableStateOf("") }
    var featureList by remember { mutableStateOf<List<FeatureInfo>>(emptyList()) }

    var showSettings by remember { mutableStateOf(false) }
    var showRoutePlanner by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showToilets by remember { mutableStateOf(false) }
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }

    var selectedBasemap by remember { mutableStateOf("osm") }
    var activeLayers by remember { mutableStateOf(setOf("tilgjengelighet3")) }

    var layers by remember { mutableStateOf<List<LayerInfo>>(emptyList()) }
    var layersLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Dynamic content description for root Box ──
    val mapContentDescription = Strings.mapContentDescription

    // ── Language change handler ──
    fun switchLanguage(newLang: Lang) {
        currentLang = newLang
        coroutineScope.launch {
            LanguageManager.setLanguage(context, newLang.code)
        }
    }

    // ── Permission launcher ──
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            coroutineScope.launch {
                val loc = withContext(Dispatchers.IO) {
                    LocationService.getCurrentLocation(context)
                }
                currentLocation = loc
                loc?.let {
                    map?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude), 14.0,
                        )
                    )
                }
            }
        }
    }

    // ── Load capabilities on first composition ──
    LaunchedEffect(Unit) {
        try {
            layersLoading = true
            val caps = withContext(Dispatchers.IO) {
                FeatureInfoParser.fetchCapabilities()
            }
            layers = caps
        } catch (_: Exception) { }
        layersLoading = false
    }

    // ── MapView lifecycle ──
    val mapView = remember {
        MapView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            cameraPosition = CameraPosition.Builder()
                .target(LatLng(MapConfig.NORWAY_CENTER_LAT, MapConfig.NORWAY_CENTER_LNG))
                .zoom(MapConfig.NORWAY_ZOOM)
                .build()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Initialize map ──
    LaunchedEffect(Unit) {
        mapView.getMapAsync { nativeMap ->
            map = nativeMap
            nativeMap.setStyle(MapConfig.BASEMAP_LIBERTY) { loadedStyle ->
                style = loadedStyle
                val source = MapStyleBuilder.buildRasterSource()
                loadedStyle.addSource(source)
                val layer = MapStyleBuilder.buildRasterLayer()
                loadedStyle.addLayer(layer)
            }

            // Tap → GetFeatureInfo
            nativeMap.addOnMapClickListener { latLng ->
                coroutineScope.launch {
                    featureLoading = true
                    showFeaturePopup = true
                    featureTitle = Strings.featureLoading

                    try {
                        val features = withContext(Dispatchers.IO) {
                            FeatureInfoApi.queryAllLayers(
                                lat = latLng.latitude,
                                lng = latLng.longitude,
                            )
                        }
                        featureList = features
                        featureTitle = if (features.isNotEmpty()) {
                            features.first().props["tittel"]
                                ?: features.first().props["navn"]
                                ?: "${features.size} treff"
                        } else {
                            Strings.featureNoInfo
                        }
                    } catch (_: Exception) {
                        featureTitle = Strings.featureError
                        featureList = emptyList()
                    }
                    featureLoading = false
                }
            }

            // Long press → announce for TalkBack
            nativeMap.addOnMapLongClickListener { latLng ->
                nativeMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        latLng, nativeMap.cameraPosition.zoom
                    )
                )
                com.turbolego.rullut.a11y.AccessibilityUtils.announce(
                    context, mapContentDescription
                )
            }
        }
    }

    // Content
    Box(modifier = modifier.fillMaxSize()
        .semantics { contentDescription = mapContentDescription }) {

        // Map
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // ── Floating action buttons (bottom-right stack) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // GPS center
            FloatingActionButton(
                onClick = {
                    if (currentLocation != null) {
                        map?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLocation!!.latitude, currentLocation!!.longitude),
                                14.0,
                            )
                        )
                    } else {
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            coroutineScope.launch {
                                val loc = withContext(Dispatchers.IO) {
                                    LocationService.getCurrentLocation(context)
                                }
                                currentLocation = loc
                                loc?.let {
                                    map?.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(it.latitude, it.longitude), 14.0,
                                        )
                                    )
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = Strings.fabLocation },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null)
            }

            Spacer(Modifier.size(8.dp))

            // Search
            SmallFloatingActionButton(
                onClick = { showSearch = true },
                modifier = Modifier.semantics { contentDescription = Strings.fabSearch },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
            }

            Spacer(Modifier.size(8.dp))

            // Toilets
            SmallFloatingActionButton(
                onClick = { showToilets = true },
                modifier = Modifier.semantics { contentDescription = "Finn nærliggende toaletter" },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Default.Wc, contentDescription = null)
            }

            Spacer(Modifier.size(8.dp))

            // Route planner
            SmallFloatingActionButton(
                onClick = { showRoutePlanner = true },
                modifier = Modifier.semantics { contentDescription = Strings.fabRoute },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Default.Route, contentDescription = null)
            }

            Spacer(Modifier.size(8.dp))

            // Settings
            SmallFloatingActionButton(
                onClick = { showSettings = true },
                modifier = Modifier.semantics { contentDescription = Strings.fabSettings },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Default.Layers, contentDescription = null)
            }
        }

        // ── Feature info popup ──
        FeaturePopup(
            visible = showFeaturePopup,
            loading = featureLoading,
            title = featureTitle,
            features = featureList,
            onDismiss = { showFeaturePopup = false },
        )

        // ── Settings panel ──
        SettingsPanel(
            visible = showSettings,
            layers = layers,
            layersLoading = layersLoading,
            activeLayers = activeLayers,
            onLayerToggle = { layer ->
                activeLayers = if (activeLayers.contains(layer)) {
                    activeLayers - layer
                } else {
                    activeLayers + layer
                }
            },
            basemap = selectedBasemap,
            onBasemapChange = { basemap ->
                selectedBasemap = basemap
                val styleUrl = when (basemap) {
                    "topo" -> MapConfig.BASEMAP_TOPO
                    "none" -> null
                    else -> MapConfig.BASEMAP_LIBERTY
                }
                if (styleUrl != null) {
                    map?.setStyle(styleUrl) { loadedStyle ->
                        style = loadedStyle
                        val source = MapStyleBuilder.buildRasterSource()
                        loadedStyle.addSource(source)
                        val layer = MapStyleBuilder.buildRasterLayer()
                        loadedStyle.addLayer(layer)
                    }
                }
            },
            currentLang = currentLang,
            onLanguageChange = { newLang -> switchLanguage(newLang) },
            onDismiss = { showSettings = false },
        )

        // ── Route planner ──
        RoutePlannerModal(
            visible = showRoutePlanner,
            myLocation = currentLocation?.let {
                Pair(it.latitude, it.longitude)
            },
            onRouteRequest = { fromLat, fromLon, toLat, toLon ->
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        RouteEngine.findRoute(
                            context, fromLat, fromLon, toLat, toLon
                        )
                    }
                    routeResult = result
                    if (result != null) {
                        drawRouteOnMap(map, style, result)
                        Toast.makeText(
                            context,
                            "${Strings.routeTitle}: ${result.distanceLabel}, ${result.durationLabel}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            Strings.routeNoRoute,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDismiss = { showRoutePlanner = false },
        )

        // ── Search modal ──
        SearchModal(
            visible = showSearch,
            onSearchPlace = { query ->
                withContext(Dispatchers.IO) {
                    PlaceSearchApi.search(query)
                }
            },
            onSelectPlace = { place ->
                map?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(place.lat, place.lon), 12.0,
                    )
                )
                map?.addMarker(
                    MarkerOptions()
                        .title(place.name)
                        .snippet(place.municipality)
                        .position(LatLng(place.lat, place.lon))
                )
            },
            onDismiss = { showSearch = false },
        )
    }
}

/**
 * Draw a route GeoJSON on the map as a styled line.
 */
private fun drawRouteOnMap(
    map: MapLibreMap?,
    style: Style?,
    result: RouteResult,
) {
    if (map == null || style == null) return

    try {
        style.removeLayer("route-layer")
        style.removeSource("route-source")

        val source = GeoJsonSource("route-source", result.geojson)
        style.addSource(source)

        val layer = LineLayer("route-layer", "route-source").withProperties(
            PropertyFactory.lineColor(android.graphics.Color.parseColor("#E8A020")),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayer(layer)
    } catch (_: Exception) { }
}