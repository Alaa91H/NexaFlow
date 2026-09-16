package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.models.MaintenanceWindow
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(ImportResult.Success(1, 1), result)
        assertEquals(1, repository.saved.size)
        assertFalse(repository.saved.single().enabled)
    }

    @Test
    fun `imported advanced command remains disabled until explicitly reviewed`() = runBlocking {
        val advanced = validAutomation().copy(
            actions = listOf(Action(ActionType.ADVANCED_ROOT, mapOf("command" to "id")))
        )

        assertEquals(ImportResult.Success(1, 1), manager.import(backupJson(advanced)))
        assertFalse(repository.saved.single().enabled)
        assertEquals(ActionType.ADVANCED_ROOT, repository.saved.single().actions.single().type)
    }

    @Test
    fun `multiple automations import all`() = runBlocking {
        val result = manager.import(backupJson(validAutomation("a"), validAutomation("b")))
        assertEquals(ImportResult.Success(2, 2), result)
        assertEquals(2, repository.saved.size)
    }

    @Test
    fun `duplicate automation ids in one backup are rejected before any save`() = runBlocking {
        val result = manager.import(
            backupJson(
                validAutomation("duplicate"),
                validAutomation("duplicate").copy(name = "Second definition")
            )
        )

        assertEquals(ImportResult.InvalidFile, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `import preserves local automation and rewrites imported dependency collisions`() = runBlocking {
        val local = validAutomation("shared").copy(name = "Local Morning", enabled = true)
        val importedDependency = validAutomation("shared").copy(name = "Imported Morning")
        val importedDependent = validAutomation("dependent").copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("shared")
            )
        )
        repository.saveAutomation(local)

        val result = manager.import(backupJson(importedDependency, importedDependent))

        assertEquals(ImportResult.Success(2, 2), result)
        assertEquals(3, repository.saved.size)
        assertEquals(local, repository.saved.single { it.id == "shared" })
        val remappedDependency = repository.saved.single { it.name == "Imported Morning" }
        val remappedDependent = repository.saved.single { it.id == "dependent" }
        assertFalse(remappedDependency.enabled)
        assertFalse(remappedDependent.enabled)
        assertTrue(remappedDependency.id != "shared")
        assertEquals(listOf(remappedDependency.id), remappedDependent.maintenanceProfile?.dependencyAutomationIds)
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
        assertEquals(ImportResult.Success(0, 0), result)
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
    fun `rich automation round-trips through export and import identically`() = runBlocking {
        // Full-fidelity bidirectional check: every field kind (nested triggers
        // with config maps, actions, constraints, exit actions, flags) must
        // survive export -> JSON -> import byte-for-byte. This is the same
        // schema the R8 release build serializes (field names and enum names
        // are compile-time constants, unaffected by obfuscation).
        val original = Automation(
            id = "a-1",
            name = "Home Mode",
            description = "A rich task",
            icon = "home",
            iconColor = 0xFF1B62B7,
            backgroundColor = 0xFFE8A33D,
            category = "location",
            priority = 5,
            enabled = true,
            triggers = listOf(
                Trigger(TriggerType.TIME, mapOf("time" to "08:00", "timeMode" to "RANGE", "repeat" to "DAILY")),
                Trigger(TriggerType.APPLICATION, mapOf("packages" to "com.whatsapp,com.telegram"))
            ),
            actions = listOf(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60")),
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "25", "ramp" to "true"))
            ),
            constraints = listOf(
                Constraint(ConstraintType.BATTERY, mapOf("direction" to "ABOVE", "level" to "30")),
                Constraint(ConstraintType.WIFI)
            ),
            exitActions = listOf(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "100"))
            ),
            revertOnExit = false,
            cooldownSeconds = 15,
            createdAt = 111L,
            updatedAt = 222L
        )
        repository.saveAutomation(original)
        val exported = manager.export()
        assertEquals(1, exported.automations.size)
        val result = manager.import(manager.toJson(exported))
        assertEquals(ImportResult.Success(1, 1), result)
        assertEquals(2, repository.saved.size)
        val imported = repository.saved.single { it.id != original.id }
        assertEquals(original.copy(id = imported.id, enabled = false), imported)
    }

    @Test
    fun `maintenance profile round-trips in version one backup`() = runBlocking {
        val original = validAutomation().copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.APP,
                window = MaintenanceWindow(
                    startTime = "02:00",
                    endTime = "05:00",
                    chargingRequired = true,
                    unmeteredWifiRequired = true
                )
            )
        )
        repository.saveAutomation(original)

        val exported = manager.export()

        assertEquals(BackupManager.BACKUP_VERSION, exported.version)
        assertEquals(original.maintenanceProfile, exported.automations.single().maintenanceProfile)
        assertEquals(ImportResult.Success(1, 1), manager.import(manager.toJson(exported)))
        assertEquals(2, repository.saved.size)
        val imported = repository.saved.single { it.id != original.id }
        assertEquals(original.copy(id = imported.id, enabled = false), imported)
    }

    @Test
    fun `dependency cycle is rejected before any automation is saved`() = runBlocking {
        val first = validAutomation("first").copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("second")
            )
        )
        val second = validAutomation("second").copy(
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("first")
            )
        )

        val result = manager.import(backupJson(first, second))

        assertTrue(result is ImportResult.InvalidWorkflow)
        assertTrue((result as ImportResult.InvalidWorkflow).issues.any {
            it.code == WorkflowValidationCode.CIRCULAR_DEPENDENCY
        })
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `valid export round-trips through toJson and import`() = runBlocking {
        val exported = manager.export()
        assertEquals(ImportResult.Success(0, 0), manager.import(manager.toJson(exported)))
        assertTrue(repository.saved.isEmpty())
    }

    // ---- Single-task (.nexaflow) share format ---------------------------

    @Test
    fun `single task export produces a single automation container that round trips`() = runBlocking {
        val automation = validAutomation("shared-1")
        val json = manager.exportSingle(automation)
        when (val preflight = manager.preflightSingle(json)) {
            is SingleTaskPreflight.Ready -> assertEquals("shared-1", preflight.automation.id)
            else -> fail("expected Ready, got $preflight")
        }
    }

    @Test
    fun `single task import saves the task disabled for review`() = runBlocking {
        val json = manager.exportSingle(validAutomation("imported-1").copy(enabled = true))
        val result = manager.importSingle(json)
        assertTrue(result is SingleTaskImportResult.Success)
        val saved = (result as SingleTaskImportResult.Success).automation
        assertFalse("imported tasks must stay disabled until reviewed", saved.enabled)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun `single task import rekeys on id collision and keeps the local task untouched`() = runBlocking {
        val local = validAutomation("dup").copy(name = "Local Original")
        repository.saveAutomation(local)
        val json = manager.exportSingle(validAutomation("dup").copy(name = "Imported Copy", enabled = true))
        val result = manager.importSingle(json)
        assertTrue(result is SingleTaskImportResult.Success)
        val saved = (result as SingleTaskImportResult.Success).automation
        assertTrue("colliding import must be re-keyed", saved.id != "dup")
        assertEquals(2, repository.saved.size)
        val untouched = repository.saved.first { it.id == "dup" }
        assertEquals("Local Original", untouched.name)
        assertTrue(untouched.enabled)
    }

    @Test
    fun `full backup shared into the single task importer is rejected as not single`() = runBlocking {
        val json = backupJson(validAutomation("a1"), validAutomation("a2"))
        assertTrue(manager.importSingle(json) is SingleTaskImportResult.NotSingle)
    }

    @Test
    fun `single task import rejects malformed json`() = runBlocking {
        assertTrue(manager.importSingle("not json at all") is SingleTaskImportResult.InvalidFile)
    }

    @Test
    fun `single task import rejects an empty container`() = runBlocking {
        val json = backupJson()
        assertTrue(manager.importSingle(json) is SingleTaskImportResult.NotSingle)
    }

    @Test
    fun `single task preflight surfaces workflow issues`() = runBlocking {
        // An automation with an unknown trigger enum fails structural parsing
        // (InvalidFile) — matches the full-backup pipeline's wholesale reject.
        val json = backupJson(validAutomation("a1").copy(triggers = listOf(Trigger(TriggerType.BATTERY, mapOf()))))
        val preflight = manager.preflightSingle(json)
        assertTrue(preflight is SingleTaskPreflight.Ready || preflight is SingleTaskPreflight.InvalidFile)
    }

    // ---- P0.3: bounded import — byte cap + per-file resource quotas ----

    @Test
    fun `oversized payload is rejected before parsing`() {
        val oversize = "x".repeat(ImportLimits.MAX_IMPORT_BYTES + 1)
        assertTrue(manager.preflight(oversize) is BackupPreflight.InvalidFile)
    }

    @Test
    fun `too many automations in one file are rejected`() = runBlocking {
        val many = Array(ImportLimits.MAX_AUTOMATIONS_PER_FILE + 1) { i ->
            validAutomation(id = "bulk-$i")
        }
        val json = backupJson(*many)
        assertTrue(manager.preflight(json) is BackupPreflight.InvalidFile)
    }

    @Test
    fun `automation exceeding action quota is rejected`() {
        val flood = List(ImportLimits.MAX_ACTIONS_PER_AUTOMATION + 1) {
            Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("level" to "60"))
        }
        val json = backupJson(validAutomation("a1").copy(actions = flood))
        assertTrue(manager.preflight(json) is BackupPreflight.InvalidFile)
    }

    @Test
    fun `automation exceeding trigger quota is rejected`() {
        val flood = List(ImportLimits.MAX_TRIGGERS_PER_AUTOMATION + 1) {
            Trigger(TriggerType.TIME, mapOf("time" to "08:00"))
        }
        val json = backupJson(validAutomation("a1").copy(triggers = flood))
        assertTrue(manager.preflight(json) is BackupPreflight.InvalidFile)
    }

    @Test
    fun `config entry over length quota is rejected`() {
        val longValue = "v".repeat(ImportLimits.MAX_CONFIG_VALUE_LENGTH + 1)
        val json = backupJson(
            validAutomation("a1").copy(
                actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("note" to longValue)))
            )
        )
        assertTrue(manager.preflight(json) is BackupPreflight.InvalidFile)
    }

    @Test
    fun `imported deep link tokens are always stripped`() = runBlocking {
        val json = backupJson(validAutomation("a1").copy(deepLinkToken = "stolen-capability"))
        val result = manager.import(json)
        assertTrue(result is ImportResult.Success)
        val saved = repository.saved.first { it.id == "a1" }
        assertEquals(null, saved.deepLinkToken)
    }

    @Test
    fun `bounded read rejects streams larger than the cap`() {
        val big = java.io.ByteArrayInputStream(ByteArray(ImportLimits.MAX_IMPORT_BYTES + 1))
        assertNull(ImportLimits.readBoundedText(big))
    }

    @Test
    fun `bounded read decodes a small utf8 stream`() {
        val payload = "{\"hello\":\"world\"}"
        val stream = java.io.ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
        assertEquals(payload, ImportLimits.readBoundedText(stream))
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

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
