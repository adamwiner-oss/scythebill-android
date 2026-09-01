package com.scythebill.birdlist.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

/** Lightweight id/display-name pair for a user, cheap to persist and read back without
 * reparsing the source `.bsxm` file. */
data class UserDescriptor(val id: String, val name: String)

/**
 * Tracks the users available from the currently loaded `.bsxm`'s `UserSet` (empty if it has
 * none), and which one, if any, all queries should currently be limited to. Selecting a user is
 * sticky across app relaunches and file reloads via [UserPreferencesStore], but this store is
 * responsible for reconciling that choice against the users actually available in the
 * currently loaded file.
 */
class UserFilterStore(private val preferencesStore: UserPreferencesStore) {
    private val _availableUsers = MutableStateFlow<List<UserDescriptor>>(emptyList())
    val availableUsers: StateFlow<List<UserDescriptor>> = _availableUsers.asStateFlow()

    private val _selectedUserId = MutableStateFlow<String?>(null)
    val selectedUserId: StateFlow<String?> = _selectedUserId.asStateFlow()

    /** Called after each `.bsxm` load (fresh parse or cache-hit fast path) with the users
     * available in the just-loaded file (empty if it has no `UserSet`). Reverts the current
     * selection to "All users" if the persisted choice isn't among them. */
    suspend fun setAvailableUsers(users: List<UserDescriptor>) {
        _availableUsers.value = users
        val persistedId = preferencesStore.selectedUserIdFlow.firstOrNull()
        _selectedUserId.value = persistedId?.takeIf { id -> users.any { it.id == id } }
    }

    suspend fun selectUser(userId: String?) {
        _selectedUserId.value = userId
        preferencesStore.setSelectedUserId(userId)
    }
}
