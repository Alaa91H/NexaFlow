package com.nexaflow.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    /** Transaction-local snapshot used by atomic dependency revalidation. */
    @Query("SELECT * FROM automations")
    suspend fun getAllAutomationsSnapshot(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    /**
     * Collection inserts are executed by Room in a single suspending database
     * transaction. Bulk import therefore commits every automation or none.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomations(automations: List<AutomationEntity>)

    /** Upsert while advancing the definition revision only for content changes. */
    @Transaction
    suspend fun upsertDefinitionWithRevision(automation: AutomationEntity): Long {
        val current = getAutomationById(automation.id)
        val nextRevision = when {
            current == null -> 1L
            current.sameWorkflowDefinition(automation) -> current.workflowRevision
            else -> current.workflowRevision + 1L
        }
        insertAutomation(automation.copy(workflowRevision = nextRevision))
        return nextRevision
    }

    @Transaction
    suspend fun upsertDefinitionsWithRevision(automations: List<AutomationEntity>) {
        automations.forEach { upsertDefinitionWithRevision(it) }
    }

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    /**
     * Transactional compare-and-set used by API/agent optimistic concurrency.
     * The read and update share one Room transaction, so two writers that both
     * observed the same revision cannot both commit successfully.
     */
    @Transaction
    suspend fun compareAndSetAutomation(
        automation: AutomationEntity,
        expectedRevision: Long
    ): Boolean {
        val current = getAutomationById(automation.id) ?: return false
        if (current.updatedAt != expectedRevision) return false
        val revision = if (current.sameWorkflowDefinition(automation)) {
            current.workflowRevision
        } else {
            current.workflowRevision + 1L
        }
        updateAutomation(automation.copy(workflowRevision = revision))
        return true
    }

    @Delete
    suspend fun deleteAutomation(automation: AutomationEntity)

    /**
     * Deletes only the exact revision observed by an optimistic-concurrency
     * caller. SQLite evaluates the predicate and delete atomically.
     */
    @Query("DELETE FROM automations WHERE id = :id AND updatedAt = :expectedRevision")
    suspend fun deleteAutomationIfRevisionMatches(
        id: String,
        expectedRevision: Long
    ): Int

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun updateAutomationStatus(id: String, enabled: Boolean)
}

private fun AutomationEntity.sameWorkflowDefinition(other: AutomationEntity): Boolean =
    name == other.name && description == other.description && icon == other.icon &&
        iconColor == other.iconColor && backgroundColor == other.backgroundColor &&
        category == other.category && priority == other.priority && showToastOnToggle == other.showToastOnToggle &&
        triggersJson == other.triggersJson && actionsJson == other.actionsJson &&
        constraintsJson == other.constraintsJson && exitActionsJson == other.exitActionsJson &&
        revertOnExit == other.revertOnExit && cooldownSeconds == other.cooldownSeconds &&
        workflowVersion == other.workflowVersion && triggerMatch == other.triggerMatch &&
        maintenanceJson == other.maintenanceJson && deepLinkToken == other.deepLinkToken &&
        triggerExpressionJson == other.triggerExpressionJson &&
        canonicalDefinition(canonicalWorkflowJson) == canonicalDefinition(other.canonicalWorkflowJson)

private fun canonicalDefinition(value: String?): String? = value?.replace(
    Regex("\\\"workflowRevision\\\":\\d+"),
    "\"workflowRevision\":0"
)
