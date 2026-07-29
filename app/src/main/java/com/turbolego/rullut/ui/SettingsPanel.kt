package com.turbolego.rullut.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turbolego.rullut.model.LayerInfo

private val BASEMAP_OPTIONS = listOf(
    "osm" to "OpenStreetMap (Liberty)",
    "topo" to "Topografisk",
    "none" to "Ingen bakgrunn",
)

/**
 * Settings panel: basemap selector + layer toggle checkboxes.
 * ModalBottomSheet, WCAG accessible (Norwegian content descriptions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    visible: Boolean,
    layers: List<LayerInfo>,
    layersLoading: Boolean,
    activeLayers: Set<String>,
    onLayerToggle: (String) -> Unit,
    basemap: String,
    onBasemapChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .semantics { heading() }
        ) {
            Text(
                "Innstillinger",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "Innstillinger for kartet" }
            )
            Spacer(Modifier.height(16.dp))

            // Basemap picker
            Text(
                "Bakgrunnskart",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            BASEMAP_OPTIONS.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .semantics { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = basemap == id,
                        onClick = { onBasemapChange(id) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Layer toggles
            Text(
                "Kartlag",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            if (layersLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (layers.isEmpty()) {
                Text(
                    "Ingen kartlag funnet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LayerList(layers, activeLayers, onLayerToggle)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .align(Alignment.End)
                    .semantics { contentDescription = "Lukk innstillinger" }
            ) {
                Text("Lukk", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LayerList(
    layers: List<LayerInfo>,
    activeLayers: Set<String>,
    onLayerToggle: (String) -> Unit,
    indent: Int = 0,
) {
    layers.forEach { layer ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (indent * 16).dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = activeLayers.contains(layer.name),
                onCheckedChange = { onLayerToggle(layer.name) },
                modifier = Modifier.semantics {
                    contentDescription = "Slå på/av lag: ${layer.title}"
                }
            )
            Text(
                text = layer.title,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (layer.children.isNotEmpty()) {
            LayerList(
                layers = layer.children,
                activeLayers = activeLayers,
                onLayerToggle = onLayerToggle,
                indent = indent + 1,
            )
        }
    }
}