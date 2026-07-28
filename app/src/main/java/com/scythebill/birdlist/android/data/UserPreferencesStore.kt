package com.scythebill.birdlist.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")
private val KEY_SELECTED_USER_ID = stringPreferencesKey("selected_user_id")

/** Persists the sticky "User preferences" selection (null id = "All users") across app
 * launches; [UserFilterStore] is responsible for reconciling it against the currently
 * loaded `.bsxm`'s available users. */
class UserPreferencesStore(private val context: Context) {
    val selectedUserIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_USER_ID]
    }

    suspend fun setSelectedUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) {
                prefs.remove(KEY_SELECTED_USER_ID)
            } else {
                prefs[KEY_SELECTED_USER_ID] = userId
            }
        }
    }
}
