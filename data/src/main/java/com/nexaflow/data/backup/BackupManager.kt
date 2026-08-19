package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import com.nexaflow.domain.workflow.WorkflowValidator
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
    /** Imported definitions never become active until the user reviews them. */
    data class Success(val count: Int, val disabledCount: Int) : ImportResult
    data class InvalidWorkflow(val automationId: String, val issues: List<WorkflowValidationIssue>) : ImportResult
    data object InvalidFile : ImportResult
}

/** Non-mutating import preflight used by UI review before calling [BackupManager.import]. */
sealed interface BackupPreflight {
    data class Ready(val backup: BackupFile) : BackupPreflight
    data class InvalidWorkflow(val automationId: String, val issues: List<WorkflowValidationIssue>) : BackupPreflight
    data object InvalidFile : BackupPreflight
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
        val backup = when (val preflight = preflight(jsonText)) {
            is BackupPreflight.Ready -> preflight.backup
            is BackupPreflight.InvalidWorkflow -> return ImportResult.InvalidWorkflow(preflight.automationId, preflight.issues)
            BackupPreflight.InvalidFile -> return ImportResult.InvalidFile
        }
        // Imported rules are data from outside this installation. Saving them
        // disabled prevents a trigger — especially an advanced Root/Shizuku
        // action — from running before the user has reviewed its capabilities.
        val disabledCount = backup.automations.count { it.enabled }
        backup.automations.forEach { automation ->
            automationRepository.saveAutomation(automation.copy(enabled = false))
        }
        return ImportResult.Success(backup.automations.size, disabledCount)
    }

    fun preflight(jsonText: String): BackupPreflight {
        val backup = try {
            json.decodeFromString<BackupFile>(jsonText)
        } catch (_: Exception) {
            return BackupPreflight.InvalidFile
        }
        if (backup.version !in 1..BACKUP_VERSION || backup.automations.any { !it.isWellFormed() }) {
            return BackupPreflight.InvalidFile
        }
        backup.automations.forEach { automation ->
            val validation = WorkflowValidator.validate(automation)
            if (!validation.isValid) return BackupPreflight.InvalidWorkflow(automation.id, validation.issues)
        }
        return BackupPreflight.Ready(backup)
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
