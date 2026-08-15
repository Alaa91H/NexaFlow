package com.nexaflow.core.database

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room open-helper factory that installs a SQLite corruption handler.
 *
 * When SQLite reports corruption, the corrupt database (and its WAL/SHM
 * sidecar files) is copied verbatim into a private `corrupt_databases` folder
 * under `filesDir` — before the platform's default recovery deletes them — so
 * the crash can be analysed. The default recovery then takes over: the files
 * are removed and the next open recreates a fresh, empty database instead of
 * the app crashing forever on a corrupt file.
 *
 * androidx.sqlite wires corruption through [SupportSQLiteOpenHelper.Callback.onCorruption]
 * (via the `DatabaseErrorHandler` it installs into the framework open helper),
 * so this factory only needs to swap in a delegating callback — every other
 * callback (create/upgrade/downgrade/open/configure) is forwarded untouched to
 * the Room-generated callback. Wire it with:
 *
 * ```kotlin
 * Room.databaseBuilder(...).openHelperFactory(CorruptionRecoveryFactory()).build()
 * ```
 */
class CorruptionRecoveryFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val wrapped = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(CorruptionRecoveryCallback(configuration.callback, configuration.context))
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
            .build()
        return delegate.create(wrapped)
    }
}

/**
 * Delegating callback that copies the corrupt files for analysis before
 * handing the corruption event to the original (Room-generated) callback,
 * whose default behaviour closes the handle and deletes the files so the next
 * open recreates the database.
 */
internal class CorruptionRecoveryCallback(
    private val delegate: SupportSQLiteOpenHelper.Callback,
    private val context: Context
) : SupportSQLiteOpenHelper.Callback(delegate.version) {

    override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)

    override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)

    override fun onCorruption(db: SupportSQLiteDatabase) {
        // Copy BEFORE the default handler closes + deletes, otherwise the
        // evidence is gone for good. A copy failure must never crash the app
        // while it is already recovering from corruption.
        copyCorruptDatabase(context, db)
        // Room 2.8's callback doesn't override onCorruption, so this is the
        // androidx default: log + close + delete the corrupt files. The next
        // open then recreates a fresh database.
        delegate.onCorruption(db)
    }
}

private const val TAG = "CorruptionRecovery"
private const val BACKUP_DIR = "corrupt_databases"
private const val MAX_BACKUPS_PER_DB = 8

internal fun copyCorruptDatabase(context: Context, db: SupportSQLiteDatabase) {
    val path = db.path ?: return
    val targetDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val dbName = File(path).name

    // Copy each existing file (db + optional WAL/SHM sidecars) verbatim.
    listOf(
        path to ".db",
        "$path-wal" to ".db-wal",
        "$path-shm" to ".db-shm"
    ).forEach { (source, suffix) ->
        val src = File(source)
        if (src.exists() && src.isFile) {
            val dst = File(targetDir, "$dbName-$stamp$suffix")
            try {
                src.inputStream().use { input ->
                    dst.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
            } catch (e: Exception) {
                // Keep recovery going even if the copy fails (e.g. disk full).
                Log.w(TAG, "Failed to back up corrupt database file $source", e)
            }
        }
    }
    Log.w(TAG, "Corrupt database backed up to ${targetDir.absolutePath} for analysis ($dbName)")

    // Bound the archive: keep the newest MAX_BACKUPS_PER_DB snapshots per db.
    pruneOldBackups(targetDir, "$dbName-")
}

private fun pruneOldBackups(targetDir: File, prefix: String) {
    val snapshots = targetDir.listFiles { file -> file.isFile && file.name.startsWith(prefix) }
        ?.groupBy { it.name.substringAfter(prefix).substringBefore('.') }
        ?.keys
        ?.sorted()
        ?: return
    val excess = snapshots.size - MAX_BACKUPS_PER_DB
    if (excess <= 0) return
    snapshots.take(excess).forEach { stamp ->
        targetDir.listFiles { file -> file.isFile && file.name.startsWith("$prefix$stamp.") }
            ?.forEach { it.delete() }
    }
}
