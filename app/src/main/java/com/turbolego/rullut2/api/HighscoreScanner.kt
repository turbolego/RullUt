package com.turbolego.rullut2.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the map viewport for road segments and builds the highscore
 * data structures used by [HighscoreModal].
 *
 * Uses the Geonorge WFS ([RoadWfsApi]) with a viewport bbox filter to
 * fetch all surveyed road segments (`app:TettstedVei` + `app:FriluftTurvei`)
 * with full attributes (bredde, stigning, segmentLengde, gatetype, kommune)
 * and real geometry.
 *
 * Note: this deliberately does NOT use the WMS GetFeatureInfo grid scan —
 * the Geonorge MapServer returns "no results" for the 1×1 pixel harvest
 * queries (sub-pixel geometry is dropped during rasterization), which made
 * the previous implementation always produce an empty highscore.
 */
object HighscoreScanner {

    /**
     * Perform a scan of the given viewport bounds and build a complete
     * [HighscoreResult].
     *
     * Bounds must be extracted on the main thread before calling this.
     */
    suspend fun scan(
        swLat: Double, swLon: Double,
        neLat: Double, neLon: Double,
    ): HighscoreResult = withContext(Dispatchers.IO) {
        val segments = RoadWfsApi.fetchRoadSegments(
            minLat = swLat,
            minLon = swLon,
            maxLat = neLat,
            maxLon = neLon,
        )

        buildHighscore(segments)
    }
}
