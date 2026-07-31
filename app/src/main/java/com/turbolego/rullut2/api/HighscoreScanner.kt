package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.RoadSegmentFeature
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
     * Perform a grid scan of the given viewport bounds and build a
     * complete [HighscoreResult].
     *
     * Bounds must be extracted on the main thread before calling this.
     */
    suspend fun scan(
        swLat: Double, swLon: Double,
        neLat: Double, neLon: Double,
    ): HighscoreResult = withContext(Dispatchers.IO) {
        val (swX, swY) = CoordinateUtils.lonLatToMercator(swLon, swLat)
        val (neX, neY) = CoordinateUtils.lonLatToMercator(neLon, neLat)
        val minX = minOf(swX, neX)
        val minY = minOf(swY, neY)
        val maxX = maxOf(swX, neX)
        val maxY = maxOf(swY, neY)

        val viewportFeatures = ViewportFeatureScanner.scanViewport(
            bboxMinX = minX,
            bboxMinY = minY,
            bboxMaxX = maxX,
            bboxMaxY = maxY,
            queryLayers = "t_vei_r",
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
                objid = vf.objId.ifBlank { "${vf.layerName}:${vf.featureId}:${vf.centreX}:${vf.centreY}" },
                sourceLayer = vf.layerName,
                roadType = props["gatetype"]?.takeIf { it.isNotBlank() }
                    ?: props["byggtype"]?.takeIf { it.isNotBlank() }
                    ?: vf.byggtype,
                widthCm = props["bredde"]?.toDoubleOrNull(),
                slopePercent = props["stigning"]?.toDoubleOrNull(),
                municipality = props["kommune"] ?: "",
                surfaceMaterial = props["dekkemateriale"] ?: "",
                surfaceCondition = props["dekketilstand"] ?: "",
                comment = props["kommentar"] ?: "",
                estimatedLengthMetres = props["segmentlengde"]?.toDoubleOrNull()
                    ?: props["lengde"]?.toDoubleOrNull(),
                centerLat = lat,
                centerLon = lon,
            )
        }

        buildHighscore(segments)
    }
}
