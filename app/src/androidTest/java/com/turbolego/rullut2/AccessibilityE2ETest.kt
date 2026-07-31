package com.turbolego.rullut2

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turbolego.rullut2.i18n.Strings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility-focused E2E instrumentation tests.
 *
 * These tests run against the real [MainActivity] and verify that major
 * app features expose discoverable, operable UI for assistive tech users.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mapAndPrimaryActions_haveAccessibleLabels() {
        onNodeWithAnyContentDescription(
            "Tilgjengelighetskart over Norge. Viser rullestol-ruter og universell utforming.",
            "Accessibility map of Norway. Showing wheelchair routes and universal design features.",
        ).assertIsDisplayed()

        onNodeWithAnyContentDescription(
            "Gå til min posisjon",
            "Go to my location",
        ).assertIsDisplayed()

        onNodeWithAnyContentDescription(
            "Søk etter sted",
            "Search place",
        ).assertIsDisplayed()

        onNodeWithAnyContentDescription(
            "Planlegg rute",
            "Plan route",
        ).assertIsDisplayed()

        onNodeWithAnyContentDescription(
            "Innstillinger",
            "Settings",
        ).assertIsDisplayed()

        onNodeWithAnyContentDescription("Finn nærliggende toaletter").assertIsDisplayed()
        onNodeWithAnyContentDescription("Objekter i visning").assertIsDisplayed()
        onNodeWithAnyContentDescription("Highscore").assertIsDisplayed()
    }

    @Test
    fun settingsPanel_isReachableAndOperable() {
        onNodeWithAnyContentDescription(
            "Innstillinger",
            "Settings",
        ).performClick()

        onNodeWithAnyText(Strings.settingsTitle).assertIsDisplayed()
        onNodeWithAnyText(Strings.settingsLanguage).assertIsDisplayed()
        onNodeWithAnyText(Strings.settingsBasemap).assertIsDisplayed()
        onNodeWithAnyText(Strings.settingsLayers).assertIsDisplayed()

        onNodeWithAnyContentDescription(
            "Lukk innstillinger",
            "Close settings",
        ).performClick()
    }

    @Test
    fun searchModal_isReachableAndDismissible() {
        onNodeWithAnyContentDescription(
            "Søk etter sted",
            "Search place",
        ).performClick()

        onNodeWithAnyText(Strings.searchTitle).assertIsDisplayed()
        onNodeWithAnyText(Strings.settingsCloseLabel).assertIsDisplayed()
        onNodeWithAnyContentDescription(
            "Lukk søk",
            "Close search",
        ).assertIsDisplayed()

        onNodeWithAnyText(Strings.settingsCloseLabel).performClick()
    }

    @Test
    fun routePlannerModal_hasCoreA11yElements() {
        onNodeWithAnyContentDescription(
            "Planlegg rute",
            "Plan route",
        ).performClick()

        onNodeWithAnyText(Strings.routeTitle).assertIsDisplayed()
        onNodeWithAnyText(Strings.routeFrom).assertIsDisplayed()
        onNodeWithAnyText(Strings.routeTo).assertIsDisplayed()
        onNodeWithAnyText(Strings.routeCalculate).assertIsDisplayed()

        dismissModalWithBack()
    }

    @Test
    fun toiletViewportAndHighscoreFeatures_areReachable() {
        // Toilets dialog
        onNodeWithAnyContentDescription("Finn nærliggende toaletter").performClick()
        onNodeWithAnyText(Strings.toiletTitle).assertIsDisplayed()
        onNodeWithAnyText(Strings.settingsCloseLabel).performClick()

        // Viewport scanner sheet
        onNodeWithAnyContentDescription("Objekter i visning").performClick()
        onNodeWithAnyText("Søker...", "Objekter i visning").assertIsDisplayed()
        dismissModalWithBack()

        // Highscore sheet
        onNodeWithAnyContentDescription("Highscore").performClick()
        onNodeWithAnyText("Tilgjengelighet — Highscore").assertIsDisplayed()
        dismissModalWithBack()
    }

    private fun dismissModalWithBack() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun onNodeWithAnyContentDescription(vararg descriptions: String) =
        composeRule.onNode(
            descriptions
                .map { androidx.compose.ui.test.hasContentDescription(it) }
                .reduce(SemanticsMatcher::or),
            useUnmergedTree = true,
        )

    private fun onNodeWithAnyText(vararg labels: String) =
        composeRule.onNode(
            labels
                .map { androidx.compose.ui.test.hasText(it) }
                .reduce(SemanticsMatcher::or),
            useUnmergedTree = true,
        )
}
