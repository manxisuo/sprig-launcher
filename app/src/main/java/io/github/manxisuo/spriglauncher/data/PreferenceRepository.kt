package io.github.manxisuo.spriglauncher.data

import androidx.room.withTransaction
import io.github.manxisuo.spriglauncher.domain.LaunchableEntry
import kotlinx.coroutines.flow.Flow

class PreferenceRepository(private val database: AppDatabase) {
    val preferences: Flow<List<AppPreferenceEntity>> = database.dao().observePreferences()

    suspend fun togglePinned(entry: LaunchableEntry) = database.withTransaction {
        val dao = database.dao()
        val all = dao.preferences()
        val current = all.firstOrNull { it.entryId == entry.id }
        val order = if (current?.pinnedOrder == null) {
            (all.mapNotNull { it.pinnedOrder }.maxOrNull() ?: -1) + 1
        } else null
        dao.savePreference(
            current?.copy(pinnedOrder = order)
                ?: AppPreferenceEntity(entry.id, entry.packageName, pinnedOrder = order)
        )
    }

    suspend fun toggleExcluded(entry: LaunchableEntry) = database.withTransaction {
        val dao = database.dao()
        val all = dao.preferences()
        val next = all.none { it.packageName == entry.packageName && it.excluded }
        val matching = all.filter { it.packageName == entry.packageName }
        if (matching.isEmpty()) dao.savePreference(AppPreferenceEntity(entry.id, entry.packageName, excluded = next))
        else matching.forEach { dao.savePreference(it.copy(excluded = next)) }
    }

    suspend fun movePinned(entryId: String, delta: Int) = database.withTransaction {
        val dao = database.dao()
        val pinned = dao.preferences().filter { it.pinnedOrder != null }.sortedBy { it.pinnedOrder }
        val from = pinned.indexOfFirst { it.entryId == entryId }
        val to = (from + delta).coerceIn(0, pinned.lastIndex)
        if (from >= 0 && from != to) {
            val a = pinned[from]
            val b = pinned[to]
            dao.savePreference(a.copy(pinnedOrder = b.pinnedOrder))
            dao.savePreference(b.copy(pinnedOrder = a.pinnedOrder))
        }
    }
}
