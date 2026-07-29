package com.turbolego.rullut2.api

import android.content.Context
import android.util.Log
import com.turbolego.rullut2.map.MapConfig
import com.turbolego.rullut2.model.CoordinateUtils
import com.turbolego.rullut2.model.RouteAccessibilitySegment
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Accessibility assessment along a route.
 *
 * At sampled points along the route path, queries WMS GetFeatureInfo
 * on the t_vei_r layer and scores accessibility based on these fields:
 *   - tilgjengvurderingrulleman (wheelchair manual)
 *   - tilgjengvurderingrulleauto (wheelchair electric)
 *   - tilgjengvurderingelrull (el-rollator)
 *   - tilgjengvurderingsyn (visual impairment)
 *
 * Score: 0=unknown, 1=not accessible, 2=partial, 3=fully accessible
 * Ported from the Expo app's accessibility scoring logic.
 */
object AccessibilityAssessment {

    private const val TAG = "AccessibilityAssessment"
    private const val SAMPLE_INTERVAL_M = 100 // sample every 100m
    private const val SAMPLE_RADIUS_M = 50 // radius to query per sample

    private val client = OkHttpClient.Builder()
        .connectTimeout(10_000, TimeUnit.MILLISECONDS)
        .readTimeout(10_000, TimeUnit.MILLISECONDS)
        .build()

    data class AssessmentResult(
        val segments: List<RouteAccessibilitySegment>,
        val accessiblePct: Int,
        val partiallyAccessiblePct: Int,
        val notAccessiblePct: Int,
        val unknownPct: Int,
    )

    /**
     * Assess accessibility along a route path.
     * Samples at regular intervals and queries WMS for each.
     */
    suspend fun assess(
        context: Context,
        coordinates: List<Pair<Double, Double>>, // (lng, lat)
        source: String,
    ): AssessmentResult {
        if (coordinates.size < 2) {
            return AssessmentResult(emptyList(), 0, 0, 0, 100)
        }

        // Compute cumulative distances
        val cumDist = mutableListOf(0.0)
        for (i in 1 until coordinates.size) {
            val (lng1, lat1) = coordinates[i - 1]
            val (lng2, lat2) = coordinates[i]
            cumDist.add(
                cumDist.last() + CoordinateUtils.haversineM(lat1, lng1, lat2, lng2)
            )
        }

        val totalDist = cumDist.last()
        if (totalDist <= 0.0) {
            return AssessmentResult(emptyList(), 0, 0, 0, 100)
        }

        // Sample points
        val samplePoints = mutableListOf<Pair<Double, Double>>() // (lat, lng)
        val sampleDistances = mutableListOf<Double>()

        // Start at 0, then every SAMPLE_INTERVAL_M
        samplePoints.add(Pair(coordinates[0].second, coordinates[0].first))
        sampleDistances.add(0.0)

        var nextSampleDist = SAMPLE_INTERVAL_M.toDouble()
        while (nextSampleDist < totalDist) {
            val idx = cumDist.indexOfFirst { it >= nextSampleDist }
            if (idx < 0) break

            val p = if (idx > 0) {
                val segStart = cumDist[idx - 1]
                val segEnd = cumDist[idx]
                val frac = if (segEnd > segStart) (nextSampleDist - segStart) / (segEnd - segStart) else 0.0
                val (lng1, lat1) = coordinates[idx - 1]
                val (lng2, lat2) = coordinates[idx]
                Pair(lat1 + (lat2 - lat1) * frac, lng1 + (lng2 - lng1) * frac)
            } else {
                Pair(coordinates[idx].second, coordinates[idx].first)
            }

            samplePoints.add(p)
            sampleDistances.add(nextSampleDist)
            nextSampleDist += SAMPLE_INTERVAL_M
        }

        // Also sample the end
        if (cumDist.size > 1 && totalDist % SAMPLE_INTERVAL_M > 10) {
            val last = coordinates.last()
            samplePoints.add(Pair(last.second, last.first))
            sampleDistances.add(totalDist)
        }

        // Query WMS for each sample point
        val scores = mutableListOf<Int>()
        for ((lat, lng) in samplePoints) {
            val score = queryAccessibilityAt(lat, lng)
            scores.add(score)
        }

        // Build segments
        return buildSegments(sampleDistances, scores, totalDist)
    }

    /**
     * Query WMS t_vei_r at a point for accessibility data.
     * Returns score: 0=unknown, 1=not accessible, 2=partial, 3=full
     */
    private suspend fun queryAccessibilityAt(lat: Double, lng: Double): Int {
        try {
            val (mx, my) = CoordinateUtils.lonLatToMercator(lng, lat)
            val mapBbox = "${mx - SAMPLE_RADIUS_M},${my - SAMPLE_RADIUS_M}," +
                    "${mx + SAMPLE_RADIUS_M},${my + SAMPLE_RADIUS_M}"
            val i = 128
            val j = 128

            val url = buildString {
                append(MapConfig.WMS_BASE_URL)
                append("?service=WMS&request=GetFeatureInfo")
                append("&version=1.1.1")
                append("&layers=t_vei_r")
                append("&query_layers=t_vei_r")
                append("&styles=")
                append("&srs=EPSG:3857")
                append("&bbox=$mapBbox")
                append("&width=256&height=256")
                append("&format=image/png&transparent=true")
                append("&info_format=text/plain")
                append("&feature_count=1")
                append("&x=$i&y=$j")
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MapConfig.USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return 0

            val raw = response.body?.string() ?: return 0
            return scoreFromResponse(raw)
        } catch (_: Exception) {
            return 0
        }
    }

    /**
     * Parse the WMS GetFeatureInfo response for accessibility fields.
     * Score logic mirrors the Expo app:
     * - If any field indicates "ikke tilgjengerig" → 1 (not accessible)
     * - If any field indicates "delvis" → 2 (partial)
     * - If all accessible fields indicate full access → 3 (accessible)
     * - No data → 0 (unknown)
     */
    private fun scoreFromResponse(raw: String): Int {
        val lower = raw.lowercase()

        val fields = listOf(
            "tilgjengvurderingrulleman",
            "tilgjengvurderingrulleauto",
            "tilgjengvurderingelrull",
            "tilgjengvurderingsyn",
        )

        var hasData = false
        var notAccessible = false
        var partial = false

        for (field in fields) {
            val idx = lower.indexOf(field)
            if (idx < 0) continue

            // Extract the value after the last colon on this line
            val lineStart = lower.lastIndexOf('\n', idx) + 1
            val lineEnd = lower.indexOf('\n', idx)
            val line = if (lineEnd > lineStart) lower.substring(lineStart, lineEnd)
                       else lower.substring(lineStart)

            val value = line.substringAfter(":").trim()
            if (value.isEmpty() || value == "null") continue

            hasData = true

            when {
                value.contains("ikke") -> notAccessible = true
                value.contains("delvis") || value.contains("del") -> partial = true
            }
        }

        return when {
            !hasData -> 0
            notAccessible -> 1
            partial -> 2
            else -> 3
        }
    }

    /**
     * Build route segments with accessibility scores.
     * Maps sampled scores back to distance ranges.
     */
    private fun buildSegments(
        distances: List<Double>,
        scores: List<Int>,
        totalDist: Double,
    ): AssessmentResult {
        val segments = mutableListOf<RouteAccessibilitySegment>()
        var totalAccessible = 0.0
        var totalPartial = 0.0
        var totalNotAccessible = 0.0
        var totalUnknown = 0.0

        for (i in 0 until scores.size - 1) {
            val startM = distances[i]
            val endM = distances[i + 1]
            val score = scores[i]
            val segLen = endM - startM

            val labels = mapOf(
                0 to "Ingen data",
                1 to "Ikke tilgjengelig",
                2 to "Delvis tilgjengelig",
                3 to "Tilgjengelig",
            )

            segments.add(
                RouteAccessibilitySegment(
                    range = startM..endM,
                    score = score,
                    label = labels[score] ?: "Ukjent",
                )
            )

            when (score) {
                3 -> totalAccessible += segLen
                2 -> totalPartial += segLen
                1 -> totalNotAccessible += segLen
                0 -> totalUnknown += segLen
            }
        }

        val total = totalDist.coerceAtLeast(1.0)

        return AssessmentResult(
            segments = segments,
            accessiblePct = ((totalAccessible / total) * 100).toInt(),
            partiallyAccessiblePct = ((totalPartial / total) * 100).toInt(),
            notAccessiblePct = ((totalNotAccessible / total) * 100).toInt(),
            unknownPct = ((totalUnknown / total) * 100).toInt(),
        )
    }
}