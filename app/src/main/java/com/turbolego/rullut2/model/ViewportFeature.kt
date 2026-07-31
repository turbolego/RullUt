package com.turbolego.rullut2.model

import com.turbolego.rullut2.model.FeatureInfo

/**
 * A feature discovered during a viewport grid scan, enriched with
 * metadata derived from its WMS properties and computed distance
 * from the map centre.
 *
 * @property featureId   Unique OGC FID (e.g. "topo.12345").
 * @property layerName   WMS layer that owns the feature (e.g. "tilgjengelighet3").
 * @property objId       Dédoublonnage ID from the `objid` or `lokalid` property.
 * @property byggtype    Building / feature type code (from `byggtype` prop).
 * @property name        Feature display name (from `navn`, `navnerom`, or fallback).
 * @property accessibility  Accessibility label (from `tilgjengelighet` prop).
 * @property distanceFromCentre  Haversine distance (metres) to the map viewport centre.
 * @property centreX     EPSG:3857 X coordinate (used for camera navigation).
 * @property centreY     EPSG:3857 Y coordinate (used for camera navigation).
 * @property rawProps    All remaining key–value properties for display.
 */
data class ViewportFeature(
    val featureId: String,
    val layerName: String,
    val objId: String,
    val byggtype: String,
    val name: String,
    val accessibility: String,
    val distanceFromCentre: Double,
    val centreX: Double,
    val centreY: Double,
    val rawProps: Map<String, String> = emptyMap(),
) {
    /** Human-readable summary of the accessibility value. */
    val accessibilitySummary: String
        get() = when (accessibility.lowercase().trim()) {
            "ja", "yes", "true", "1", "tilgjengelig" -> "Tilgjengelig"
            "nei", "no", "false", "0", "ikke tilgjengelig" -> "Ikke tilgjengelig"
            "delvis", "partial", "begrenset" -> "Delvis tilgjengelig"
            else -> accessibility.ifBlank { "Ukjent" }
        }

    /** Type label combining layer name and building type. */
    val typeLabel: String
        get() = if (byggtype.isNotBlank()) {
            "$layerName / $byggtype"
        } else {
            layerName
        }

    companion object {
        /**
         * Factory: build a [ViewportFeature] from a parsed [FeatureInfo] and
         * the EPSG:3857 centre coordinates for distance computation.
         */
        fun fromFeatureInfo(
            feature: FeatureInfo,
            centreX3857: Double,
            centreY3857: Double,
        ): ViewportFeature {
            val props = feature.props

            // Determine deduplication key: prefer objid, fall back to lokalid, then featureId.
            val fallbackId = feature.featureId.ifBlank {
                "${feature.layerName}:${centreX3857}:${centreY3857}:${props.hashCode()}"
            }

            val objId = props["objid"]
                ?.takeIf { it.isNotBlank() }
                ?: props["lokalid"]
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackId

            val byggtype = props["byggtype"]?.takeIf { it.isNotBlank() } ?: ""
            val name = props["navn"]?.takeIf { it.isNotBlank() }
                ?: props["navnerom"]?.takeIf { it.isNotBlank() }
                ?: props["tittel"]?.takeIf { it.isNotBlank() }
                ?: feature.featureId

            val accessibility = props["tilgjengelighet"]?.takeIf { it.isNotBlank() } ?: ""

            // Parse EPSG:3857 centre coordinates from properties
            // (feature members from WMS GetFeatureInfo typically include
            //  geometry elements; we use the first available coordinate pair).
            val centreX = props["x_3857"]?.toDoubleOrNull()
                ?: props["east"]?.toDoubleOrNull()
                ?: centreX3857

            val centreY = props["y_3857"]?.toDoubleOrNull()
                ?: props["north"]?.toDoubleOrNull()
                ?: centreY3857

            // Compute rough Euclidean distance in EPSG:3857 metres
            val dx = centreX - centreX3857
            val dy = centreY - centreY3857
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

            return ViewportFeature(
                featureId = feature.featureId,
                layerName = feature.layerName,
                objId = objId,
                byggtype = byggtype,
                name = name,
                accessibility = accessibility,
                distanceFromCentre = distance,
                centreX = centreX,
                centreY = centreY,
                rawProps = props,
            )
        }
    }
}
