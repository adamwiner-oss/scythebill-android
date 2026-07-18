package com.scythebill.birdlist.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "picked_file")
private val KEY_URI = stringPreferencesKey("picked_file_uri")

/** Persists the SAF/ACTION_VIEW `Uri` of the currently-picked `.bsxm` file. */
class PickedFilePreferences(private val context: Context) {
    val pickedUriFlow: Flow<String?> = context.dataStore.data.map { it[KEY_URI] }

    suspend fun setPickedUri(uriString: String) {
        context.dataStore.edit { it[KEY_URI] = uriString }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_URI) }
    }
}
