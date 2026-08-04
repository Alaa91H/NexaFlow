package com.nexaflow.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nexaflow.domain.models.Automation
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
        val automations = backup?.automations
        if (automations == null || backup.version > BACKUP_VERSION) {
            return ImportResult.InvalidFile
        }
        automations.forEach { automationRepository.saveAutomation(it) }
        return ImportResult.Success(automations.size)
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}
