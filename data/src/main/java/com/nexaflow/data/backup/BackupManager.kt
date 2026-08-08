package com.nexaflow.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.first

/**
 * Portable JSON backup file produced by [BackupManager.export].
 * [version] allows future format migrations.
 */
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
 * Import is strict: Gson silently tolerates a lot (missing fields become
 * null, unknown enum names become null), so we validate every entry before
 * touching the repository. A malformed file is rejected wholesale instead of
 * half-importing corrupt automations that would crash the engine later.
 */
class BackupManager(
    private val automationRepository: AutomationRepository
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(): BackupFile {
        val automations = automationRepository.getAutomations().first()
        return BackupFile(
            version = BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = automations
        )
    }

    fun toJson(backup: BackupFile): String = gson.toJson(backup)

    suspend fun import(json: String): ImportResult {
        val backup = try {
            gson.fromJson(json, BackupFile::class.java)
        } catch (_: Exception) {
            return ImportResult.InvalidFile
        }
        // Reject malformed/foreign files before any write happens:
        //  - missing automations list, or
        //  - a version that is out of the supported range (negative, zero, or
        //    from a newer format we cannot migrate yet).
        val automations = backup?.automations
        if (automations == null || backup.version !in 1..BACKUP_VERSION) {
            return ImportResult.InvalidFile
        }
        // Validate the whole set first; only save once everything is sound so
        // a single corrupt entry cannot leave the database half-imported.
        // Null-safe: Gson happily parses `[null, {...}]` into runtime-null
        // elements even though the Kotlin list type is non-null.
        if (automations.any { it?.isWellFormed() != true }) {
            return ImportResult.InvalidFile
        }
        automations.forEach { automationRepository.saveAutomation(it) }
        return ImportResult.Success(automations.size)
    }

    /**
     * Structural sanity check for one automation parsed from JSON. Gson leaves
     * unknown enums and missing maps as null, so we must not let them through.
     * Null-safe inner access also covers `[null]` entries inside the lists.
     */
    private fun Automation.isWellFormed(): Boolean =
        id.isNotBlank() &&
            name.isNotBlank() &&
            triggers != null && triggers.all { it?.isWellFormed() == true } &&
            actions != null && actions.all { it?.isWellFormed() == true } &&
            exitActions != null && exitActions.all { it?.isWellFormed() == true }

    private fun Trigger.isWellFormed(): Boolean = type != null && config != null

    private fun Action.isWellFormed(): Boolean = type != null && config != null

    companion object {
        const val BACKUP_VERSION = 1
    }
}
