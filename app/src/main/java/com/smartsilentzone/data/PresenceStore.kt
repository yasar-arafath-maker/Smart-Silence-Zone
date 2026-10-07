package com.smartsilentzone.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.smartsilentzone.domain.RingerAction
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceStore @Inject constructor(private val ds: DataStore<Preferences>) {
    private val insideKey = stringSetPreferencesKey("confirmed_inside")
    private val lastAppliedKey = stringPreferencesKey("last_applied")

    val confirmedInside: Flow<Set<String>> = ds.data.map { it[insideKey] ?: emptySet() }
    val lastApplied: Flow<RingerAction?> = ds.data.map { p ->
        p[lastAppliedKey]?.let { runCatching { RingerAction.valueOf(it) }.getOrNull() }
    }

    suspend fun snapshotInside(): Set<String> = confirmedInside.first()
    suspend fun lastAppliedOnce(): RingerAction? = lastApplied.first()

    suspend fun markInside(id: String) = ds.edit { it[insideKey] = (it[insideKey] ?: emptySet()) + id }
    suspend fun markOutside(id: String) = ds.edit { it[insideKey] = (it[insideKey] ?: emptySet()) - id }

    /** Replaces the whole set. Used by [PresenceReconciler] after a reboot. */
    suspend fun setInside(ids: Set<String>) = ds.edit { it[insideKey] = ids }

    suspend fun setLastApplied(a: RingerAction) = ds.edit { it[lastAppliedKey] = a.name }
    suspend fun clearAll() = ds.edit { it.clear() }
}
