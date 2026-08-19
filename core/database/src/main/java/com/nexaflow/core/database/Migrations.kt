package com.nexaflow.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, data-preserving migrations for every schema version this app has
 * shipped. Replaces the destructive fallback so upgrading never wipes the
 * user's automations and history.
 */
object Migrations {

    /** v1 -> v2: adds the execution history table. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `execution_history` (" +
                    "`id` TEXT NOT NULL, " +
                    "`automationId` TEXT NOT NULL, " +
                    "`automationName` TEXT NOT NULL, " +
                    "`success` INTEGER NOT NULL, " +
                    "`message` TEXT NOT NULL, " +
                    "`executedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
        }
    }

    /** v2 -> v3: adds the (now removed) profiles table. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profiles` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`icon` TEXT NOT NULL, " +
                    "`color` INTEGER NOT NULL, " +
                    "`active` INTEGER NOT NULL, " +
                    "`automationIdsJson` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
        }
    }

    /**
     * v3 -> v4: drops the profiles table and removes the obsolete
     * conditionsJson column. Uses a table-recreation transaction because
     * SQLite only supports ALTER TABLE DROP COLUMN from 3.35+ (Android 12+).
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `automations_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`icon` TEXT NOT NULL, " +
                    "`iconColor` INTEGER NOT NULL, " +
                    "`backgroundColor` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`priority` INTEGER NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, " +
                    "`triggersJson` TEXT NOT NULL, " +
                    "`actionsJson` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT INTO `automations_new` " +
                    "(`id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `createdAt`, `updatedAt`) " +
                    "SELECT `id`, `name`, `description`, `icon`, `iconColor`, `backgroundColor`, `category`, " +
                    "`priority`, `enabled`, `triggersJson`, `actionsJson`, `createdAt`, `updatedAt` FROM `automations`"
            )
            db.execSQL("DROP TABLE IF EXISTS `automations`")
            db.execSQL("ALTER TABLE `automations_new` RENAME TO `automations`")
            db.execSQL("DROP TABLE IF EXISTS `profiles`")
        }
    }

    /** v4 -> v5: adds the exit-behavior columns (defaults keep existing rows valid). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `automations` ADD COLUMN `exitActionsJson` TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE `automations` ADD COLUMN `revertOnExit` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v5 -> v6: adds the execution-channel column to the history table so each
     * run records which provider actually executed it ("ROOT", "SHIZUKU", ...).
     * Nullable — pre-v6 rows simply have no channel.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `execution_history` ADD COLUMN `channel` TEXT")
        }
    }

    /**
     * v6 -> v7: adds the per-action results column (JSON) so the run details
     * timeline can show each action's outcome and duration. Nullable — pre-v7
     * rows have no detailed results.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `execution_history` ADD COLUMN `resultsJson` TEXT")
        }
    }

    /**
     * v7 -> v8: adds the per-task cooldown column (seconds). Default 10 keeps
     * every existing row valid; the per-action end behavior lives inside
     * actionsJson so it needs no new column.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `automations` ADD COLUMN `cooldownSeconds` INTEGER NOT NULL DEFAULT 10")
        }
    }

    /**
     * v8 -> v9: adds the global variables table backing the Tasker-style
     * %variable system. Purely additive — no existing table changes.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `global_variables` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
        }
    }

    /**
     * v9 -> v10: adds the constraints column to automations (JSON-encoded gate
     * checks). Default '[]' keeps every existing row valid — constraints are
     * optional and an empty list means "no gate".
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `automations` ADD COLUMN `constraintsJson` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * v10 -> v11: adds the sensitive flag to global variables so secret
     * values (tokens, passwords) can be encrypted at rest via Keystore.
     * Default 0 keeps every existing variable readable as plaintext.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `global_variables` ADD COLUMN `sensitive` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v11 -> v12: adds an index on execution_history.executedAt. The history
     * list sorts by this column and the retention pruner filters on it, so the
     * index keeps both queries fast as the table approaches the 1,000-record
     * ceiling. Pure additive — no data changes.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_execution_history_executedAt` " +
                    "ON `execution_history` (`executedAt`)"
            )
        }
    }

    /**
     * v12 -> v13: adds a monotonic revision to global variables. Existing
     * values become revision one; typed serialization stays inside the existing
     * value field so this migration never rewrites plaintext or Keystore data.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `global_variables` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * v13 -> v14: adds the optional JSON representation used by typed global
     * values. Existing values remain legacy text (`NULL`) until explicitly
     * rewritten through VariableRepository.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `global_variables` ADD COLUMN `serializedValue` TEXT")
        }
    }

    /**
     * v14 -> v15: persists the workflow schema revision. Existing definitions
     * are valid version one workflows, so this is a metadata-only migration.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `automations` ADD COLUMN `workflowVersion` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val ALL = listOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15
    )
}
