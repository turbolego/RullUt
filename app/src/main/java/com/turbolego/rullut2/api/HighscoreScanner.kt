package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.RoadSegmentFeature
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the map viewport for road segments and builds the highscore
 * data structures used by [HighscoreModal].
 *
 * This is a simplified implementation that reuses the existing
 * [ViewportFeatureScanner] grid scan and converts the results to
 * [RoadSegmentFeature] objects.  A full Expo-parity implementation
 * would use GML (msGMLOutput) GetFeatureInfo to pull structured
 * fields (stigning, bredde, segmentlengde, gatetype, dekkemateriale).
 */
object HighscoreScanner {

    /**
     * Perform a grid scan of the current viewport and build a
     * complete [HighscoreResult].
     */
    suspend fun scan(
        map: MapLibreMap,
    ): HighscoreResult = withContext(Dispatchers.IO) {
        val region = map.projection.visibleRegion
        val latLngBounds = region.latLngBounds
        val sw = latLngBounds.southWest
        val ne = latLngBounds.northEast

        // Convert viewport corners to EPSG:3857 for the grid scan
        val (swX, swY) = CoordinateUtils.lonLatToMercator(sw.longitude, sw.latitude)
        val (neX, neY) = CoordinateUtils.lonLatToMercator(ne.longitude, ne.latitude)
        val minX = minOf(swX, neX)
        val minY = minOf(swY, neY)
        val maxX = maxOf(swX, neX)
        val maxY = maxOf(swY, neY)

        val viewportFeatures = ViewportFeatureScanner.scanViewport(
            bboxMinX = minX,
            bboxMinY = minY,
            bboxMaxX = maxX,
            bboxMaxY = maxY,
        )

        // Convert to RoadSegmentFeature (limited field mapping).
        val segments = viewportFeatures.mapNotNull { vf ->
            // Convert EPSG:3857 centre to WGS84 for RoadSegmentFeature
            val (lon, lat) = if (vf.centreX != 0.0 || vf.centreY != 0.0) {
                CoordinateUtils.mercatorToLonLat(vf.centreX, vf.centreY)
            } else {
                Pair(vf.rawProps["lon"]?.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0,
                     vf.rawProps["lat"]?.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0)
            }

            val props = vf.rawProps
            RoadSegmentFeature(
                objid = vf.objId,
                sourceLayer = vf.layerName,
                roadType = props["byggtype"]?.takeIf { it.isNotBlank() } ?: vf.byggtype,
                widthCm = props["bredde"]?.toDoubleOrNull(),
                slopePercent = props["stigning"]?.toDoubleOrNull(),
                municipality = props["kommune"] ?: "",
                surfaceMaterial = props["dekkemateriale"] ?: "",
                surfaceCondition = props["dekkestandard"] ?: "",
                comment = props["merknad"] ?: "",
                estimatedLengthMetres = props["segmentlengde"]?.toDoubleOrNull()
                    ?: props["lengde"]?.toDoubleOrNull(),
                centerLat = lat,
                centerLon = lon,
            )
        }

        buildHighscore(segments)
    }
}
