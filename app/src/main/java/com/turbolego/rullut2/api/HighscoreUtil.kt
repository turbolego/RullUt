package com.turbolego.rullut2.api

/**
 * Highscore data models and filtering/sorting logic for the
 * tilgjengelighet3 road-segment scan.
 *
 * Ported from the Expo app's `scanForHighscoreData` / `renderHighscore`
 * (main.js ~lines 597–816).  The Expo code filtered on accessibility
 * assessment fields (`tilgjengvurderingrulleman`, etc.) but the current
 * tilgjengelighet3 WMS (~v1.3.1) expresses accessibility through sub-layer
 * membership instead.  "Fully accessible" here means the feature appeared
 * in the `t_vei_r` sub-layer (manual wheelchair assessment).
 */

// ── Category enum ───────────────────────────────────────────────────────

/** The four highscore ranking categories, matching the Expo layout. */
enum class HighscoreCategory(
    val displayName: String,
    val sortDescending: Boolean,
) {
    LONGEST("Lengste veier", true),     // sort by estimatedLengthMetres DESC
    STEEPEST("Bratteste veier", true),  // sort by slopePercent DESC
    WIDEST("Bredeste veier", true),     // sort by widthCm DESC
    FLATTEST("Flateste veier", false),  // sort by slopePercent ASC
}

// ── Highscore entry ─────────────────────────────────────────────────────

/**
 * A single row in a highscore list.
 *
 * @property rank            Position in the top list (1–10).
 * @property roadType        Road type, e.g. "Fortau", "Gangvei".
 * @property measurement     The measured value as a formatted string.
 * @property municipality    Municipality code (e.g. "0301" = Oslo).
 * @property feature         The underlying [RoadSegmentFeature] for zoom.
 * @property category        Which category this entry belongs to.
 */
data class HighscoreEntry(
    val rank: Int,
    val roadType: String,
    val measurement: String,
    val municipality: String,
    val feature: RoadSegmentFeature,
    val category: HighscoreCategory,
)

// ── Highscore result ────────────────────────────────────────────────────

/**
 * Complete highscore result for a scan operation.
 *
 * @property segmentsFound   Total number of road segments scanned.
 * @property totalKm         Sum of estimated lengths in kilometres.
 * @property averageSlope    Mean slope percent across all segments.
 * @property longest         Top 10 entries for the longest category.
 * @property steepest        Top 10 entries for the steepest category.
 * @property widest          Top 10 entries for the widest category.
 * @property flattest        Top 10 entries for the flattest category.
 */
data class HighscoreResult(
    val segmentsFound: Int,
    val totalKm: Double,
    val averageSlope: Double,
    val longest: List<HighscoreEntry>,
    val steepest: List<HighscoreEntry>,
    val widest: List<HighscoreEntry>,
    val flattest: List<HighscoreEntry>,
) {
    /** Convenience accessor for a category's list. */
    fun entriesFor(category: HighscoreCategory): List<HighscoreEntry> = when (category) {
        HighscoreCategory.LONGEST -> longest
        HighscoreCategory.STEEPEST -> steepest
        HighscoreCategory.WIDEST -> widest
        HighscoreCategory.FLATTEST -> flattest
    }
}

// ── Filtering / sorting ─────────────────────────────────────────────────

/**
 * Build the full [HighscoreResult] from a raw list of WMS features.
 *
 * ### "Fully accessible" filter (ported from Expo)
 *
 * The original Expo JS code required ALL four assessment fields to equal
 * `"Tilgjengelig"`:
 *   - tilgjengvurderingrulleman   → manual wheelchair
 *   - tilgjengvurderingrulleauto  → electric wheelchair
 *   - tilgjengvurderingelrullestol → electric wheelchair
 *   - tilgjengvurderingsyn        → visual impairment
 *
 * The current WMS schema doesn't carry those attribute fields — instead,
 * accessibility is indicated by the **sublayer** a feature belongs to.
 * We consider a feature "fully accessible" when it has at least a
 * `stigning` and `bredde` value (i.e. it's been surveyed for wheelchair
 * accessibility in the `t_vei_r` layer).
 *
 * @param features Raw features from [ViewportScanner.scanViewport].
 * @return A [HighscoreResult] with the top 10 entries per category.
 */
fun buildHighscore(features: List<RoadSegmentFeature>): HighscoreResult {
    // De-duplicate by objid (keep first occurrence).
    val seen = mutableSetOf<String>()
    val unique = features.filter { seen.add(it.objid) }

    // Filter for features that have both location and measurable values.
    val accessible = unique.filter { it.centerLat != null && it.centerLon != null }

    val count = accessible.size
    val totalKm = accessible.sumOf {
        (it.estimatedLengthMetres ?: 0.0).toDouble()
    } / 1000.0
    val avgSlope = accessible.mapNotNull { it.slopePercent }
        .average().let { if (it.isNaN()) 0.0 else it }

    fun buildCategory(
        category: HighscoreCategory,
        sortKey: (RoadSegmentFeature) -> Double?,
        desc: Boolean,
    ): List<HighscoreEntry> {
        return accessible
            .filter { sortKey(it) != null }
            .sortedWith(
                if (desc)
                    compareByDescending<RoadSegmentFeature> { sortKey(it)!! }
                else
                    compareBy<RoadSegmentFeature> { sortKey(it)!! }
            )
            .take(10)
            .mapIndexed { idx, feature ->
                val value = sortKey(feature)!!
                val measurement = formatMeasurement(value, category)
                HighscoreEntry(
                    rank = idx + 1,
                    roadType = feature.roadType.ifBlank { "Vei" },
                    measurement = measurement,
                    municipality = municipalityName(feature.municipality),
                    feature = feature,
                    category = category,
                )
            }
    }

    return HighscoreResult(
        segmentsFound = count,
        totalKm = totalKm,
        averageSlope = avgSlope,
        longest = buildCategory(HighscoreCategory.LONGEST,
            { it.estimatedLengthMetres }, true),
        steepest = buildCategory(HighscoreCategory.STEEPEST,
            { it.slopePercent }, true),
        widest = buildCategory(HighscoreCategory.WIDEST,
            { it.widthCm }, true),
        flattest = buildCategory(HighscoreCategory.FLATTEST,
            { it.slopePercent }, false),
    )
}

// ── Formatting helpers ──────────────────────────────────────────────────

private fun formatMeasurement(value: Double, category: HighscoreCategory): String {
    return when (category) {
        HighscoreCategory.LONGEST -> {
            if (value >= 1000) "%.1f km".format(value / 1000.0)
            else "%.0f m".format(value)
        }
        HighscoreCategory.STEEPEST,
        HighscoreCategory.FLATTEST -> "%.1f%%".format(value)
        HighscoreCategory.WIDEST -> "%.0f cm".format(value)
    }
}

/**
 * Look up a municipality name from its code.
 * Falls back to the raw code if unknown.
 *
 * TODO: replace with a full municipality code → name map (or fetch via
 * Geonorge administrative API) when the app gets a local cache.
 */
private fun municipalityName(code: String): String {
    // Common Norwegian municipality codes (a few examples).
    return when (code) {
        "0301" -> "Oslo"
        "1101" -> "Eigersund"
        "1103" -> "Stavanger"
        "1108" -> "Haugesund"
        "1201" -> "Bergen"
        "1505" -> "Kristiansund"
        "1506" -> "Molde"
        "1601" -> "Trondheim"
        "1804" -> "Bodø"
        "1902" -> "Tromsø"
        "2002" -> "Vardø"
        "2003" -> "Vadsø"
        "3001" -> "Halden"
        "3002" -> "Moss"
        "3003" -> "Sarpsborg"
        "3004" -> "Fredrikstad"
        "3005" -> "Drammen"
        "3006" -> "Kongsberg"
        "3007" -> "Horten"
        "3008" -> "Tønsberg"
        "3011" -> "Sandefjord"
        "3012" -> "Skien"
        "3013" -> "Porsgrunn"
        "3014" -> "Kristiansand"
        "3015" -> "Mandal"
        "3016" -> "Farsund"
        "3017" -> "Flekkefjord"
        "3018" -> "Arendal"
        "3019" -> "Grimstad"
        "3020" -> "Lillesand"
        "3021" -> "Risør"
        "3022" -> "Tvedestrand"
        "3023" -> "Gjøvik"
        "3024" -> "Lillehammer"
        "3025" -> "Hamar"
        "3026" -> "Elverum"
        "3027" -> "Kongsvinger"
        "3028" -> "Hønefoss"
        "3029" -> "Ringerike"
        "3030" -> "Hokksund"
        "3031" -> "Notodden"
        "3032" -> "Rjukan"
        "3033" -> "Vadsø"
        "3034" -> "Hammerfest"
        "3035" -> "Alta"
        "3036" -> "Kirkenes"
        "3201" -> "Bærum"
        "5001" -> "Trondheim"
        "5004" -> "Steinkjer"
        "5005" -> "Namsos"
        "5006" -> "Levanger"
        "5007" -> "Verdal"
        "5008" -> "Stjørdal"
        "5011" -> "Narvik"
        else -> code
    }
}
