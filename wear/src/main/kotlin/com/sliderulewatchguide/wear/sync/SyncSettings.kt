package com.sliderulewatchguide.wear.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bezelSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "bezel_sync")

/**
 * Persisted user preference for the bezel-sync feature. A single
 * boolean — "Sync bezel with watch/phone" — defaulting to true (the
 * feature self-activates when a paired partner is detected; this
 * toggle is the explicit OFF override).
 */
class SyncSettings(context: Context) {
    private val store = context.applicationContext.bezelSyncDataStore

    val syncEnabled: Flow<Boolean> = store.data.map { it[KEY_ENABLED] ?: true }

    suspend fun setSyncEnabled(enabled: Boolean) {
        store.edit { it[KEY_ENABLED] = enabled }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("sync_enabled")
    }
}
