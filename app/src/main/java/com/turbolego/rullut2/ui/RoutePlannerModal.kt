package com.turbolego.rullut2.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.turbolego.rullut2.i18n.Strings
import com.turbolego.rullut2.model.PlaceResult
import com.turbolego.rullut2.model.RouteResult

/**
 * Route planner — search for origin/destination, compute accessible route.
 * Uses place search instead of manual lat/lon entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerModal(
    visible: Boolean,
    myLocation: Pair<Double, Double>?,
    onRouteRequest: (fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) -> Unit,
    onSearchPlace: suspend (query: String) -> List<PlaceResult>,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    // Origin
    var fromQuery by remember { mutableStateOf("") }
    var fromResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var fromSelected by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var fromSearching by remember { mutableStateOf(false) }

    // Destination
    var toQuery by remember { mutableStateOf("") }
    var toResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var toSelected by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var toSearching by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }

    // Auto-set origin from current GPS
    LaunchedEffect(myLocation) {
        if (myLocation != null && fromSelected == null) {
            fromQuery = "📍 Min posisjon"
            fromSelected = myLocation
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                Strings.routeTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(12.dp))

            // ── FROM ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = Strings.routeFrom,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    Strings.routeFrom,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = fromQuery,
                onValueChange = {
                    fromQuery = it
                    fromSelected = null
                    if (it.length >= 2) {
                        fromSearching = true
                    }
                },
                label = { Text(Strings.searchPlaceholder) },
                trailingIcon = {
                    if (fromSelected != null) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // From search results
            if (fromSearching && fromQuery.length >= 2 && fromSelected == null) {
                LaunchedEffect(fromQuery) {
                    fromResults = onSearchPlace(fromQuery)
                    fromSearching = false
                }
            }
            if (fromResults.isNotEmpty() && fromSelected == null) {
                Column(modifier = Modifier.heightIn(max = 120.dp)) {
                    fromResults.take(4).forEach { place ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fromSelected = Pair(place.lat, place.lon)
                                    fromQuery = place.name
                                    fromResults = emptyList()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                "${place.name} — ${place.municipality}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── TO ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    Strings.routeTo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = toQuery,
                onValueChange = {
                    toQuery = it
                    toSelected = null
                    if (it.length >= 2) {
                        toSearching = true
                    }
                },
                label = { Text(Strings.searchPlaceholder) },
                trailingIcon = {
                    if (toSelected != null) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // To search results
            if (toSearching && toQuery.length >= 2 && toSelected == null) {
                LaunchedEffect(toQuery) {
                    toResults = onSearchPlace(toQuery)
                    toSearching = false
                }
            }
            if (toResults.isNotEmpty() && toSelected == null) {
                Column(modifier = Modifier.heightIn(max = 120.dp)) {
                    toResults.take(4).forEach { place ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    toSelected = Pair(place.lat, place.lon)
                                    toQuery = "${place.name} — ${place.municipality}"
                                    toSelected = Pair(place.lat, place.lon)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                "${place.name} — ${place.municipality}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Route button ──
            Button(
                onClick = {
                    val f = fromSelected
                    val t = toSelected
                    if (f != null && t != null) {
                        loading = true
                        onRouteRequest(f.first, f.second, t.first, t.second)
                    }
                },
                enabled = !loading && fromSelected != null && toSelected != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.routeCalculating, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(Strings.routeCalculate, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            if (routeResult != null) {
                RouteResultDisplay(routeResult!!)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RouteResultDisplay(result: RouteResult) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))

    Text(Strings.routeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatBox(Strings.routeDistance, result.distanceLabel)
        StatBox(Strings.routeDuration, result.durationLabel)
        StatBox(Strings.routeSource, result.routeSource.uppercase())
    }

    Spacer(Modifier.height(12.dp))

    Text(Strings.routeAccessibility, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))

    AccessibilityBar(
        Strings.routeAccessible(result.accessiblePct),
        result.accessiblePct, Color(0xFF00C853),
    )
    AccessibilityBar(
        Strings.routePartially(result.partiallyAccessiblePct),
        result.partiallyAccessiblePct, Color(0xFFFFC107),
    )
    AccessibilityBar(
        Strings.routeNotAccessible(result.notAccessiblePct),
        result.notAccessiblePct, Color(0xFFD32F2F),
    )
    AccessibilityBar(
        Strings.routeUnknown(result.unknownPct),
        result.unknownPct, Color(0xFF8B949E),
    )
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccessibilityBar(label: String, pct: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text("$pct%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}