package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup/restore hardening gate: import must reject malformed or foreign JSON
 * wholesale (never half-importing entries that would crash the engine), while
 * accepting well-formed exports.
 */
class BackupManagerTest {

    private val repository = FakeAutomationRepository()
    private val manager = BackupManager(repository)

    private fun validAutomation(id: String = "a1") = Automation(
        id = id,
        name = "Morning Mode",
        description = "",
        icon = "sunny",
        iconColor = 0xFFFFFFFF,
        backgroundColor = 0xFFE8A33D,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00"))),
        actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60"))),
        createdAt = 0,
        updatedAt = 0
    )

    private fun backupJson(vararg automations: Automation, version: Int = BackupManager.BACKUP_VERSION): String =
        manager.toJson(
            BackupFile(
                version = version,
                exportedAt = 123L,
                automations = automations.toList()
            )
        )

    @Test
    fun `well formed export imports successfully`() = runBlocking {
        val result = manager.import(backupJson(validAutomation()))
        assertEquals(ImportResult.Success(1), result)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun `multiple automations import all`() = runBlocking {
        val result = manager.import(backupJson(validAutomation("a"), validAutomation("b")))
        assertEquals(ImportResult.Success(2), result)
        assertEquals(2, repository.saved.size)
    }

    @Test
    fun `future version is rejected`() = runBlocking {
        val result = manager.import(backupJson(validAutomation(), version = BackupManager.BACKUP_VERSION + 1))
        assertEquals(ImportResult.InvalidFile, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `zero version is rejected`() = runBlocking {
        val result = manager.import(backupJson(validAutomation(), version = 0))
        assertEquals(ImportResult.InvalidFile, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `negative version is rejected`() = runBlocking {
        val result = manager.import(backupJson(validAutomation(), version = -1))
        assertEquals(ImportResult.InvalidFile, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `missing automations list is rejected`() = runBlocking {
        val result = manager.import("""{"version":1,"exportedAt":1}""")
        assertEquals(ImportResult.InvalidFile, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `empty automations list imports zero`() = runBlocking {
        val result = manager.import(backupJson())
        assertEquals(ImportResult.Success(0), result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `not json is rejected`() = runBlocking {
        assertEquals(ImportResult.InvalidFile, manager.import("this is not json"))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `null trigger type is rejected`() = runBlocking {
        // Simulates a hand-edited file with an unknown/absent trigger type:
        // Gson leaves the enum null, which must not reach the repository.
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[{"type":null,"config":{"time":"08:00"}}],"actions":[],
               "exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `unknown trigger enum name is rejected`() = runBlocking {
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[{"type":"NOT_A_TRIGGER","config":{}}],"actions":[],
               "exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `null action type is rejected`() = runBlocking {
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[],"actions":[{"type":null,"config":{}}],
               "exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `null trigger config is rejected`() = runBlocking {
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[{"type":"TIME","config":null}],"actions":[],
               "exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `blank automation id is rejected`() = runBlocking {
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"  ","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[],"actions":[],"exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `null element inside automations list is rejected`() = runBlocking {
        // Gson parses `[null, {...}]` into a runtime-null element; import must
        // reject rather than crash on the null entry.
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              null,
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[],"actions":[],"exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `null element inside trigger list is rejected`() = runBlocking {
        val json = """
            {"version":1,"exportedAt":1,"automations":[
              {"id":"a1","name":"x","description":"","icon":"sunny","iconColor":4294967295,
               "backgroundColor":4292330301,"category":"general","priority":1,"enabled":true,
               "triggers":[null],"actions":[],"exitActions":[],"createdAt":0,"updatedAt":0}
            ]}
        """.trimIndent()
        assertEquals(ImportResult.InvalidFile, manager.import(json))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `valid export round-trips through toJson and import`() = runBlocking {
        val exported = manager.export()
        assertEquals(ImportResult.Success(0), manager.import(manager.toJson(exported)))
        assertTrue(repository.saved.isEmpty())
    }

    private class FakeAutomationRepository : AutomationRepository {
        val saved = mutableListOf<Automation>()

        override fun getAutomations(): Flow<List<Automation>> = flowOf(saved.toList())

        override suspend fun getAutomationById(id: String): Automation? =
            saved.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) {
            saved.removeAll { it.id == automation.id }
            saved.add(automation)
        }

        override suspend fun deleteAutomation(automation: Automation) {
            saved.removeAll { it.id == automation.id }
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            val index = saved.indexOfFirst { it.id == id }
            if (index >= 0) saved[index] = saved[index].copy(enabled = enabled)
        }
    }
}
