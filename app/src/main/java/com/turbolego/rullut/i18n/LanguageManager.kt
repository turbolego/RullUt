package com.turbolego.rullut.i18n

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore for language preference. */
private val Context.langStore by preferencesDataStore(name = "language_settings")

private val KEY_LANG = stringPreferencesKey("app_language")

/**
 * Manages app language preference persisted in DataStore.
 * Also updates [Strings.lang] whenever the language changes so all
 * string lookups reflect the current choice immediately.
 */
object LanguageManager {

    /**
     * Observe the persisted language code as a Flow.
     * Emits "nb" by default if never set.
     */
    fun observeLanguage(context: Context): Flow<String> {
        return context.langStore.data.map { prefs ->
            prefs[KEY_LANG] ?: "nb"
        }
    }

    /**
     * Set the language and update [Strings.lang] for immediate UI effect.
     */
    suspend fun setLanguage(context: Context, code: String) {
        context.langStore.edit { prefs ->
            prefs[KEY_LANG] = code
        }
        // Update the live string table reference
        Strings.lang = langFromCode(code)
    }

    /**
     * Get the current language code synchronously (for app init).
     */
    suspend fun getLanguage(context: Context): String {
        val code = context.langStore.data.first()[KEY_LANG] ?: "nb"
        Strings.lang = langFromCode(code)
        return code
    }

    /**
     * Resolve a language code to a [Lang] enum, defaulting to NB.
     */
    fun langFromCode(code: String): Lang {
        return Lang.entries.firstOrNull { it.code == code } ?: Lang.NB
    }
}
