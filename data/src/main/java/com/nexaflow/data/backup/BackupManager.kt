package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable JSON backup file produced by [BackupManager.export].
 * [version] allows future format migrations.
 */
@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val automations: List<Automation>
)

sealed interface ImportResult {
    data class Success(val count: Int) : ImportResult
    data object InvalidFile : ImportResult
}

/**
 * Exports and imports all automations as a single pretty-printed JSON file,
 * so users can back up, restore, or share their automations between devices.
 *
 * Serialization is kotlinx.serialization (compile-time, R8-safe — no reflective
 * keep rules needed), with a strict-but-tolerant decoder: unknown keys from
 * newer versions are ignored, missing fields fall back to model defaults, and
 * any structurally invalid input (bad enums, null list entries, wrong types)
 * fails the whole import instead of half-importing corrupt automations.
 */
class BackupManager(
    private val automationRepository: AutomationRepository
) {

    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun export(): BackupFile {
        val automations = automationRepository.getAutomations().first()
        return BackupFile(
            version = BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = automations
        )
    }

    fun toJson(backup: BackupFile): String = json.encodeToString(backup)

    suspend fun import(jsonText: String): ImportResult {
        val backup = try {
            json.decodeFromString<BackupFile>(jsonText)
        } catch (_: Exception) {
            // Malformed JSON, unknown enum names, wrong types, `[null, …]`
            // entries — all land here and reject the file wholesale.
            return ImportResult.InvalidFile
        }
        // Reject foreign files: a version out of the supported range (negative,
        // zero, or from a newer format we cannot migrate yet).
        if (backup.version !in 1..BACKUP_VERSION) {
            return ImportResult.InvalidFile
        }
        // Semantic validation: kotlinx guarantees types/enums are correct, but
        // blank ids/names would still produce broken tasks.
        if (backup.automations.any { !it.isWellFormed() }) {
            return ImportResult.InvalidFile
        }
        backup.automations.forEach { automationRepository.saveAutomation(it) }
        return ImportResult.Success(backup.automations.size)
    }

    /**
     * Semantic sanity check for one automation parsed from JSON. Types and
     * enum values are already enforced by kotlinx; this only rejects entries
     * with blank identifiers/names that would break routing or the UI.
     */
    private fun Automation.isWellFormed(): Boolean =
        id.isNotBlank() &&
            name.isNotBlank() &&
            triggers.all { it.isWellFormed() } &&
            actions.all { it.isWellFormed() } &&
            exitActions.all { it.isWellFormed() } &&
            constraints.all { it.type.name.isNotBlank() }

    private fun Trigger.isWellFormed(): Boolean = type.name.isNotBlank() && config.keys.all { it.isNotBlank() }

    private fun Action.isWellFormed(): Boolean = type.name.isNotBlank() && config.keys.all { it.isNotBlank() }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
