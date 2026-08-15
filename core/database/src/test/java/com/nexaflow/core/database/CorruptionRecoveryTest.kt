package com.nexaflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Corruption-recovery gate (Robolectric + real SQLite): a database whose file
 * is non-SQLite garbage must trigger the [CorruptionRecoveryFactory] handler,
 * which copies the corrupt database verbatim into a private folder for later
 * analysis, while the framework's built-in recovery deletes the garbage and
 * the open completes transparently with a freshly recreated database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CorruptionRecoveryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "corrupt-recovery-test.db"

    @After
    fun tearDown() {
        deleteFile(dbName)
        deleteFile("$dbName-wal")
        deleteFile("$dbName-shm")
        File(context.filesDir, "corrupt_databases").deleteRecursively()
    }

    private fun deleteFile(name: String) {
        File(context.getDatabasePath(name).path).delete()
    }

    private fun buildDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .openHelperFactory(CorruptionRecoveryFactory())
            .build()

    @Test
    fun corruptDatabase_isCopiedForAnalysis_andRecreatedTransparently() {
        val garbage = ByteArray(4096) { it.toByte() }
        File(context.getDatabasePath(dbName).path).apply {
            parentFile?.mkdirs()
            writeBytes(garbage)
        }
        File(context.getDatabasePath("$dbName-wal").path).writeBytes(garbage)
        File(context.getDatabasePath("$dbName-shm").path).writeBytes(garbage)

        // 1. First open: the handler archives the corrupt files, the
        //    framework deletes them, and the open completes transparently
        //    with a fresh database (no crash, no permanent failure).
        val db = buildDatabase()
        db.openHelper.writableDatabase

        // 2. The corrupt evidence was preserved byte-for-byte for analysis.
        val backupDir = File(context.filesDir, "corrupt_databases")
        val copies = backupDir.listFiles { f -> f.isFile && f.name.startsWith("$dbName-") }
            .orEmpty()
        assertTrue("expected the corrupt database to be archived", copies.isNotEmpty())
        val dbCopy = copies.first { it.name.endsWith(".db") }
        assertTrue("archived db must be byte-identical to the corrupt file", garbage.contentEquals(dbCopy.readBytes()))

        // 3. The live file is a valid SQLite database again (recovery deleted
        //    the garbage and recreated the schema).
        val live = File(context.getDatabasePath(dbName).path)
        assertTrue(
            "live db must be a valid SQLite file",
            String(live.readBytes(), Charsets.US_ASCII).startsWith(SQLITE_HEADER)
        )

        // 4. The recreated database is fully usable end to end.
        runBlocking {
            db.automationDao().insertAutomation(
                AutomationEntity(
                    id = "a1",
                    name = "Recovered",
                    description = "",
                    icon = "sunny",
                    iconColor = 0xFFFFFFFF,
                    backgroundColor = 0xFFE8A33D,
                    category = "general",
                    priority = 1,
                    enabled = true,
                    triggersJson = "[]",
                    actionsJson = "[]",
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
            val loaded = db.automationDao().getAllAutomations().first()
            assertEquals(1, loaded.size)
            assertEquals("Recovered", loaded.single().name)
        }
        db.close()
    }

    @Test
    fun healthyDatabase_opensNormally_withoutBackupArtifacts() {
        // A healthy database must not be touched by the handler at all.
        val db = buildDatabase()
        db.openHelper.writableDatabase
        val backupDir = File(context.filesDir, "corrupt_databases")
        assertTrue(!backupDir.exists() || backupDir.listFiles().orEmpty().isEmpty())
        db.close()
    }

    private companion object {
        const val SQLITE_HEADER = "SQLite format 3"
    }
}
