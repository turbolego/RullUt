package com.turbolego.rullut2.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.DialogProperties
import com.turbolego.rullut2.i18n.Strings
import com.turbolego.rullut2.model.PlaceResult

/**
 * Search modal — search Norwegian place names via Kartverket Stedsnavn API.
 * All text via [Strings] for i18n support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchModal(
    visible: Boolean,
    onSearchPlace: suspend (String) -> List<PlaceResult>,
    onSelectPlace: (PlaceResult) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(300)
        try {
            results = onSearchPlace(query)
        } catch (_: Exception) {
            results = emptyList()
        }
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                Strings.searchTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(Strings.searchPlaceholder) },
                    leadingIcon = {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = Strings.searchClose)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = Strings.searchTitle },
                )

                Spacer(Modifier.height(8.dp))

                if (results.isEmpty() && query.length >= 2 && !searching) {
                    Text(
                        Strings.searchNoResults,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(results) { place ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPlace(place)
                                    onDismiss()
                                }
                                .semantics { contentDescription = "Velg ${place.name}" },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    place.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    place.municipality,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.settingsCloseLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
    )
}