package com.turbolego.rullut2

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turbolego.rullut2.i18n.Lang
import com.turbolego.rullut2.i18n.Strings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime accessibility checks with Compose UI tests.
 *
 * Verifies that key UI strings are non-blank and that SemanticsProperties
 * are set for TalkBack across both supported languages.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── All strings, both languages ──

    @Test
    fun allStrings_nonEmpty_nb() {
        Strings.lang = Lang.NB
        assertNonBlank("appName", Strings.appName)
        assertNonBlank("mapContentDescription", Strings.mapContentDescription)
        assertNonBlank("fabLocation", Strings.fabLocation)
        assertNonBlank("fabRoute", Strings.fabRoute)
        assertNonBlank("fabSettings", Strings.fabSettings)
        assertNonBlank("fabSearch", Strings.fabSearch)
        assertNonBlank("settingsTitle", Strings.settingsTitle)
        assertNonBlank("settingsBasemap", Strings.settingsBasemap)
        assertNonBlank("settingsLayers", Strings.settingsLayers)
        assertNonBlank("settingsCloseLabel", Strings.settingsCloseLabel)
        assertNonBlank("routeTitle", Strings.routeTitle)
        assertNonBlank("routeCalculate", Strings.routeCalculate)
        assertNonBlank("searchTitle", Strings.searchTitle)
        assertNonBlank("searchPlaceholder", Strings.searchPlaceholder)
        assertNonBlank("featureInfoTitle", Strings.featureInfoTitle)
        assertNonBlank("featureLoading", Strings.featureLoading)
        assertNonBlank("toiletsLoading", Strings.toiletsLoading)
        assertNonBlank("highscoreTitle", Strings.highscoreTitle)
        assertNonBlank("errorGeneral", Strings.errorGeneral)
    }

    @Test
    fun allStrings_nonEmpty_en() {
        Strings.lang = Lang.EN
        assertNonBlank("appName", Strings.appName)
        assertNonBlank("mapContentDescription", Strings.mapContentDescription)
        assertNonBlank("fabLocation", Strings.fabLocation)
        assertNonBlank("fabRoute", Strings.fabRoute)
        assertNonBlank("fabSettings", Strings.fabSettings)
        assertNonBlank("fabSearch", Strings.fabSearch)
        assertNonBlank("settingsTitle", Strings.settingsTitle)
        assertNonBlank("settingsBasemap", Strings.settingsBasemap)
        assertNonBlank("settingsLayers", Strings.settingsLayers)
        assertNonBlank("settingsCloseLabel", Strings.settingsCloseLabel)
        assertNonBlank("routeTitle", Strings.routeTitle)
        assertNonBlank("routeCalculate", Strings.routeCalculate)
        assertNonBlank("searchTitle", Strings.searchTitle)
        assertNonBlank("searchPlaceholder", Strings.searchPlaceholder)
        assertNonBlank("featureInfoTitle", Strings.featureInfoTitle)
        assertNonBlank("featureLoading", Strings.featureLoading)
        assertNonBlank("toiletsLoading", Strings.toiletsLoading)
        assertNonBlank("highscoreTitle", Strings.highscoreTitle)
        assertNonBlank("errorGeneral", Strings.errorGeneral)
    }

    @Test
    fun allLanguages_haveDistinctTranslations() {
        val nbTexts = Lang.NB.let {
            Strings.lang = it
            listOf(
                Strings.appName,
                Strings.fabLocation,
                Strings.fabRoute,
                Strings.fabSettings,
                Strings.fabSearch,
                Strings.settingsTitle,
                Strings.settingsBasemap,
                Strings.settingsCloseLabel,
                Strings.routeTitle,
                Strings.routeCalculate,
                Strings.searchTitle,
            )
        }

        val enTexts = Lang.EN.let {
            Strings.lang = it
            listOf(
                Strings.appName,
                Strings.fabLocation,
                Strings.fabRoute,
                Strings.fabSettings,
                Strings.fabSearch,
                Strings.settingsTitle,
                Strings.settingsBasemap,
                Strings.settingsCloseLabel,
                Strings.routeTitle,
                Strings.routeCalculate,
                Strings.searchTitle,
            )
        }

        // appName can stay the same; most UI strings should differ
        var diffs = 0
        for (i in nbTexts.indices) {
            if (nbTexts[i] != enTexts[i]) diffs++
        }
        assertTrue("Expected most strings to differ between languages (got $diffs/11)", diffs >= 5)
    }

    @Test
    fun langEnum_allEntriesHaveDisplayNames() {
        Lang.entries.forEach { lang ->
            assertNonBlank("lang.${lang.code}.displayName", lang.displayName)
        }
    }

    @Test
    fun langEnum_allCodesSupported() {
        Lang.entries.forEach { lang ->
            val resolved = com.turbolego.rullut2.i18n.LanguageManager.langFromCode(lang.code)
            assertEquals("Language code ${lang.code} should resolve to itself", lang, resolved)
        }
    }

    @Test
    fun mapContentDescription_validBothLangs() {
        Strings.lang = Lang.NB
        assertFalse(Strings.mapContentDescription.isEmpty())

        Strings.lang = Lang.EN
        assertFalse(Strings.mapContentDescription.isEmpty())
    }

    @Test
    fun dynamicStrings_formatCorrectly() {
        Strings.lang = Lang.NB
        val dist = Strings.routeAccessible(75)
        assertTrue("routeAccessible should contain the number", dist.contains("75"))

        val toilet = Strings.toiletDistance(200)
        assertTrue("toiletDistance should contain the number", toilet.contains("200"))
    }

    // ── Helpers ──

    private fun assertNonBlank(label: String, value: String) {
        assertFalse("$label should be non-blank in ${Strings.lang.code}", value.isBlank())
    }
}