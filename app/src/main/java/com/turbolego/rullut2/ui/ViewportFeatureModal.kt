package com.turbolego.rullut2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.turbolego.rullut2.model.ViewportFeature
import java.util.Locale

/**
 * Modal bottom sheet / dialog showing all WMS features visible in the current
 * map viewport, collected via a 4×4 grid scan.
 *
 * @param isVisible       Whether the modal is currently shown.
 * @param features        The full list of deduplicated features (empty = loading).
 * @param isLoading       True while the grid scan is in progress.
 * @param errorMessage    Non-null when an error occurred.
 * @param onDismiss       Called when the user dismisses the modal.
 * @param onFeatureClick  Called with the [ViewportFeature] the user tapped;
 *                        the caller should move the camera to that feature
 *                        and close the modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewportFeatureModal(
    isVisible: Boolean,
    features: List<ViewportFeature>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onFeatureClick: (ViewportFeature) -> Unit,
) {
    if (!isVisible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 600.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isLoading) "Søker..." else "Objekter i visning",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                if (isLoading.not()) {
                    Text(
                        text = "${features.size} stk",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Lukk",
                    )
                }
            }

            // ── Error state ─────────────────────────────────────────
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ── Loading spinner ──────────────────────────────────────
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Skanner kartet i rutenett...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Feature list ─────────────────────────────────────────
            if (!isLoading && errorMessage == null) {
                if (features.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Ingen objekter funnet i dette kartutsnittet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(features, key = { it.objId }) { feature ->
                            FeatureCard(
                                feature = feature,
                                onClick = { onFeatureClick(feature) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Single feature card ──────────────────────────────────────────────

@Composable
private fun FeatureCard(
    feature: ViewportFeature,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Type label + distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = feature.typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatDistance(feature.distanceFromCentre),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Feature name
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Row 3: Accessibility badge
            if (feature.accessibility.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                val chipColor = when (feature.accessibilitySummary) {
                    "Tilgjengelig" -> Color(0xFF4CAF50)     // green
                    "Delvis tilgjengelig" -> Color(0xFFFFA726) // amber
                    else -> Color(0xFFEF5350)                 // red / default
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = chipColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = feature.accessibilitySummary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = chipColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

/**
 * Format a distance in metres to a human-friendly string.
 * e.g. "12 m", "1.2 km", "340 m"
 */
private fun formatDistance(metres: Double): String {
    return if (metres < 1000.0) {
        "${metres.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", metres / 1000.0)
    }
}
