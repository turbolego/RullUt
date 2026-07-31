package com.turbolego.rullut2

import com.turbolego.rullut2.api.FeatureInfoParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the WMS GetFeatureInfo text/plain parser
 * and GetCapabilities XML parser.
 */
class FeatureInfoParserTest {

    @Test
    fun `parse single feature with properties`() {
        val raw = """
            FeatureId: gid_12345
            tittel: Rute 1
            tilgjengvurderingrulleman: Ikke tilgjengelig
            tilgjengvurderingrulleauto: Delvis tilgjengelig
            beskrivelse: Bratt stigning
            lengde_m: 250
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")

        assertEquals("Should parse 1 feature", 1, features.size)
        val f = features[0]
        assertEquals("Feature ID should match", "gid_12345", f.featureId)
        assertEquals("Layer name should match", "tilgjengelighet3", f.layerName)
        assertEquals("Props should contain tittel", "Rute 1", f.props["tittel"])
        assertEquals("Props should contain vurdering", "Ikke tilgjengelig", f.props["tilgjengvurderingrulleman"])
        assertEquals("Props should contain delvis", "Delvis tilgjengelig", f.props["tilgjengvurderingrulleauto"])
    }

    @Test
    fun `parse multiple features separated by delimiter`() {
        val raw = """
            --- tilgjengelighet3 ---
            FeatureId: gid_1
            tittel: Rute A
            --- tilgjengelighet3 ---
            FeatureId: gid_2
            tittel: Rute B
            tilgjengvurderingrulleman: Fullt tilgjengelig
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")

        assertEquals("Should parse 2 features", 2, features.size)
        assertEquals("First feature ID", "gid_1", features[0].featureId)
        assertEquals("Second feature ID", "gid_2", features[1].featureId)
        assertEquals("Second feature has accessibility data", "Fullt tilgjengelig", features[1].props["tilgjengvurderingrulleman"])
    }

    @Test
    fun `parse empty response returns empty list`() {
        val features = FeatureInfoParser.parseGetFeatureInfo("", "test")
        assertTrue("Empty input should return empty list", features.isEmpty())
    }

    @Test
    fun `parse response with only FeatureId`() {
        val raw = """
            FeatureId: gid_42
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "test")
        assertEquals("Should parse feature with just ID", 1, features.size)
        assertEquals("ID should match", "gid_42", features[0].featureId)
        assertTrue("Props should be empty", features[0].props.isEmpty())
    }

    @Test
    fun `parse response with no FeatureId`() {
        val raw = """
            tittel: Veg
            bredde: 2.5
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "test")
        assertEquals("Should parse feature without FeatureId", 1, features.size)
        assertEquals("Tittel should match", "Veg", features[0].props["tittel"])
    }

    @Test
    fun `parse response detects image URLs`() {
        val raw = """
            FeatureId: gid_99
            tittel: Sti med bilde
            bildelenke: https://wms.geonorge.no/wms.png
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "test")
        assertEquals("Should parse 1 feature", 1, features.size)
        assertTrue("Should detect image URL", features[0].images.isNotEmpty())
    }

      @Test
      fun `parse equals separated key value lines`() {
        val raw = """
          GetFeatureInfo results:
          Feature 1
          FeatureId=vei.12
          OBJID=abc-123
          BREDDE=260
          STIGNING=4.2
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "t_vei_r")

        assertEquals("Should parse one feature", 1, features.size)
        assertEquals("Should parse feature id", "vei.12", features[0].featureId)
        assertEquals("Should normalize key casing", "abc-123", features[0].props["objid"])
        assertEquals("Should parse width", "260", features[0].props["bredde"])
        assertEquals("Should parse slope", "4.2", features[0].props["stigning"])
      }

      @Test
      fun `parse no-result payload returns empty list`() {
        val raw = """
          GetFeatureInfo results:

            Search returned no results.
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")
        assertTrue("No-result payload should produce empty list", features.isEmpty())
      }

    @Test
    fun `parse capabilities xml basic structure`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <WMS_Capabilities version="1.3.0">
              <Capability>
                <Layer>
                  <Title>Tilgjengelighet</Title>
                  <Layer>
                    <Name>tilgjengelighet3</Name>
                    <Title>Tilgjengelighet 3</Title>
                  </Layer>
                  <Layer>
                    <Name>t_vei_r</Name>
                    <Title>Vei rullestol</Title>
                  </Layer>
                </Layer>
              </Capability>
            </WMS_Capabilities>
        """.trimIndent()

        val layers = FeatureInfoParser.parseCapabilitiesXml(xml)

        // The root Layer (abstract) should be skipped, children promoted
        assertTrue("Should have child layers", layers.isNotEmpty())
    }

    @Test
    fun `parse capabilities xml with multiple layers`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <WMS_Capabilities version="1.3.0">
              <Capability>
                <Layer>
                  <Title>Layer Group</Title>
                  <Layer>
                    <Name>layer_a</Name>
                    <Title>Layer A</Title>
                  </Layer>
                  <Layer>
                    <Name>layer_b</Name>
                    <Title>Layer B</Title>
                  </Layer>
                </Layer>
              </Capability>
            </WMS_Capabilities>
        """.trimIndent()

        val layers = FeatureInfoParser.parseCapabilitiesXml(xml)
        assertTrue("Should parse layers", layers.isNotEmpty())
        // Since abstract Layer promotes children directly
    }

    // ── Real Geonorge text/plain GetFeatureInfo format ──────────────────

    @Test
    fun `parse geonorge format with equals and quotes`() {
        val raw = """
            GetFeatureInfo results:

            Layer 't_vei_r'
              Feature 111291:
                objid = '111291'
                gatetype = 'Fortau'
                bredde = '350'
                stigning = '1'
                segmentlengde = '76.7'
                kommune = '5001'
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")

        assertEquals("Should parse 1 feature", 1, features.size)
        val f = features[0]
        assertEquals("Feature ID from header", "111291", f.featureId)
        assertEquals("Layer from section header", "t_vei_r", f.layerName)
        assertEquals("objid prop", "111291", f.props["objid"])
        assertEquals("gatetype prop", "Fortau", f.props["gatetype"])
        assertEquals("bredde prop", "350", f.props["bredde"])
        assertEquals("stigning prop", "1", f.props["stigning"])
        assertEquals("segmentlengde prop", "76.7", f.props["segmentlengde"])
        assertEquals("kommune prop", "5001", f.props["kommune"])
    }

    @Test
    fun `parse geonorge format multiple layers and features`() {
        val raw = """
            GetFeatureInfo results:

            Layer 't_vei_r'
              Feature 1:
                objid = '1'
                gatetype = 'Fortau'
                bredde = '300'

            Layer 't_vei_el'
              Feature 2:
                objid = '2'
                gatetype = 'Gangvei'
                bredde = '250'
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")

        assertEquals("Should parse 2 features", 2, features.size)
        assertEquals("First layer", "t_vei_r", features[0].layerName)
        assertEquals("First id", "1", features[0].featureId)
        assertEquals("Second layer", "t_vei_el", features[1].layerName)
        assertEquals("Second id", "2", features[1].featureId)
    }

    @Test
    fun `parse geonorge format skips blank values and header noise`() {
        val raw = """
            GetFeatureInfo results:

            Layer 't_vei_r'
              Feature 7:
                objid = '7'
                bildefil1 = ''
                kommentar = 'Har kolon: i verdien'
                bredde = '400'
        """.trimIndent()

        val features = FeatureInfoParser.parseGetFeatureInfo(raw, "tilgjengelighet3")

        assertEquals("Should parse 1 feature", 1, features.size)
        val f = features[0]
        assertNull("Blank values are skipped", f.props["bildefil1"])
        assertEquals("Colon inside value preserved", "Har kolon: i verdien", f.props["kommentar"])
        assertEquals("bredde prop", "400", f.props["bredde"])
    }

    @Test
    fun `parse geonorge no results response returns empty`() {
        val features = FeatureInfoParser.parseGetFeatureInfo(
            "GetFeatureInfo results:\n\n  Search returned no results.", "test"
        )
        assertTrue("No results should be empty", features.isEmpty())
    }
}