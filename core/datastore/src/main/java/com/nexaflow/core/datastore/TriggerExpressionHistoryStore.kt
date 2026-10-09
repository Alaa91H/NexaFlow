package com.nexaflow.core.datastore

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nexaflow.core.security.OccurrenceIdentityHmac
import com.nexaflow.domain.workflow.TriggerExpressionHistoryEvent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.triggerHistoryDataStore by preferencesDataStore(name = "trigger_expression_history_v2")

enum class TriggerHistoryStatus { RECORDED, READ, DUPLICATE, CLOCK_RESET, CORRUPT_RESET, UNAVAILABLE, CAPACITY_REACHED }

data class TriggerHistoryResult(
    val events: List<TriggerExpressionHistoryEvent>,
    val status: TriggerHistoryStatus
)

/** Bounded, atomic store for minimal temporal trigger metadata. */
class TriggerExpressionHistoryStore(
    private val dataStore: DataStore<Preferences>,
    private val identityHmac: OccurrenceIdentityHmac,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val maxSerializedBytes: Int = MAX_SERIALIZED_BYTES
) {
    constructor(
        context: Context,
        identityHmac: OccurrenceIdentityHmac,
        elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
        maxSerializedBytes: Int = MAX_SERIALIZED_BYTES
    ) : this(context.applicationContext.triggerHistoryDataStore, identityHmac, elapsedRealtime, maxSerializedBytes)

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    suspend fun recordAndRead(
        automationId: String,
        workflowRevision: Long,
        eventIndices: List<Int>,
        elapsedMs: Long = elapsedRealtime(),
        sourceId: String,
        stableEventId: String
    ): TriggerHistoryResult {
        if (automationId.isBlank() || workflowRevision <= 0L || elapsedMs < 0L ||
            sourceId.isBlank() || stableEventId.isBlank() || eventIndices.isEmpty() ||
            eventIndices.size > 64 || eventIndices.any { it < 0 } || eventIndices.distinct().size != eventIndices.size) {
            return TriggerHistoryResult(emptyList(), TriggerHistoryStatus.UNAVAILABLE)
        }
        val digest = runCatching { identityHmac.digest(sourceId, stableEventId) }.getOrNull()
            ?.takeIf { it.length == 64 && it.all(Char::isLetterOrDigit) }
            ?: return TriggerHistoryResult(emptyList(), TriggerHistoryStatus.UNAVAILABLE)
        var result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.UNAVAILABLE)
        dataStore.edit { prefs ->
            val encoded = prefs[KEY_HISTORY]
            var envelope = try {
                if (encoded == null) HistoryEnvelope() else decodeBounded(encoded)
            } catch (_: Exception) {
                prefs.remove(KEY_HISTORY)
                result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.CORRUPT_RESET)
                HistoryEnvelope()
            }
            if (envelope.lastElapsedMs > elapsedMs) {
                envelope = HistoryEnvelope()
                result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.CLOCK_RESET)
            }
            var items = envelope.items.filter { elapsedMs - it.elapsedMs <= RETENTION_MS }
                .filterNot { it.automationId == automationId && it.workflowRevision != workflowRevision }
            if (items.none { it.automationId == automationId } && items.map { it.automationId }.distinct().size >= MAX_WORKFLOWS) {
                result = TriggerHistoryResult(items.toDomain(), TriggerHistoryStatus.CAPACITY_REACHED)
                return@edit
            }
            val alreadySeen = items.any { it.automationId == automationId && it.digest == digest }
            var status = result.status.takeIf { it == TriggerHistoryStatus.CLOCK_RESET || it == TriggerHistoryStatus.CORRUPT_RESET }
                ?: if (alreadySeen) TriggerHistoryStatus.DUPLICATE else TriggerHistoryStatus.RECORDED
            if (!alreadySeen) {
                val ordinal = envelope.nextOrdinal + 1L
                envelope = envelope.copy(nextOrdinal = ordinal)
                items = items + eventIndices.map { index ->
                    HistoryItem(automationId, workflowRevision, index, elapsedMs, ordinal, digest)
                }
                if (items.size > MAX_RECORDS) {
                    status = TriggerHistoryStatus.CAPACITY_REACHED
                    items = envelope.items.filter { elapsedMs - it.elapsedMs <= RETENTION_MS }
                }
            }
            val candidate = envelope.copy(lastElapsedMs = elapsedMs, items = items)
            val serialized = runCatching { json.encodeToString(candidate) }.getOrNull()
            if (serialized == null || serialized.toByteArray(Charsets.UTF_8).size > maxSerializedBytes) {
                status = TriggerHistoryStatus.CAPACITY_REACHED
                result = TriggerHistoryResult(items.filter { it.automationId == automationId }.toDomain(), status)
                return@edit
            }
            prefs[KEY_HISTORY] = serialized
            result = TriggerHistoryResult(items.filter { it.automationId == automationId && it.workflowRevision == workflowRevision }
                .toDomain(), status)
        }
        return result
    }

    suspend fun read(automationId: String, workflowRevision: Long, elapsedMs: Long = elapsedRealtime()): TriggerHistoryResult {
        if (automationId.isBlank() || workflowRevision <= 0L || elapsedMs < 0L) {
            return TriggerHistoryResult(emptyList(), TriggerHistoryStatus.UNAVAILABLE)
        }
        var result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.READ)
        dataStore.edit { prefs ->
            val encoded = prefs[KEY_HISTORY] ?: return@edit
            var envelope = try {
                decodeBounded(encoded)
            } catch (_: Exception) {
                prefs.remove(KEY_HISTORY)
                result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.CORRUPT_RESET)
                return@edit
            }
            if (envelope.lastElapsedMs > elapsedMs) {
                prefs.remove(KEY_HISTORY)
                result = TriggerHistoryResult(emptyList(), TriggerHistoryStatus.CLOCK_RESET)
                return@edit
            }
            val items = envelope.items.filter { elapsedMs - it.elapsedMs <= RETENTION_MS }
                .filterNot { it.automationId == automationId && it.workflowRevision != workflowRevision }
            val next = envelope.copy(lastElapsedMs = elapsedMs, items = items)
            val serialized = json.encodeToString(next)
            prefs[KEY_HISTORY] = serialized
            result = TriggerHistoryResult(
                items.filter { it.automationId == automationId && it.workflowRevision == workflowRevision }.toDomain(),
                TriggerHistoryStatus.READ
            )
        }
        return result
    }

    suspend fun clearAutomation(automationId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY] ?: return@edit
            val envelope = runCatching { decodeBounded(current) }.getOrNull()
            if (envelope == null) prefs.remove(KEY_HISTORY)
            else {
                val next = envelope.copy(items = envelope.items.filterNot { it.automationId == automationId })
                if (next.items.isEmpty()) prefs.remove(KEY_HISTORY) else prefs[KEY_HISTORY] = json.encodeToString(next)
            }
        }
    }

    /** Drop affected trigger histories after permission revocation; no permission strings are retained. */
    suspend fun clearSourcePermissionScope(automationId: String, triggerIndices: Set<Int>) {
        if (automationId.isBlank() || triggerIndices.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY] ?: return@edit
            val envelope = runCatching { decodeBounded(current) }.getOrNull()
            if (envelope == null) prefs.remove(KEY_HISTORY)
            else {
                val next = envelope.copy(items = envelope.items.filterNot {
                    it.automationId == automationId && it.triggerIndex in triggerIndices
                })
                if (next.items.isEmpty()) prefs.remove(KEY_HISTORY) else prefs[KEY_HISTORY] = json.encodeToString(next)
            }
        }
    }

    suspend fun clearAll() { dataStore.edit { it.remove(KEY_HISTORY) } }

    internal suspend fun encodedHistoryForTest(): String? = dataStore.data.first()[KEY_HISTORY]

    private fun decodeBounded(encoded: String): HistoryEnvelope {
        require(encoded.toByteArray(Charsets.UTF_8).size <= maxSerializedBytes) { "history exceeds byte limit" }
        val value = json.decodeFromString<HistoryEnvelope>(encoded)
        require(value.items.size <= MAX_RECORDS) { "history exceeds record limit" }
        require(value.nextOrdinal >= 0L && value.lastElapsedMs >= 0L) { "invalid history clock" }
        require(value.items.all {
            it.automationId.isNotBlank() && it.automationId.length <= 128 &&
                it.workflowRevision > 0L && it.triggerIndex in 0..255 &&
                it.elapsedMs >= 0L && it.ordinal in 1L..value.nextOrdinal &&
                it.digest.length == 64 && it.digest.all { char -> char in '0'..'9' || char in 'a'..'f' }
        }) { "invalid history record" }
        return value
    }

    private fun List<HistoryItem>.toDomain(): List<TriggerExpressionHistoryEvent> =
        map { TriggerExpressionHistoryEvent(it.triggerIndex, it.elapsedMs, it.ordinal) }

    @Serializable private data class HistoryEnvelope(
        val lastElapsedMs: Long = 0L,
        val items: List<HistoryItem> = emptyList(),
        val nextOrdinal: Long = 0L
    )
    @Serializable private data class HistoryItem(
        val automationId: String,
        val workflowRevision: Long,
        val triggerIndex: Int,
        val elapsedMs: Long,
        val ordinal: Long,
        val digest: String
    )

    companion object {
        const val MAX_WORKFLOWS = 128
        const val MAX_RECORDS = 4_096
        const val MAX_SERIALIZED_BYTES = 262_144
        const val RETENTION_MS = 604_800_000L
        private val KEY_HISTORY = stringPreferencesKey("records")
    }
}
