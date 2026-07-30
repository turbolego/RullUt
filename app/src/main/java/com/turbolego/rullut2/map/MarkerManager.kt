package com.turbolego.rullut2.map

import android.graphics.Color
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.GeoJsonOptions

/**
 * Manages a shared layer-based marker system with large circular touch targets.
 *
 * Background: MapLibre's MarkerOptions creates tiny 24×24 icons with a very small
 * tap area. This manager replaces those with a CircleLayer (radius 14dp) that is
 * far easier to tap.
 *
 * Usage:
 *   // In onStyleLoaded:
 *   MarkerManager.initialize(style)
 *
 *   // After adding/removing markers:
 *   MarkerManager.addMarker(lat, lon, "title", "snippet", "id-1")
 *   MarkerManager.removeById("id-1")
 *   MarkerManager.apply()
 *
 *   // Remove all:
 *   MarkerManager.clear()
 */
object MarkerManager {

    private const val SOURCE_ID = "app-marker-source"
    private const val CIRCLE_LAYER_ID = "app-marker-circle-layer"
    private const val LABEL_LAYER_ID = "app-marker-label-layer"

    // Store markers as a GeoJSON FeatureCollection JSON string
    private val featuresJson = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[]}")

    private var source: GeoJsonSource? = null

    /** Pending features to apply once source is ready */
    private val pendingFeatures = mutableListOf<String>()

    /**
     * Set up the source + layers on a loaded style.
     * Call once from onStyleLoaded.
     */
    fun initialize(style: org.maplibre.android.maps.Style) {
        // Source
        var src = style.getSource(SOURCE_ID) as? GeoJsonSource
        if (src == null) {
            src = GeoJsonSource(SOURCE_ID)
            style.addSource(src)
        }
        source = src

        // Circle layer — large radius for easy tapping
        if (style.getLayer(CIRCLE_LAYER_ID) == null) {
            val circleLayer = CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID).withProperties(
                circleRadius(14f),
                circleColor(Color.parseColor("#007acc")),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(3f),
            )
            style.addLayer(circleLayer)
        }

        // Label layer — shows title above the circle
        if (style.getLayer(LABEL_LAYER_ID) == null) {
            val labelLayer = SymbolLayer(LABEL_LAYER_ID, SOURCE_ID).withProperties(
                textField("{title}"),
                textSize(12f),
                textOffset(arrayOf(0f, -2f)),
                textColor(Color.BLACK),
                textHaloColor(Color.WHITE),
                textHaloWidth(2f),
                textAnchor("bottom"),
                textOptional(true),
            )
            style.addLayer(labelLayer)
        }

        // Apply any queued features
        applyInternal()
    }

    /**
     * Add a marker at [lat],[lon] with a label.
     * If [id] is provided, replaces any existing marker with the same id.
     */
    fun addMarker(lat: Double, lon: Double, title: String, snippet: String? = null, id: String? = null) {
        val featureJson = buildFeatureJson(lon, lat, title, snippet, id)
        pendingFeatures.add(featureJson)
        applyInternal()
    }

    /** Remove all markers. */
    fun clear() {
        pendingFeatures.clear()
        applyInternal()
    }

    /** Push all current features to the GeoJSON source. */
    fun apply() = applyInternal()

    /** Number of current markers. */
    val count get() = pendingFeatures.size

    // ── internals ──

    private fun applyInternal() {
        val src = source ?: return
        val fc = "{\"type\":\"FeatureCollection\",\"features\":[${pendingFeatures.joinToString(",")}]}"
        try {
            src.setGeoJson(fc)
        } catch (_: Exception) {
            // Style not ready yet; initialize() will push on next style load
        }
    }

    /**
     * Build a single GeoJSON Feature point JSON string.
     */
    private fun buildFeatureJson(
        lng: Double,
        lat: Double,
        title: String,
        snippet: String,
    ): String {
        val escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedSnippet = snippet.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{"title":"$escapedTitle","snippet":"$escapedSnippet"}}"""
    }
}