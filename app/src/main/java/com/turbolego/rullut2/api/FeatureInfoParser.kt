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
 * GetFeatureInfo response format (text/plain GML) — Geonorge MapServer:
 *   GetFeatureInfo results:
 *
 *   Layer 't_vei_r'
 *     Feature 111291:
 *       objid = '111291'
 *       key = 'value'
 *
 * Legacy format (still supported):
 *   --- layerName ---
 *   FeatureId: id
 *   key: value
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
     *
     * Supports both the real Geonorge WMS format and the legacy format:
     *
     * Geonorge (MapServer msGMLOutput text/plain):
     *   GetFeatureInfo results:
     *
     *   Layer 't_vei_r'
     *     Feature 111291:
     *       objid = '111291'
     *       gatetype = 'Fortau'
     *       bredde = '350'
     *       ...
     *
     * Legacy:
     *   --- layerName ---
     *   FeatureId: gid_123
     *   Key1: Value1
     *   ...
     */
    fun parseGetFeatureInfo(raw: String, layerName: String): List<FeatureInfo> {
        if (raw.isBlank()) return emptyList()

        val features = mutableListOf<FeatureInfo>()
        var currentLayer = layerName
        var currentId: String? = null
        val currentProps = mutableMapOf<String, String>()
        val currentImages = mutableListOf<String>()

        fun flush() {
            if (currentId != null || currentProps.isNotEmpty() || currentImages.isNotEmpty()) {
                features.add(
                    FeatureInfo(
                        layerName = currentLayer,
                        featureId = currentId ?: "",
                        props = currentProps.toMap(),
                        images = currentImages.toList(),
                    )
                )
            }
            currentId = null
            currentProps.clear()
            currentImages.clear()
        }

        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // "Layer 't_vei_r'" — Geonorge section header; starts a new layer group
            val layerMatch = Regex("^Layer\\s+'(.+)'$").find(line)
            if (layerMatch != null) {
                flush()
                currentLayer = layerMatch.groupValues[1]
                continue
            }

            // "Feature 111291:" / "FeatureId: gid_123" / "FeatureId=gid_123" — starts a new feature
            val featureMatch = Regex(
                "^Feature(?:Id)?(?:\\s+(\\d+))?\\s*[=:]\\s*(.*)$",
                RegexOption.IGNORE_CASE,
            ).find(line)
            if (featureMatch != null) {
                flush()
                currentId = (featureMatch.groupValues[1].takeIf { it.isNotBlank() }
                    ?: featureMatch.groupValues[2])
                    .trim().ifEmpty { null }
                continue
            }

            // Property lines. Geonorge uses "key = 'value'", legacy uses "key: value".
            // Try '=' first so colons inside values (e.g. timestamps) don't split the key.
            val eqMatch = Regex("^([^=:]+)=\\s*(.*)$").find(line)
            val colonMatch = eqMatch ?: Regex("^([^:]+):\\s*(.*)$").find(line)
            if (colonMatch != null) {
                val key = colonMatch.groupValues[1].trim().lowercase()
                val value = colonMatch.groupValues[2].trim().trim('\'', '"')
                if (key.isEmpty() || value.isBlank()) continue
                if (value.startsWith("http") &&
                    (value.contains(".png") || value.contains(".jpg") || value.contains("wms"))
                ) {
                    currentImages.add(value)
                } else {
                    currentProps[key] = value
                }
            }
            // Anything else (e.g. "GetFeatureInfo results:", "Search returned no results.")
            // is ignored.
        }
        flush()
        return features
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
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            // Navigate to the Capability/Layer tree
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                    parser.name == "Layer" &&
                    parser.depth == 3 // WMS_Capabilities > Capability > Layer
                ) {
                    parseLayerTree(parser).let { root ->
                        if (root != null) {
                            // Abstract root (no Name) — promote its children
                            if (root.name.isNotEmpty()) layers.add(root) else layers.addAll(root.children)
                        }
                    }
                    break
                }
                eventType = parser.next()
            }
        } catch (_: XmlPullParserException) {
        } catch (_: Exception) {
        }
        return layers
    }

    /**
     * Recursively parse a <Layer> element.
     * Returns a LayerInfo; abstract groups (no <Name>) get an empty name so
     * the caller can promote their children.
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
                    if (child != null) {
                        // Abstract child (no Name) — promote its children
                        if (child.name.isNotEmpty()) children.add(child) else children.addAll(child.children)
                    }
                }
                eventType == XmlPullParser.END_TAG && parser.name == "Layer" -> {
                    break
                }
            }
            eventType = parser.next()
        }

        // If this layer has a name, return it with children.
        // If no name, this is an abstract group — still return it (with
        // name empty) so the caller can promote its children.
        return LayerInfo(
            name = name ?: "",
            title = title ?: name ?: "",
            legendUrl = legendUrl,
            children = children,
        )
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
