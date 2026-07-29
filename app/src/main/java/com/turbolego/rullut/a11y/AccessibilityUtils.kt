package com.turbolego.rullut.a11y

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

/**
 * Accessibility utilities for TalkBack support.
 *
 * The app's core mission is accessibility (WCAG 2.1 AA).
 * This file provides helpers to ensure all map features,
 * controls, and route data are accessible via screen readers.
 */
object AccessibilityUtils {

    /**
     * Send an accessibility announcement via TalkBack.
     * Use for ephemeral messages like "Route computed" or "Feature selected".
     */
    fun announce(context: Context, message: String) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am?.isEnabled != true) return

        val event = AccessibilityEvent.obtain(
            AccessibilityEvent.TYPE_ANNOUNCEMENT
        )
        event.text.add(message)
        event.packageName = context.packageName
        event.className = "com.turbolego.rullut.MainActivity"
        try {
            am.sendAccessibilityEvent(event)
        } catch (_: Exception) {
        }
    }

    /**
     * Format a feature property for screen reader consumption.
     * Converts "tilgjengvurderingrulleman" to "Tilgjengelighet rullestol manuell"
     * and appends the value in Norwegian.
     */
    fun formatPropertyForTalkBack(key: String, value: String): String {
        val readableKey = when (key.lowercase()) {
            "tilgjengvurderingrulleman" -> "Tilgjengelighet for manuell rullestol"
            "tilgjengvurderingrulleauto" -> "Tilgjengelighet for elektrisk rullestol"
            "tilgjengvurderingelrull" -> "Tilgjengelighet for el-rullator"
            "tilgjengvurderingsyn" -> "Tilgjengelighet for synshemmede"
            "tittel" -> "Navn"
            "navn" -> "Navn"
            "beskrivelse" -> "Beskrivelse"
            "veiklasse" -> "Veiklasse"
            "hastighet" -> "Hastighet"
            "overflatetype" -> "Overflatetype"
            "bredde" -> "Bredde"
            "stigning" -> "Stigning"
            "belysning" -> "Belysning"
            else -> key
        }

        val readableValue = when (value.lowercase()) {
            "ja" -> "Ja"
            "nei" -> "Nei"
            "ikke tilgjengelig" -> "Ikke tilgjengelig"
            "delvis tilgjengelig" -> "Delvis tilgjengelig"
            "fullt tilgjengelig" -> "Fullt tilgjengelig"
            else -> value
        }

        return "$readableKey: $readableValue"
    }
}

/**
 * Modifier extension for TalkBack-optimised semantic descriptions.
 * Use on route segments to make them accessible.
 */
fun Modifier.talkBackDescription(description: String): Modifier = this.semantics {
    contentDescription = description
}

/**
 * Modifier for heading semantics on screen sections.
 */
fun Modifier.talkBackHeading(): Modifier = this.semantics {
    heading()
}