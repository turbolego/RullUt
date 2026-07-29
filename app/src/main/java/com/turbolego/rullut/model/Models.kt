package com.turbolego.rullut.model

/**
 * Data models for the RullUt accessibility map app.
 * Type-safe port of the Expo app's TypeScript interfaces.
 */

/**
 * A WMS feature returned by GetFeatureInfo.
 */
data class FeatureInfo(
    val layerName: String,
    val featureId: String,
    val props: Map<String, String>,
    val images: List<String> = emptyList(),
)

/**
 * A layer from the WMS GetCapabilities tree.
 */
data class LayerInfo(
    val name: String,
    val title: String,
    val legendUrl: String? = null,
    val children: List<LayerInfo> = emptyList(),
)

/**
 * Place search result from Kartverket Stedsnavntjeneste.
 */
data class PlaceResult(
    val name: String,
    val municipality: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Result of a GetFeatureInfo request on t_vei_r along a route segment.
 * score: 0=unknown, 1=not accessible, 2=partial, 3=fully accessible
 */
data class AccessibilitySample(
    val score: Int,
    val label: String,
)

/**
 * A segment of a route with accessibility assessment.
 */
data class RouteAccessibilitySegment(
    val range: ClosedFloatingPointRange<Double>, // [startM, endM]
    val score: Int,
    val label: String,
)

/**
 * The final result of a route computation.
 */
data class RouteResult(
    val geojson: String, // GeoJSON FeatureCollection as JSON string
    val distanceMeters: Double,
    val durationSeconds: Double,
    val distanceLabel: String,
    val durationLabel: String,
    val segments: List<RouteAccessibilitySegment>,
    val accessiblePct: Int,
    val partiallyAccessiblePct: Int,
    val notAccessiblePct: Int,
    val unknownPct: Int,
    val routeSource: String, // "wfs", "osm", "valhalla"
)

/**
 * Graph node — one point in the routing graph.
 */
data class GraphNode(val lat: Double, val lon: Double)

/**
 * An edge in the routing graph: from nodeIndex to nodeIndex with weight.
 */
data class GraphEdge(val to: Int, val weight: Double)

/**
 * The routing graph (WFS or OSM).
 */
data class RoutingGraph(
    val nodes: List<GraphNode>,
    val edges: List<List<GraphEdge>>,
    val highwayTagLookup: Map<String, String>? = null,
)

/**
 * Raw compact graph format from the JSON asset.
 */
@kotlinx.serialization.Serializable
data class RawGraph(
    val la: List<Int>,
    val lo: List<Int>,
    val e: List<Int>,
)

/**
 * Result from the Overpass toilet search.
 */
data class ToiletResult(
    val lat: Double,
    val lon: Double,
    val name: String,
    val distanceKm: Double,
)

/**
 * Core map coordinate helpers.
 */
object CoordinateUtils {
    const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).pow(2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return haversineKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    fun lonLatToMercator(lon: Double, lat: Double): Pair<Double, Double> {
        val x = (lon * 20037508.34) / 180.0
        val y = Math.log(Math.tan(((90.0 + lat) * Math.PI) / 360.0)) /
                (Math.PI / 180.0) * (20037508.34 / 180.0)
        return Pair(x, y)
    }
}