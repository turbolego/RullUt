package com.turbolego.rullut2.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbolego.rullut2.api.HighscoreCategory
import com.turbolego.rullut2.api.HighscoreEntry
import com.turbolego.rullut2.api.HighscoreResult
import com.turbolego.rullut2.api.RoadSegmentFeature
import kotlinx.coroutines.launch

// ── Colour palette (dark theme optimised) ────────────────────────────────

private val AccessibleGreen = Color(0xFF4CAF50)
private val SteepOrange = Color(0xFFFF9800)
private val WideBlue = Color(0xFF2196F3)
private val FlatTeal = Color(0xFF009688)

private fun categoryColor(cat: HighscoreCategory): Color = when (cat) {
    HighscoreCategory.LONGEST -> AccessibleGreen
    HighscoreCategory.STEEPEST -> SteepOrange
    HighscoreCategory.WIDEST -> WideBlue
    HighscoreCategory.FLATTEST -> FlatTeal
}

// ── Highscore FAB ────────────────────────────────────────────────────────

/**
 * Floating action button that triggers the highscore scan.
 * Place on the [MapScreen] or wherever the map is rendered.
 *
 * @param onClick Called when the FAB is tapped.
 * @param isLoading When true shows a progress indicator.
 */
@Composable
fun HighscoreFab(
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Highscore-skanning",
            )
        }
    }
}

// ── Highscore Modal (BottomSheet) ────────────────────────────────────────

/**
 * Bottom-sheet modal showing the four highscore top lists.
 *
 * @param result         The [HighscoreResult] from [buildHighscore].
 * @param onDismiss      Called when the sheet is dismissed.
 * @param onZoomToFeature Called when the user taps "Zoom til veien".
 *                        Receives the [RoadSegmentFeature] to centre the map on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighscoreModal(
    result: HighscoreResult,
    onDismiss: () -> Unit,
    onZoomToFeature: (RoadSegmentFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = HighscoreCategory.entries
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .animateContentSize(),
        ) {
            // ── Title ────────────────────────────────────────────────────
            Text(
                text = "Tilgjengelighet — Highscore",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // ── Stats header ─────────────────────────────────────────────
            StatsHeader(result)

            Spacer(Modifier.height(12.dp))

            // ── Category tabs ────────────────────────────────────────────
            CategoryTabs(
                categories = categories,
                selectedIndex = selectedCategory,
                onSelect = { selectedCategory = it },
            )

            Spacer(Modifier.height(8.dp))

            // ── Top-10 list ──────────────────────────────────────────────
            val cat = categories[selectedCategory]
            val entries = result.entriesFor(cat)

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Ingen veier funnet i denne kategorien.\nSkanne et større område for flere resultater.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(entries) { _, entry ->
                        HighscoreRow(
                            entry = entry,
                            onZoom = { onZoomToFeature(entry.feature) },
                        )
                    }
                    // Bottom padding
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ── Stats header ─────────────────────────────────────────────────────────

@Composable
private fun StatsHeader(result: HighscoreResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatChip("Segmenter", "${result.segmentsFound}")
        StatChip("Totalt", "%.1f km".format(result.totalKm))
        StatChip("Snitt stigning", "%.1f%%".format(result.averageSlope))
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Category tabs ────────────────────────────────────────────────────────

@Composable
private fun CategoryTabs(
    categories: List<HighscoreCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categories.forEachIndexed { idx, cat ->
            val isSelected = idx == selectedIndex
            val color = categoryColor(cat)

            FilledTonalButton(
                onClick = { onSelect(idx) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isSelected)
                        color.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) color
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = cat.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── Single row ───────────────────────────────────────────────────────────

@Composable
private fun HighscoreRow(
    entry: HighscoreEntry,
    onZoom: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        categoryColor(entry.category).copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#${entry.rank}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor(entry.category),
                )
            }

            Spacer(Modifier.width(10.dp))

            // Road type + municipality
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.roadType,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.municipality,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Measurement value
            Box(
                modifier = Modifier.width(80.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = entry.measurement,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor(entry.category),
                )
            }

            Spacer(Modifier.width(6.dp))

            // Zoom button
            FilledTonalButton(
                onClick = onZoom,
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Zoom til veien",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Zoom",
                    fontSize = 11.sp,
                )
            }
        }
    }
}
