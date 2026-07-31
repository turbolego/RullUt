package com.turbolego.rullut2.api

import com.turbolego.rullut2.model.FeatureInfo
import com.turbolego.rullut2.model.LayerInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Parsers for WMS GetFeatureInfo responses and GetCapabilities XML.
 *
 * GetFeatureInfo response format (text/plain GML):
 *   --- layerName ---
 *   FeatureId: id
 *   key: value
 *   ...
 *
 * GetCapabilities response format (XML):
 *   <WMS_Capabilities>
 *     <Capability>
 *       <Layer>
 *         <Name>...</Name>
 *         <Title>...</Title>
 *         ...
 */
object FeatureInfoParser {

    private const val TAG = "FeatureInfoParser"
    private const val TIMEOUT_MS = 10_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Parse the text/plain GetFeatureInfo response.
     * Format from Geonorge WMS:
     *   --- layerName ---
     *   FeatureId: gid_123
     *   Key1: Value1
     *   Key2: Value2
     *   ...
     *   (blank line separates features)
     */
    fun parseGetFeatureInfo(raw: String, layerName: String): List<FeatureInfo> {
        if (raw.isBlank()) return emptyList()

        val features = mutableListOf<FeatureInfo>()
        val currentBlock = mutableListOf<String>()

        fun flushBlock() {
            if (currentBlock.isEmpty()) return
            parseFeatureBlock(currentBlock.joinToString("\n"), layerName)?.let { features.add(it) }
            currentBlock.clear()
        }

        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushBlock()
                continue
            }

            if (trimmed.equals("GetFeatureInfo results:", ignoreCase = true) ||
                trimmed.contains("Search returned no results", ignoreCase = true)
            ) {
                continue
            }

            if (trimmed.startsWith("---") ||
                Regex("""(?i)^feature\s+\d+\b""").containsMatchIn(trimmed) ||
                Regex("""(?i)^layer\s+['\"].*['\"]\s*$""").containsMatchIn(trimmed)
            ) {
                flushBlock()
                continue
            }

            currentBlock.add(trimmed)
        }

        flushBlock()

        if (features.isNotEmpty()) return features

        // Fallback for unconventional payloads with no separators.
        return listOfNotNull(parseFeatureBlock(raw, layerName))
    }

    private fun parseFeatureBlock(block: String, layerName: String): FeatureInfo? {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        var featureId = ""
        val props = mutableMapOf<String, String>()
        val images = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("FeatureId:", ignoreCase = true) ||
                    line.startsWith("FeatureId=", ignoreCase = true) -> {
                    featureId = if (line.contains(':')) {
                        line.substringAfter(":").trim()
                    } else {
                        line.substringAfter("=").trim()
                    }
                }
                line.contains("FeatureId", ignoreCase = true) &&
                    (line.contains(":") || line.contains("=")) -> {
                    // Some WMS servers use "FeatureId=..."
                    val featurePart = if (line.contains(':')) {
                        line.substringAfter(":")
                    } else {
                        line.substringAfter("=")
                    }
                    val id = featurePart.substringBefore(",").trim()
                    if (featureId.isEmpty()) featureId = id
                }
                line.contains(':') || line.contains('=') -> {
                    val separator = if (line.contains(':')) ':' else '='
                    val key = line.substringBefore(separator).trim().lowercase()
                    val value = line.substringAfter(separator).trim().trim('"')
                    if (key.isNotBlank() && value.isNotBlank()) {
                        // Detect image URLs
                        if (value.startsWith("http") &&
                            (value.contains(".png") || value.contains(".jpg") || value.contains("wms"))
                        ) {
                            images.add(value)
                        } else {
                            props[key] = value
                        }
                    }
                }
            }
        }

        if (props.isEmpty() && featureId.isEmpty()) return null

        return FeatureInfo(
            layerName = layerName,
            featureId = featureId,
            props = props,
            images = images,
        )
    }

    /**
     * Fetch and parse WMS GetCapabilities XML.
     * Returns a tree of LayerInfo objects.
     */
    suspend fun fetchCapabilities(): List<LayerInfo> {
        val url = "https://wms.geonorge.no/skwms1/wms.tilgjengelighet3?request=GetCapabilities&service=WMS"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RullUt/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("GetCapabilities HTTP ${response.code}")
        }
        val xml = response.body?.string() ?: return emptyList()
        return parseCapabilitiesXml(xml)
    }

    /**
     * Parse WMS Capabilities XML using XmlPullParser.
     * Extracts the <Layer> tree with <Name>, <Title>, <LegendURL>.
     */
    fun parseCapabilitiesXml(xml: String): List<LayerInfo> {
        val layers = mutableListOf<LayerInfo>()
        val layerRegex = Regex(
            pattern = """
                <Layer>\s*
                .*?<Name>(.*?)</Name>\s*
                .*?<Title>(.*?)</Title>
                .*?</Layer>
            """.trimIndent(),
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

        layerRegex.findAll(xml).forEach { match ->
            val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val title = match.groupValues.getOrNull(2)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: name
            if (name.isNotBlank()) {
                layers.add(
                    LayerInfo(
                        name = name,
                        title = title,
                        legendUrl = null,
                        children = emptyList(),
                    )
                )
            }
        }

        return layers
    }

    /**
     * Recursively parse a <Layer> element.
     * Returns a LayerInfo for this layer if it has a <Name>,
     * otherwise returns null (abstract grouping layer).
     */
    private fun parseLayerTree(parser: XmlPullParser): LayerInfo? {
        var name: String? = null
        var title: String? = null
        var legendUrl: String? = null
        val children = mutableListOf<LayerInfo>()

        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.START_TAG && parser.name == "Name" -> {
                    name = parser.nextText().trim()
                }
                eventType == XmlPullParser.START_TAG && parser.name == "Title" -> {
                    title = parser.nextText().trim()
                }
                eventType == XmlPullParser.START_TAG && parser.name == "LegendURL" -> {
                    // Look for OnlineResource in LegendURL
                    legendUrl = parseLegendUrl(parser)
                }
                eventType == XmlPullParser.START_TAG && parser.name == "Layer" -> {
                    val child = parseLayerTree(parser)
                    if (child != null) children.add(child)
                }
                eventType == XmlPullParser.END_TAG && parser.name == "Layer" -> {
                    break
                }
            }
            eventType = parser.next()
        }

        // If this layer has a name, return it with children.
        // If no name, this is an abstract group — promote children.
        return if (name != null || children.isNotEmpty()) {
            LayerInfo(
                name = name ?: title ?: "",
                title = title ?: name ?: "",
                legendUrl = legendUrl,
                children = children,
            )
        } else {
            null
        }
    }

    private fun parseLegendUrl(parser: XmlPullParser): String? {
        var url: String? = null
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG &&
                parser.name == "OnlineResource"
            ) {
                val baseUrl = parser.getAttributeValue(null, "xlink:href")
                    ?: parser.getAttributeValue(null, "href")
                if (baseUrl != null) url = baseUrl
                parser.next() // read end tag
            }
            if (eventType == XmlPullParser.END_TAG && parser.name == "LegendURL") break
            eventType = parser.next()
        }
        return url
    }
}