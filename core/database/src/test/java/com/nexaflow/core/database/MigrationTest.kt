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
    fun migrate1To5_preservesUserAutomations() {
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

        val migrated = helper.runMigrationsAndValidate(5, Migrations.ALL)
        migrated.prepare(
            "SELECT name, exitActionsJson, revertOnExit FROM `automations` WHERE id = 'a1'"
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Morning Mode", stmt.getText(0))
            // New columns must get their defaults so existing rows stay valid.
            assertEquals("[]", stmt.getText(1))
            assertEquals(0, stmt.getLong(2))
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

        val migrated = helper.runMigrationsAndValidate(5, listOf(Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5))
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

        val migrated = helper.runMigrationsAndValidate(5, listOf(Migrations.MIGRATION_4_5))
        migrated.prepare("SELECT exitActionsJson, revertOnExit FROM `automations` WHERE id = 'a1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("[]", stmt.getText(0))
            assertEquals(0, stmt.getLong(1))
        }
        migrated.close()
    }

    @Test
    fun migrate1To5_keepsExecutionHistoryEmptyButValid() {
        // A v1 database has no execution_history; after the full chain the
        // table must exist and accept rows (schema validated by Room).
        helper.createDatabase(1).close()
        val migrated = helper.runMigrationsAndValidate(5, Migrations.ALL)
        migrated.execSQL(
            "INSERT INTO `execution_history` " +
                "(`id`, `automationId`, `automationName`, `success`, `message`, `executedAt`) " +
                "VALUES ('e1', 'a1', 'Morning', 1, 'ok', 1700000000000)"
        )
        migrated.prepare("SELECT automationName FROM `execution_history` WHERE id = 'e1'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Morning", stmt.getText(0))
        }
        migrated.close()
    }
}
