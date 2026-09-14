package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.AutomationDependencyValidator
import com.nexaflow.domain.workflow.WorkflowValidationIssue
import com.nexaflow.domain.workflow.WorkflowValidator
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Portable JSON backup file produced by [BackupManager.export].
 * [version] allows future format migrations.
 */
@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val automations: List<Automation>,
    /** Derived review index only; regenerated from actions at every boundary. */
    val pluginDependencies: List<PluginDependency> = emptyList()
)

sealed interface ImportResult {
    /** Imported definitions never become active until the user reviews them. */
    data class Success(val count: Int, val disabledCount: Int) : ImportResult
    data class InvalidWorkflow(val automationId: String, val issues: List<WorkflowValidationIssue>) : ImportResult
    data object InvalidFile : ImportResult
}

/** Preflight result for a single-task (.nexaflow) import — see [BackupManager.preflightSingle]. */
sealed interface SingleTaskPreflight {
    data class Ready(val automation: Automation) : SingleTaskPreflight
    data class InvalidWorkflow(val automationId: String, val issues: List<WorkflowValidationIssue>) : SingleTaskPreflight

    /** A valid backup container, but not a single-task file (0 or 2+ automations). */
    data object NotSingle : SingleTaskPreflight
    data object InvalidFile : SingleTaskPreflight
}

/** Import result for a single-task (.nexaflow) import — see [BackupManager.importSingle]. */
sealed interface SingleTaskImportResult {
    /** Carries the saved automation with its final (possibly re-keyed) local id. */
    data class Success(val automation: Automation) : SingleTaskImportResult
    data class InvalidWorkflow(val automationId: String, val issues: List<WorkflowValidationIssue>) : SingleTaskImportResult

    /** A valid backup container, but not a single-task file (0 or 2+ automations). */
    data object NotSingle : SingleTaskImportResult
    data object InvalidFile : SingleTaskImportResult
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
            automations = automations,
            pluginDependencies = PluginDependencyScanner.scan(automations)
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
        //
        // A backup is also a template-sharing format. Never let an imported ID
        // silently replace a local automation: Room uses REPLACE for ordinary
        // user edits, so collisions must be made unique at this boundary.
        // Re-key only colliding imported rules and rewrite their internal
        // maintenance dependencies in the same pass, preserving the imported
        // workflow graph while leaving the local graph untouched.
        val existingIds = automationRepository.getAutomations().first().map { it.id }.toSet()
        val importedIdMap = backup.automations.associate { automation ->
            automation.id to if (automation.id in existingIds) UUID.randomUUID().toString() else automation.id
        }
        val importedAutomations = backup.automations.map { automation ->
            val remappedDependencies = automation.maintenanceProfile
                ?.dependencyAutomationIds
                ?.map { dependencyId -> importedIdMap[dependencyId] ?: dependencyId }
            automation.copy(
                id = importedIdMap.getValue(automation.id),
                enabled = false,
                maintenanceProfile = automation.maintenanceProfile?.copy(
                    dependencyAutomationIds = remappedDependencies.orEmpty()
                )
            )
        }
        val disabledCount = backup.automations.count { it.enabled }
        for (automation in importedAutomations) {
            automationRepository.saveAutomation(automation)
        }
        return ImportResult.Success(importedAutomations.size, disabledCount)
    }

    /**
     * Single-task sharing format (.nexaflow files): the proven, validated
     * full-backup container with exactly one automation. Reusing it means
     * imports ride the same version gate, structural preflight, workflow
     * validation, ID-collision re-keying and review-before-enable policy as
     * full backups — no second, weaker parsing path exists.
     */
    fun exportSingle(automation: Automation): String {
        val single = BackupFile(
            version = BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = listOf(automation),
            pluginDependencies = PluginDependencyScanner.scan(listOf(automation))
        )
        return json.encodeToString(single)
    }

    /**
     * Single-task import preflight: a thin wrapper over [preflight] that also
     * rejects multi-automation files so a full backup shared into the
     * single-task importer cannot quietly import "just the first task".
     * Returns the same shapes as [preflight] (Ready carries the reviewed,
     * dependency-rebuilt backup) plus [SingleTaskPreflight.NotSingle].
     */
    fun preflightSingle(jsonText: String): SingleTaskPreflight =
        when (val preflight = preflight(jsonText)) {
            is BackupPreflight.Ready ->
                if (preflight.backup.automations.size == 1) {
                    SingleTaskPreflight.Ready(preflight.backup.automations.first())
                } else {
                    SingleTaskPreflight.NotSingle
                }
            is BackupPreflight.InvalidWorkflow ->
                SingleTaskPreflight.InvalidWorkflow(preflight.automationId, preflight.issues)
            BackupPreflight.InvalidFile -> SingleTaskPreflight.InvalidFile
        }

    /**
     * Single-task import: parses through [preflightSingle], then applies the
     * exact [import] persistence policy for one automation (unique-ID
     * re-keying on collision, review-before-enable). Returns the saved
     * automation (with its final local id) on success so the UI can offer
     * "open in builder" directly.
     */
    suspend fun importSingle(jsonText: String): SingleTaskImportResult {
        val automation = when (val preflight = preflightSingle(jsonText)) {
            is SingleTaskPreflight.Ready -> preflight.automation
            is SingleTaskPreflight.InvalidWorkflow ->
                return SingleTaskImportResult.InvalidWorkflow(preflight.automationId, preflight.issues)
            SingleTaskPreflight.NotSingle -> return SingleTaskImportResult.NotSingle
            SingleTaskPreflight.InvalidFile -> return SingleTaskImportResult.InvalidFile
        }
        val existingIds = automationRepository.getAutomations().first().map { it.id }.toSet()
        // Both branches persist AND return the disabled copy: the result must
        // describe exactly what was stored, never the pre-review payload.
        val saved = if (automation.id in existingIds) {
            val rekeyed = automation.copy(id = UUID.randomUUID().toString(), enabled = false)
            automationRepository.saveAutomation(rekeyed)
            rekeyed
        } else {
            val disabled = automation.copy(enabled = false)
            automationRepository.saveAutomation(disabled)
            disabled
        }
        return SingleTaskImportResult.Success(saved)
    }

    fun preflight(jsonText: String): BackupPreflight {
        val backup = try {
            json.decodeFromString<BackupFile>(jsonText)
        } catch (_: Exception) {
            return BackupPreflight.InvalidFile
        }
        // A duplicate ID makes the later ID-remapping map ambiguous. More
        // importantly, Room's normal save path replaces equal IDs, so accepting
        // a hand-edited or corrupt backup could silently discard one automation.
        // Reject the whole file before any persistence takes place.
        val hasDuplicateAutomationIds = backup.automations
            .map { it.id }
            .toSet()
            .size != backup.automations.size
        if (
            backup.version !in 1..BACKUP_VERSION ||
            backup.automations.any { !it.isWellFormed() } ||
            hasDuplicateAutomationIds
        ) {
            return BackupPreflight.InvalidFile
        }
        val dependencyValidation = AutomationDependencyValidator.validate(backup.automations)
        backup.automations.forEach { automation ->
            val issues = WorkflowValidator.validate(automation).issues +
                dependencyValidation.issuesFor(automation.id)
            if (issues.isNotEmpty()) return BackupPreflight.InvalidWorkflow(automation.id, issues)
        }
        // Incoming dependency metadata is descriptive and may be stale or
        // tampered with. Rebuild it from the typed workflow actions before UI
        // review or saving, and never treat it as an authority for execution.
        return BackupPreflight.Ready(
            backup.copy(pluginDependencies = PluginDependencyScanner.scan(backup.automations))
        )
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
