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

    val ALL = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
