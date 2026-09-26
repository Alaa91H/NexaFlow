package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.smsDeliveryDataStore by preferencesDataStore(
    name = "nexaflow_sms_delivery",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SmsDeliveryStore(
    private val context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private val dataStore = context.smsDeliveryDataStore

    suspend fun claim(
        automationId: String,
        fingerprint: String,
        cooldownMillis: Long,
        occurredAt: Long = nowMillis()
    ): Boolean {
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(fingerprint.matches(FINGERPRINT_REGEX)) { "fingerprint must be a SHA-256 hex digest" }

        var accepted = false
        dataStore.edit { prefs ->
            val lastAccepted = decodeLastAccepted(prefs[KEY_LAST_ACCEPTED].orEmpty())
            val recent = decodeRecent(prefs[KEY_RECENT].orEmpty())
                .filter { occurredAt - it.occurredAt <= DEDUPE_RETENTION_MS }
                .toMutableList()

            val last = lastAccepted[automationId]
            val cooldownBlocked = last != null && occurredAt - last <= cooldownMillis.coerceAtLeast(0L)
            val duplicate = recent.any {
                it.automationId == automationId && it.fingerprint == fingerprint
            }

            if (!cooldownBlocked && !duplicate) {
                lastAccepted[automationId] = occurredAt
                recent += RecentClaim(automationId, fingerprint, occurredAt)
                accepted = true
            }

            val boundedRecent = recent
                .sortedByDescending { it.occurredAt }
                .take(MAX_RECENT_CLAIMS)

            val knownAutomationIds = boundedRecent.mapTo(mutableSetOf()) { it.automationId }
            val boundedLast = lastAccepted.entries
                .sortedByDescending { it.value }
                .filter { (id, timestamp) ->
                    id in knownAutomationIds || occurredAt - timestamp <= LAST_ACCEPTED_RETENTION_MS
                }
                .take(MAX_LAST_ACCEPTED)
                .associate { it.toPair() }

            prefs[KEY_RECENT] = boundedRecent.mapTo(linkedSetOf(), ::encodeRecent)
            prefs[KEY_LAST_ACCEPTED] = boundedLast.entries.mapTo(linkedSetOf()) {
                encodeLastAccepted(it.key, it.value)
            }
        }
        return accepted
    }

    suspend fun clearForAutomation(automationId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_RECENT] = prefs[KEY_RECENT].orEmpty()
                .filterNot { decodeRecentEntry(it)?.automationId == automationId }
                .toSet()
            prefs[KEY_LAST_ACCEPTED] = prefs[KEY_LAST_ACCEPTED].orEmpty()
                .filterNot { decodeLastAcceptedEntry(it)?.first == automationId }
                .toSet()
        }
    }

    internal suspend fun recentClaimsForTest(): Int =
        dataStore.data.first()[KEY_RECENT].orEmpty().size

    private fun decodeLastAccepted(entries: Set<String>): LinkedHashMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        entries.forEach { entry ->
            decodeLastAcceptedEntry(entry)?.let { (id, timestamp) ->
                val previous = result[id]
                if (previous == null || timestamp > previous) result[id] = timestamp
            }
        }
        return result
    }

    private fun decodeRecent(entries: Set<String>): List<RecentClaim> =
        entries.mapNotNull(::decodeRecentEntry)

    private fun encodeLastAccepted(automationId: String, timestamp: Long): String =
        "$automationId$SEP$timestamp"

    private fun decodeLastAcceptedEntry(entry: String): Pair<String, Long>? {
        val parts = entry.split(SEP, limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        return parts[0] to timestamp
    }

    private fun encodeRecent(claim: RecentClaim): String =
        "${claim.automationId}$SEP${claim.fingerprint}$SEP${claim.occurredAt}"

    private fun decodeRecentEntry(entry: String): RecentClaim? {
        val parts = entry.split(SEP, limit = 3)
        if (parts.size != 3 || parts[0].isBlank()) return null
        val fingerprint = parts[1]
        if (!fingerprint.matches(FINGERPRINT_REGEX)) return null
        val timestamp = parts[2].toLongOrNull() ?: return null
        return RecentClaim(parts[0], fingerprint, timestamp)
    }

    private data class RecentClaim(
        val automationId: String,
        val fingerprint: String,
        val occurredAt: Long
    )

    private companion object {
        val KEY_RECENT = stringSetPreferencesKey("recent_sms_claims")
        val KEY_LAST_ACCEPTED = stringSetPreferencesKey("last_sms_accept")
        val FINGERPRINT_REGEX = Regex("^[0-9a-f]{64}$")
        const val SEP = "\u0001"
        const val MAX_RECENT_CLAIMS = 256
        const val MAX_LAST_ACCEPTED = 128
        const val DEDUPE_RETENTION_MS = 24L * 60 * 60 * 1000
        const val LAST_ACCEPTED_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
