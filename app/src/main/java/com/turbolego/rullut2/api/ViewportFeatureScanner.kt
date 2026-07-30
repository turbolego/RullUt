package com.turbolego.rullut2.api

import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.ViewportFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Scans the current map viewport in a [MapConfig.GRID_COLS]×[MapConfig.GRID_ROWS]
 * grid using parallel WMS GetFeatureInfo requests, collects all features,
 * deduplicates by objid/lokalid, and returns them sorted by
 * distance from the viewport centre.
 *
 * Usage:
 *   val features = ViewportFeatureScanner.scanViewport(bboxMinX, bboxMinY, bboxMaxX, bboxMaxY)
 */
object ViewportFeatureScanner {

    /**
     * Perform a full grid scan of the given viewport bounds.
     */
    suspend fun scanViewport(
        bboxMinX: Double,
        bboxMinY: Double,
        bboxMaxX: Double,
        bboxMaxY: Double,
        queryLayers: String = MapConfig.TILGJENGELIGHET_QUERY_LAYERS,
    ): List<ViewportFeature> = withContext(Dispatchers.IO) {
        val cols = MapConfig.GRID_COLS
        val rows = MapConfig.GRID_ROWS
        val cellW = (bboxMaxX - bboxMinX) / cols
        val cellH = (bboxMaxY - bboxMinY) / rows

        val centreX = (bboxMinX + bboxMaxX) / 2.0
        val centreY = (bboxMinY + bboxMaxY) / 2.0

        // Build all cell queries
        val cells = mutableListOf<CellQuery>()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cMinX = bboxMinX + col * cellW
                val cMinY = bboxMinY + row * cellH
                val cMaxX = cMinX + cellW
                val cMaxY = cMinY + cellH
                cells.add(CellQuery(cMinX, cMinY, cMaxX, cMaxY))
            }
        }

        // Execute in parallel batches
        val results = coroutineScope {
            cells.map { cell ->
                async {
                    try {
                        queryCell(cell, queryLayers)
                    } catch (_: Exception) {
                        emptyList<ViewportFeature>()
                    }
                }
            }.flatMap { deferred ->
                try { deferred.await() } catch (_: Exception) { emptyList() }
            }
        }

        // Deduplicate by objId (first occurrence wins)
        val seen = mutableSetOf<String>()
        val deduplicated = mutableListOf<ViewportFeature>()
        for (feature in results) {
            if (feature.objId !in seen) {
                seen.add(feature.objId)
                deduplicated.add(feature.copy(
                    distanceFromCentre = computeDistance(
                        feature.centreX, feature.centreY,
                        centreX, centreY,
                    ),
                ))
            }
        }

        deduplicated.sortedBy { it.distanceFromCentre }
    }

    /**
     * Query a single grid cell via WMS GetFeatureInfo.
     */
    private suspend fun queryCell(
        cell: CellQuery,
        queryLayers: String,
    ): List<ViewportFeature> {
        val centreX = (cell.minX + cell.maxX) / 2.0
        val centreY = (cell.minY + cell.maxY) / 2.0

        val rawText = try {
            FeatureInfoApi.fetchBboxFeatures(
                bboxMinX = cell.minX,
                bboxMinY = cell.minY,
                bboxMaxX = cell.maxX,
                bboxMaxY = cell.maxY,
                queryLayers = queryLayers,
                featureCount = MapConfig.FEATURE_COUNT_PER_CELL,
            )
        } catch (_: Exception) {
            return emptyList()
        }

        if (rawText.isBlank()) return emptyList()

        val parsed = try {
            FeatureInfoParser.parseGetFeatureInfo(rawText, queryLayers)
        } catch (_: Exception) {
            return emptyList()
        }

        return parsed.map { feature ->
            ViewportFeature.fromFeatureInfo(
                feature = feature,
                centreX3857 = centreX,
                centreY3857 = centreY,
            )
        }
    }

    private data class CellQuery(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    )

    /** Euclidean distance in EPSG:3857 projected metres. */
    private fun computeDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
