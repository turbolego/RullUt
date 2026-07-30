package com.turbolego.rullut2.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turbolego.rullut2.i18n.PROPERTY_LABELS_NB
import com.turbolego.rullut2.i18n.Strings
import com.turbolego.rullut2.i18n.WFS_IMAGE_BASE_URL
import com.turbolego.rullut2.model.FeatureInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Property keys that carry image filenames from WFS features.
 */
private val BILDEFIL_KEYS = setOf("bildefil1", "bildefil2", "bildefil3")

/**
 * Helper: format a WMS property key+value pair into a Norwegian-friendly string.
 * Translates technical WMS keys using [PROPERTY_LABELS_NB]; falls back to the
 * original key if no translation exists. Exposed as a top-level function so it
 * can be used elsewhere (e.g., in testing or for custom label rendering).
 *
 * Example:
 *   formatPropertyForDisplay("tilgjengvurderingrulleman", "ja")
 *   → "Manuell rullestol: ja"
 */
fun formatPropertyForDisplay(key: String, value: String): String {
    val label = PROPERTY_LABELS_NB[key.lowercase()] ?: key
    return "$label: $value"
}

// ─────────────────────────────────────────────────────────────────

/**
 * Bottom sheet popup showing WMS GetFeatureInfo results.
 *
 * Mirrors the Expo app's rich FeaturePopup:
 *  - Deduplicates features by layerName + featureId
 *  - Extracts and displays WFS building images from `bildefil1/2/3` props
 *  - Formats technical WMS property keys as Norwegian labels
 *  - Shows an image carousel (horizontal scroll) with click-to-open in browser
 *  - Uses Material3 [Card] for each feature block
 *
 * TalkBack-accessible: headers, property rows, and buttons all get content descriptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePopup(
    visible: Boolean,
    loading: Boolean,
    title: String,
    features: List<FeatureInfo>,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    // ── Deduplicate by layerName + featureId (mirrors Expo dedup logic) ──
    val dedupedFeatures = remember(features) {
        val seen = mutableSetOf<String>()
        features.filter { f ->
            val key = "${f.layerName}:${f.featureId}"
            if (key in seen) false else {
                seen.add(key)
                true
            }
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
                .semantics { heading() },
        ) {
            // ── Title ──
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = title },
            )

            Spacer(Modifier.height(12.dp))

            // ── Content ──
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                dedupedFeatures.isEmpty() -> {
                    Text(
                        Strings.featureNoInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        dedupedFeatures.forEach { feature ->
                            FeatureCard(feature)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Dismiss button ──
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .align(Alignment.End)
                    .semantics { contentDescription = Strings.featureInfoTitle },
            ) {
                Text(Strings.settingsCloseLabel, color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Feature card (one per deduplicated feature)
// ─────────────────────────────────────────────────────────────────

/**
 * A Material3 [Card] displaying one WMS feature:
 *  - Layer name header
 *  - Horizontal-scrolling image carousel (if WFS building photos are available)
 *  - Property rows with Norwegian-friendly labels
 */
@Composable
private fun FeatureCard(feature: FeatureInfo) {
    // ── Image URLs: combine parser-detected images + bildefil props ──
    val imageUrls = remember(feature) {
        val urls = mutableSetOf<String>()
        // Already-parsed image URLs from FeatureInfoParser (direct http links)
        urls.addAll(feature.images)
        // Bildefil properties (filenames → full WFS URL)
        for (key in BILDEFIL_KEYS) {
            val filename =
                feature.props[key] ?: feature.props[key.lowercase()]
            if (!filename.isNullOrBlank()) {
                val url = if (filename.startsWith("http")) filename
                          else WFS_IMAGE_BASE_URL + filename.trimStart('/')
                urls.add(url)
            }
        }
        // Catch any other bildefil-like keys (case-insensitive)
        feature.props.forEach { (key, value) ->
            val lower = key.lowercase()
            if (lower.startsWith("bildefil") && lower !in BILDEFIL_KEYS && value.isNotBlank()) {
                val url = if (value.startsWith("http")) value
                          else WFS_IMAGE_BASE_URL + value.trimStart('/')
                urls.add(url)
            }
        }
        urls.toList()
    }

    // ── Display properties: exclude bildefil keys ──
    val displayProps = remember(feature) {
        feature.props.filter { (key, _) ->
            val lower = key.lowercase()
            lower !in BILDEFIL_KEYS && !lower.startsWith("bildefil")
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Layer name header ──
            Text(
                text = feature.layerName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(6.dp))

            // ── Image carousel ──
            if (imageUrls.isNotEmpty()) {
                FeatureImageCarousel(imageUrls)
                Spacer(Modifier.height(8.dp))
            }

            // ── Property rows ──
            displayProps.forEach { (key, value) ->
                val displayText = formatPropertyForDisplay(key, value)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .semantics { contentDescription = displayText },
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Image carousel
// ─────────────────────────────────────────────────────────────────

/**
 * Horizontal-scrolling row of lazy-loaded WFS building images.
 * Each image is clickable — opens the full-resolution image in the system browser.
 */
@Composable
private fun FeatureImageCarousel(imageUrls: List<String>) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        imageUrls.forEach { url ->
            NetworkImage(
                url = url,
                modifier = Modifier
                    .width(160.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Simple OkHttp-based async image loader
// ─────────────────────────────────────────────────────────────────

/**
 * Downloads an image from [url] via OkHttp and renders it as a Compose [Image].
 * Shows a loading spinner while downloading and a warning icon on failure.
 *
 * Uses OkHttp (already in the project) instead of Coil/Glide to avoid adding
 * a new dependency. Designed for occasional use in the popup (max 3 images).
 */
@Composable
private fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()
            if (bytes != null && bytes.isNotEmpty()) {
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            if (bitmap == null) error = true
        } catch (_: Exception) {
            error = true
        }
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surface,
            RoundedCornerShape(8.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Bilde",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            error -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Kunne ikke laste bilde",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
