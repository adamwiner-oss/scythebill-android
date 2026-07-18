package com.scythebill.birdlist.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.scythebill.birdlist.android.ui.query.QueryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "query_preferences")
private val KEY_COUNT_INTRODUCED = booleanPreferencesKey("count_introduced")
private val KEY_COUNT_HEARD_ONLY = booleanPreferencesKey("count_heard_only")
private val KEY_COUNT_RESTRAINED = booleanPreferencesKey("count_restrained")
private val KEY_COUNT_UNDESCRIBED = booleanPreferencesKey("count_undescribed")

/** Persists the user's [QueryPreferences] choices across app launches. */
class QueryPreferencesStore(private val context: Context) {
    private val defaults = QueryPreferences.DEFAULT

    val preferencesFlow: Flow<QueryPreferences> = context.dataStore.data.map { prefs ->
        QueryPreferences(
            countIntroduced = prefs[KEY_COUNT_INTRODUCED] ?: defaults.countIntroduced,
            countHeardOnly = prefs[KEY_COUNT_HEARD_ONLY] ?: defaults.countHeardOnly,
            countRestrained = prefs[KEY_COUNT_RESTRAINED] ?: defaults.countRestrained,
            countUndescribed = prefs[KEY_COUNT_UNDESCRIBED] ?: defaults.countUndescribed,
        )
    }

    suspend fun update(preferences: QueryPreferences) {
        context.dataStore.edit {
            it[KEY_COUNT_INTRODUCED] = preferences.countIntroduced
            it[KEY_COUNT_HEARD_ONLY] = preferences.countHeardOnly
            it[KEY_COUNT_RESTRAINED] = preferences.countRestrained
            it[KEY_COUNT_UNDESCRIBED] = preferences.countUndescribed
        }
    }
}
