package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.activeTriggerDataStore by preferencesDataStore(
    name = "nexaflow_active_triggers",
    // If a crash mid-write corrupts the file, reset to an empty ledger instead
    // of throwing on every startup read (same guard as the other stores).
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Durable record of which automations are currently in their "triggered" state,
 * per event source.
 *
 * Monitors (battery, connectivity, ringer, …) keep their active state in memory
 * so a condition flip can fire the task's exit behavior ("when the task ends").
 * But that state is lost on every process/service restart (reboot, swipe from
 * recents, OEM kill, Android 15 timeout) — after a restart the monitors would
 * no longer know a task was active, so when the condition later ended the exit
 * behavior silently never ran. This store makes the active state durable:
 * monitors persist a key on fire, clear it on exit, and re-arm from here on
 * [ActiveTriggerStore.activeKeys] when they initialize.
 *
 * Keys are opaque to the store — `"<source>|<monitorKey>"` — so a monitor can
 * store whatever it needs to reconstruct its state (e.g. a Bluetooth address or
 * the package that triggered a notification).
 *
 * Every [markActive] also records the write timestamp, so [purgeExpired] (run
 * at boot, before the monitors re-arm) can drop keys that are so old they can
 * only be crash leftovers — without timestamps a trigger armed days ago would
 * fire its exit behavior days later on the next boot. Keys written before this
 * stamping existed carry no timestamp and are kept (their age is unknowable),
 * so upgrading never silently cancels an in-flight exit.
 */
class ActiveTriggerStore(
    private val context: Context,
    // Injectable clock so tests can age keys without sleeping.
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private val dataStore = context.activeTriggerDataStore

    /** Marks the automation (under this monitor's key) as currently triggered. */
    suspend fun markActive(source: String, monitorKey: String) {
        val key = keyOf(source, monitorKey)
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = (prefs[KEY_ACTIVE] ?: emptySet()) + key
            prefs[KEY_STAMPS] = (prefs[KEY_STAMPS] ?: emptySet())
                .filterNot { entryKey(it) == key }.toSet() + stampEntry(key)
        }
    }

    /** Clears the active mark; safe to call when nothing was marked. */
    suspend fun clearActive(source: String, monitorKey: String) {
        val key = keyOf(source, monitorKey)
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = (prefs[KEY_ACTIVE] ?: emptySet()) - key
            prefs[KEY_STAMPS] = (prefs[KEY_STAMPS] ?: emptySet())
                .filterNot { entryKey(it) == key }.toSet()
        }
    }

    /**
     * Clears every active mark belonging to this automation — the exact
     * [monitorKey] or any composite key under it (`id`, `id|plugType`, …).
     */
    suspend fun clearAutomation(source: String, automationId: String) {
        val prefix = "$automationId|"
        val stale = activeKeys(source).filter {
            it == automationId || it.startsWith(prefix)
        }
        if (stale.isEmpty()) return
        val staleFullKeys = stale.map { keyOf(source, it) }.toSet()
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = (prefs[KEY_ACTIVE] ?: emptySet())
                .minus(staleFullKeys)
            prefs[KEY_STAMPS] = (prefs[KEY_STAMPS] ?: emptySet())
                .filterNot { entryKey(it) in staleFullKeys }.toSet()
        }
    }

    /**
     * All monitor keys currently marked active for this source, excluding any
     * that have outlived [DEFAULT_MAX_ACTIVE_AGE_MS] (only possible when the
     * boot-time [purgeExpired] did not run, e.g. the store is read by a non-
     * service caller). Keys without a timestamp are always returned.
     */
    suspend fun activeKeys(source: String): Set<String> {
        val prefix = "$source|"
        val data = dataStore.data.first()
        val active = data[KEY_ACTIVE].orEmpty()
            .filter { it.startsWith(prefix) }
        val stamps = data[KEY_STAMPS].orEmpty()
            .associate { entryKey(it) to entryMillis(it) }
        val cutoff = nowMillis() - DEFAULT_MAX_ACTIVE_AGE_MS
        return active
            .filter { key ->
                val written = stamps[key] ?: return@filter true // legacy, unknown age
                written >= cutoff
            }
            .map { it.removePrefix(prefix) }
            .toSet()
    }

    /**
     * Deletes every active mark older than [maxAgeMillis] (default 7 days) and
     * returns how many were dropped. Called once at boot, before the monitors
     * re-arm from the ledger, so a key armed days ago (device crashed, trigger
     * never exited) can never fire a late exit on the next boot.
     */
    suspend fun purgeExpired(maxAgeMillis: Long = DEFAULT_MAX_ACTIVE_AGE_MS): Int {
        val data = dataStore.data.first()
        val active = data[KEY_ACTIVE].orEmpty()
        if (active.isEmpty()) return 0
        val stamps = data[KEY_STAMPS].orEmpty()
            .associate { entryKey(it) to entryMillis(it) }
        val cutoff = nowMillis() - maxAgeMillis
        val expired = active.filterTo(mutableSetOf()) { key ->
            val written = stamps[key] ?: return@filterTo false // legacy, unknown age
            written < cutoff
        }
        if (expired.isEmpty()) return 0
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = (prefs[KEY_ACTIVE] ?: emptySet()).minus(expired)
            prefs[KEY_STAMPS] = (prefs[KEY_STAMPS] ?: emptySet())
                .filterNot { entryKey(it) in expired }.toSet()
        }
        return expired.size
    }

    /** Clears every active mark for the source (used on full teardown). */
    suspend fun clearSource(source: String) {
        val prefix = "$source|"
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = (prefs[KEY_ACTIVE] ?: emptySet())
                .filterNot { it.startsWith(prefix) }
                .toSet()
            prefs[KEY_STAMPS] = (prefs[KEY_STAMPS] ?: emptySet())
                .filterNot { it.startsWith(prefix) }
                .toSet()
        }
    }

    private fun keyOf(source: String, monitorKey: String) = "$source|$monitorKey"

    private fun stampEntry(key: String) = "$key$STAMP_SEPARATOR${nowMillis()}"

    private fun entryKey(entry: String) =
        entry.substringBefore(STAMP_SEPARATOR, missingDelimiterValue = entry)

    private fun entryMillis(entry: String): Long? =
        entry.substringAfter(STAMP_SEPARATOR, missingDelimiterValue = "").toLongOrNull()

    private companion object {
        val KEY_ACTIVE = stringSetPreferencesKey("active_triggers")
        val KEY_STAMPS = stringSetPreferencesKey("active_triggers_stamps")
        // Control char — can never appear in monitor keys (ids, addresses, types).
        const val STAMP_SEPARATOR = "\u0001"
        // 7 days: long enough for any legitimate in-flight trigger, short
        // enough that a crash leftover can never fire an exit a week later.
        const val DEFAULT_MAX_ACTIVE_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
