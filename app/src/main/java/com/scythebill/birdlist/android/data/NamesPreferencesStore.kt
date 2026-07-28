package com.scythebill.birdlist.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scythebill.birdlist.model.taxa.names.NamesPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "names_preferences")
private val KEY_CLEMENTS_LOCALE = stringPreferencesKey("clements_locale")
private val KEY_SCIENTIFIC_OR_COMMON = stringPreferencesKey("scientific_or_common")

/** Mirrors the two persisted fields of [NamesPreferences], for Compose to observe. */
data class NamesPreferencesState(
    val clementsLocale: String,
    val scientificOrCommon: NamesPreferences.ScientificOrCommon,
)

/**
 * Owns the single, process-wide [NamesPreferences] instance (read directly
 * by [com.scythebill.birdlist.model.taxa.names.LocalNames] and by
 * `speciesIndexerGroups`), keeping it in sync with persisted choices and
 * exposing them as a [StateFlow] so Compose screens can key recomposition
 * off a locale/naming-mode change.
 */
class NamesPreferencesStore(private val context: Context, scope: CoroutineScope) {
    val namesPreferences = NamesPreferences()

    private val _state = MutableStateFlow(
        NamesPreferencesState(namesPreferences.clementsLocale, namesPreferences.scientificOrCommon)
    )
    val state: StateFlow<NamesPreferencesState> = _state.asStateFlow()

    init {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                val locale = prefs[KEY_CLEMENTS_LOCALE] ?: namesPreferences.clementsLocale
                val mode = prefs[KEY_SCIENTIFIC_OR_COMMON]?.let {
                    runCatching { NamesPreferences.ScientificOrCommon.valueOf(it) }.getOrNull()
                } ?: namesPreferences.scientificOrCommon
                namesPreferences.clementsLocale = locale
                namesPreferences.scientificOrCommon = mode
                _state.value = NamesPreferencesState(locale, mode)
            }
        }
    }

    suspend fun setClementsLocale(code: String) {
        context.dataStore.edit { it[KEY_CLEMENTS_LOCALE] = code }
    }

    suspend fun setScientificOrCommon(mode: NamesPreferences.ScientificOrCommon) {
        context.dataStore.edit { it[KEY_SCIENTIFIC_OR_COMMON] = mode.name }
    }
}
