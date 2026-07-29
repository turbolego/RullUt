package com.turbolego.rullut.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turbolego.rullut.model.RouteResult

/**
 * Route planner modal — enter origin/destination, find accessible route.
 *
 * Layout: two text inputs for origin/destination coordinates,
 * a "Finn rute" button, and result display with color-coded segments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerModal(
    visible: Boolean,
    myLocation: Pair<Double, Double>?,
    onRouteRequest: (fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var fromLat by remember { mutableStateOf("") }
    var fromLng by remember { mutableStateOf("") }
    var toLat by remember { mutableStateOf("") }
    var toLng by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }

    // Pre-fill origin with myLocation if available
    LaunchedEffect(myLocation) {
        if (fromLat.isEmpty() && myLocation != null) {
            fromLat = myLocation.first.toString().take(8)
            fromLng = myLocation.second.toString().take(8)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Ruteplanlegger",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))

            // FROM
            Text("Fra", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedTextField(
                    value = fromLat,
                    onValueChange = { fromLat = it },
                    label = { Text("Bredde (lat)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = fromLng,
                    onValueChange = { fromLng = it },
                    label = { Text("Lengde (lon)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(12.dp))

            // TO
            Text("Til", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedTextField(
                    value = toLat,
                    onValueChange = { toLat = it },
                    label = { Text("Latrde (lat)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = toLng,
                    onValueChange = { toLng = it },
                    label = { Text("Lngde (lon)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Route button
            Button(
                onClick = {
                    val fLat = fromLat.toDoubleOrNull()
                    val fLng = fromLng.toDoubleOrNull()
                    val tLat = toLat.toDoubleOrNull()
                    val tLng = toLng.toDoubleOrNull()
                    if (fLat != null && fLng != null && tLat != null && tLng != null) {
                        loading = true
                        onRouteRequest(fLat, fLng, tLat, tLng)
                    }
                },
                enabled = !loading && fromLat.isNotBlank() && toLat.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Finn tilgjengelig rute" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Finn tilgjengelig rute", color = MaterialTheme.colorScheme.onPrimary)
            }

            // Route result
            if (routeResult != null) {
                RouteResultDisplay(routeResult!!, loading)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RouteResultDisplay(result: RouteResult, loading: Boolean) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))

    Text(
        "Resultat",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = "Ruteresultat" }
    )
    Spacer(Modifier.height(8.dp))

    // Summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatBox("Avstand", result.distanceLabel)
        StatBox("Tid", result.durationLabel)
        StatBox("Kilde", result.routeSource.uppercase())
    }

    Spacer(Modifier.height(12.dp))

    // Accessibility breakdown
    Text("Tilgjengelighet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))

    AccessibilityBar("Tilgjengelig", result.accessiblePct, Color(0xFF00C853))
    AccessibilityBar("Delvis tilgjengelig", result.partiallyAccessiblePct, Color(0xFFFFC107))
    AccessibilityBar("Ikke tilgjengelig", result.notAccessiblePct, Color(0xFFD32F2F))
    AccessibilityBar("Ingen data", result.unknownPct, Color(0xFF8B949E))
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccessibilityBar(label: String, pct: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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