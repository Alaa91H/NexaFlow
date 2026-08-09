package com.nexaflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room migration tests for the full 1→5 chain plus the critical individual
 * steps (3→4 drops conditionsJson + profiles, 4→5 adds exit-behavior columns).
 *
 * These run on the JVM via Robolectric, validating every migration against the
 * exported schema JSON files in `core/database/schemas`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val dbFile = File(System.getProperty("java.io.tmpdir"), "nexaflow-migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Before
    fun setUp() {
        dbFile.delete()
    }

    @Test
    fun migrate1To8_preservesUserAutomations() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `conditionsJson`, `actionsJson`, `createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Morning Mode', 'Start the day', 'sunny', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', '[]', 1700000000000, 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(8, Migrations.ALL)
        migrated.prepare(
            "SELECT name, exitActionsJson, revertOnExit, cooldownSeconds FROM `automations` WHERE id = 'a1'"
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Morning Mode", stmt.getText(0))
            // New columns must get their defaults so existing rows stay valid.
            assertEquals("[]", stmt.getText(1))
            assertEquals(0, stmt.getLong(2))
            assertEquals(10L, stmt.getLong(3))
        }
        migrated.close()
    }

    @Test
    fun migrate7To8_addsCooldownColumnWithDefault() {
        helper.createDatabase(7).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `exitActionsJson`, `revertOnExit`, " +
                    "`createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Gym', 'Workout time', 'bolt', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', '[]', 0, 1700000000000, 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(8, listOf(Migrations.MIGRATION_7_8))
        migrated.prepare("SELECT name, cooldownSeconds FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Gym", stmt.getText(0))
            assertEquals(10L, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4_dropsConditionsJsonAndProfiles() {
        helper.createDatabase(3).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `conditionsJson`, `actionsJson`, `createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Evening', 'Wind down', 'dark', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', '[]', 1700000000000, 1700000000000)"
            )
            execSQL(
                "INSERT INTO `profiles` " +
                    "(`id`, `name`, `description`, `icon`, `color`, `active`, `automationIdsJson`, `createdAt`) " +
                    "VALUES ('p1', 'Work', 'desc', 'bolt', 4280566455, 1, '[]', 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            8,
            listOf(
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7,
                Migrations.MIGRATION_7_8
            )
        )
        // The obsolete conditionsJson column must be gone.
        val columnNames = buildSet {
            migrated.prepare("PRAGMA table_info(`automations`)").use { stmt ->
                while (stmt.step()) add(stmt.getText(1))
            }
        }
        assertFalse("conditionsJson must be dropped", "conditionsJson" in columnNames)
        // The profiles table must be dropped entirely.
        migrated.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='profiles'").use { stmt ->
            assertFalse("profiles table must be dropped", stmt.step())
        }
        // And the automation row survived with exit defaults.
        migrated.prepare("SELECT name, revertOnExit FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Evening", stmt.getText(0))
            assertEquals(0, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_addsExitColumnsWithDefaults() {
        helper.createDatabase(4).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Gym', 'Workout time', 'bolt', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', 1700000000000, 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            8,
            listOf(
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7,
                Migrations.MIGRATION_7_8
            )
        )
        migrated.prepare("SELECT exitActionsJson, revertOnExit FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("[]", stmt.getText(0))
            assertEquals(0, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate5To6_addsChannelColumn() {
        helper.createDatabase(5).apply {
            execSQL(
                "INSERT INTO `execution_history` " +
                    "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`) " +
                    "VALUES ('e1', 'a1', 'Morning', 1, 'ok', 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            8,
            listOf(Migrations.MIGRATION_5_6, Migrations.MIGRATION_6_7, Migrations.MIGRATION_7_8)
        )
        // Pre-v6 rows have no channel (null), and the column accepts new values.
        migrated.prepare("SELECT channel FROM `execution_history` WHERE id = 'e1'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue("channel must be NULL for pre-v6 rows", stmt.isNull(0))
        }
        migrated.execSQL(
            "INSERT INTO `execution_history` " +
                "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`, `channel`) " +
                "VALUES ('e2', 'a2', 'Night', 0, 'nope', 1700000000000, 'SHIZUKU')"
        )
        migrated.prepare("SELECT channel FROM `execution_history` WHERE id = 'e2'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("SHIZUKU", stmt.getText(0))
        }
        migrated.close()
    }

    @Test
    fun migrate6To7_addsResultsJsonColumn() {
        helper.createDatabase(6).apply {
            execSQL(
                "INSERT INTO `execution_history` " +
                    "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`, `channel`) " +
                    "VALUES ('e1', 'a1', 'Morning', 1, 'ok', 1700000000000, 'ROOT')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(8, listOf(Migrations.MIGRATION_6_7, Migrations.MIGRATION_7_8))
        // Pre-v7 rows have no per-action results (null).
        migrated.prepare("SELECT resultsJson FROM `execution_history` WHERE id = 'e1'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue("resultsJson must be NULL for pre-v7 rows", stmt.isNull(0))
        }
        // The column accepts per-action JSON.
        migrated.execSQL(
            "INSERT INTO `execution_history` " +
                "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`, `channel`, `resultsJson`) " +
                "VALUES ('e2', 'a2', 'Night', 1, 'nope', 1700000000000, 'SHIZUKU', " +
                "'[{\"actionType\":\"SYSTEM_BRIGHTNESS\",\"success\":true,\"message\":\"ok\",\"durationMs\":12}]')"
        )
        migrated.prepare("SELECT resultsJson FROM `execution_history` WHERE id = 'e2'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.getText(0).contains("SYSTEM_BRIGHTNESS"))
        }
        migrated.close()
    }

    @Test
    fun migrate8To9_addsGlobalVariablesTable() {
        helper.createDatabase(8).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `exitActionsJson`, `revertOnExit`, " +
                    "`cooldownSeconds`, `createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Gym', 'Workout time', 'bolt', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', '[]', 0, 10, 1700000000000, 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(9, listOf(Migrations.MIGRATION_8_9))
        // The new global_variables table exists and accepts rows.
        migrated.execSQL(
            "INSERT INTO `global_variables` " +
                "(`id`, `name`, `value`, `updatedAt`) " +
                "VALUES ('g1', 'HomeAddress', '123 Main St', 1700000000000)"
        )
        migrated.prepare("SELECT name, value FROM `global_variables` WHERE id = 'g1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("HomeAddress", stmt.getText(0))
            assertEquals("123 Main St", stmt.getText(1))
        }
        // Existing automation rows survive the additive migration untouched.
        migrated.prepare("SELECT name, cooldownSeconds FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Gym", stmt.getText(0))
            assertEquals(10L, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate9To10_addsConstraintsColumnWithDefault() {
        helper.createDatabase(9).apply {
            execSQL(
                "INSERT INTO `automations` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `exitActionsJson`, `revertOnExit`, " +
                    "`cooldownSeconds`, `createdAt`, `updatedAt`) " +
                    "VALUES ('a1', 'Gym', 'Workout time', 'bolt', 4280566455, 4291611852, 'general', " +
                    "1, 1, '[]', '[]', '[]', 0, 10, 1700000000000, 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))
        // Existing rows get the empty-constraints default.
        migrated.prepare("SELECT name, constraintsJson FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Gym", stmt.getText(0))
            assertEquals("[]", stmt.getText(1))
        }
        // The new column accepts constraint JSON.
        migrated.execSQL(
            "UPDATE `automations` SET constraintsJson = " +
                "'[{\"type\":\"WIFI\",\"config\":{}}]' WHERE id = 'a1'"
        )
        migrated.prepare("SELECT constraintsJson FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.getText(0).contains("WIFI"))
        }
        migrated.close()
    }

    @Test
    fun migrate10To11_addsSensitiveColumnWithDefault() {
        helper.createDatabase(10).apply {
            execSQL(
                "INSERT INTO `global_variables` " +
                    "(`id`, `name`, `value`, `updatedAt`) " +
                    "VALUES ('g1', 'HomeAddress', '123 Main St', 1700000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(11, listOf(Migrations.MIGRATION_10_11))
        // Existing variables default to non-sensitive (plaintext stays readable).
        migrated.prepare("SELECT name, value, sensitive FROM `global_variables` WHERE id = 'g1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("HomeAddress", stmt.getText(0))
            assertEquals("123 Main St", stmt.getText(1))
            assertEquals(0, stmt.getLong(2))
        }
        // The new column accepts the sensitive flag.
        migrated.execSQL(
            "INSERT INTO `global_variables` " +
                "(`id`, `name`, `value`, `updatedAt`, `sensitive`) " +
                "VALUES ('g2', 'ApiToken', '*encrypted*', 1700000000000, 1)"
        )
        migrated.prepare("SELECT sensitive FROM `global_variables` WHERE id = 'g2'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1, stmt.getLong(0))
        }
        migrated.close()
    }

    @Test
    fun migrate11To12_addsExecutedAtIndex() {
        helper.createDatabase(11).apply {
            execSQL(
                "INSERT INTO `execution_history` " +
                    "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`, `channel`, `resultsJson`) " +
                    "VALUES ('e1', 'a1', 'Morning', 1, 'ok', 1700000000000, 'ROOT', NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(12, listOf(Migrations.MIGRATION_11_12))
        // The index exists after the migration.
        migrated.prepare(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_execution_history_executedAt'"
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals("index_execution_history_executedAt", stmt.getText(0))
        }
        // Existing rows survive untouched.
        migrated.prepare("SELECT automationName, executedAt FROM `execution_history` WHERE id = 'e1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Morning", stmt.getText(0))
            assertEquals(1700000000000L, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate1To8_keepsExecutionHistoryEmptyButValid() {
        // A v1 database has no execution_history; after the full chain the
        // table must exist and accept rows (schema validated by Room).
        helper.createDatabase(1).close()
        val migrated = helper.runMigrationsAndValidate(8, Migrations.ALL)
        migrated.execSQL(
            "INSERT INTO `execution_history` " +
                "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`, `channel`) " +
                "VALUES ('e1', 'a1', 'Morning', 1, 'ok', 1700000000000, 'ROOT')"
        )
        migrated.prepare("SELECT automationName, channel FROM `execution_history` WHERE id = 'e1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Morning", stmt.getText(0))
            assertEquals("ROOT", stmt.getText(1))
        }
        migrated.close()
    }
}
