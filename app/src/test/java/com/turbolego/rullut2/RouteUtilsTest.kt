package com.turbolego.rullut2

import com.turbolego.rullut2.api.AccessibilityAssessment
import com.turbolego.rullut2.api.ValhallaRouteApi
import com.turbolego.rullut2.model.RouteAccessibilitySegment
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Valhalla polyline decoding and
 * accessibility assessment logic.
 */
class RouteUtilsTest {

    @Test
    fun `decode simple polyline`() {
        // Precision-6 polyline example matching the Valhalla decoder in the app.
        val encoded = "_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI"

        val decoded = decodePolylineReflectively(encoded)

        assertEquals("Should decode three coordinates", 3, decoded.size)
        assertEquals("First point lng", -120.2, decoded[0].first, 1e-6)
        assertEquals("First point lat", 38.5, decoded[0].second, 1e-6)
        assertEquals("Second point lng", -120.95, decoded[1].first, 1e-6)
        assertEquals("Second point lat", 40.7, decoded[1].second, 1e-6)
        assertEquals("Third point lng", -126.453, decoded[2].first, 1e-6)
        assertEquals("Third point lat", 43.252, decoded[2].second, 1e-6)
    }

    @Test
    fun `decode empty polyline`() {
        val decoded = decodePolylineReflectively("")
        assertTrue("Empty polyline should return empty list", decoded.isEmpty())
    }

    @Test
    fun `scoreFromResponse detects not accessible`() {
        val response = """
            --- t_vei_r ---
            FeatureId: gid_123
            tilgjengvurderingrulleman: Ikke tilgjengelig
            tilgjengvurderingrulleauto: Ikke tilgjengelig
        """.trimIndent()

        val score = scoreFromResponseReflectively(response)
        assertEquals("Should score as not accessible", 1, score)
    }

    @Test
    fun `scoreFromResponse detects fully accessible`() {
        val response = """
            FeatureId: gid_456
            tilgjengvurderingrulleman: Fullt tilgjengelig
            tilgjengvurderingrulleauto: Fullt tilgjengelig
        """.trimIndent()

        val score = scoreFromResponseReflectively(response)
        assertEquals("Should score as fully accessible", 3, score)
    }

    @Test
    fun `scoreFromResponse detects partial accessibility`() {
        val response = """
            FeatureId: gid_777
            tilgjengvurderingrulleman: Delvis tilgjengelig
        """.trimIndent()

        val score = scoreFromResponseReflectively(response)
        assertEquals("Should score as partially accessible", 2, score)
    }

    @Test
    fun `scoreFromResponse returns unknown for missing values`() {
        val response = """
            FeatureId: gid_888
            tilgjengvurderingrulleman: null
        """.trimIndent()

        val score = scoreFromResponseReflectively(response)
        assertEquals("Missing values should score as unknown", 0, score)
    }

    @Test
    fun `accessibility breakdown calculates percentages`() {
        val distances = listOf(0.0, 100.0, 200.0, 300.0) // 300m total
        val scores = listOf(3, 3, 1, 2) // accessible, accessible, not, partial

        // Total accessible: 0→100 (100m) + 100→200 (100m) = 200m accessible
        // Total partial: 200→300 (100m partial? Actually segment 3's score at position 2 is 1 for distance 200→300)
        // Actually scores apply to segment start, so:
        // seg 0 (0→100): score=3, accessible=100m
        // seg 1 (100→200): score=3, accessible=100m
        // seg 2 (200→300): score=1, not accessible=100m
        // Score at position 3 is the final sample point and does not start a segment.

        val result = buildSegmentsReflectively(distances, scores, 300.0)

        assertEquals("Should build one segment per interval", 3, result.segments.size)
        assertEquals("First segment starts at 0m", 0.0, result.segments[0].range.start, 0.001)
        assertEquals("First segment ends at 100m", 100.0, result.segments[0].range.endInclusive, 0.001)
        assertEquals("Accessible percentage", 66, result.accessiblePct)
        assertEquals("Partially accessible percentage", 0, result.partiallyAccessiblePct)
        assertEquals("Not accessible percentage", 33, result.notAccessiblePct)
        assertEquals("Unknown percentage", 0, result.unknownPct)
    }
}

/**
 * Quick reflective test of Valhalla polyline decode through the known API.
 * The decodePolyline method in ValhallaRouteApi is private, so we test
 * indirectly by checking the computeRoute response factor.
 */
private fun decodePolylineReflectively(encoded: String): List<Pair<Double, Double>> {
    val method = ValhallaRouteApi::class.java.getDeclaredMethod("decodePolyline", String::class.java)
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(ValhallaRouteApi, encoded) as List<Pair<Double, Double>>
}

private fun scoreFromResponseReflectively(raw: String): Int {
    val method = AccessibilityAssessment::class.java.getDeclaredMethod(
        "scoreFromResponse",
        String::class.java,
    )
    method.isAccessible = true
    return method.invoke(AccessibilityAssessment, raw) as Int
}

private fun buildSegmentsReflectively(
    distances: List<Double>,
    scores: List<Int>,
    totalDist: Double,
): AccessibilityAssessment.AssessmentResult {
    val method = AccessibilityAssessment::class.java.getDeclaredMethod(
        "buildSegments",
        List::class.java,
        List::class.java,
        Double::class.javaPrimitiveType,
    )
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(AccessibilityAssessment, distances, scores, totalDist) as AccessibilityAssessment.AssessmentResult
}