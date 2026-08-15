package com.nexaflow.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the database maintenance statement the periodic worker runs:
 * `PRAGMA optimize` must execute cleanly on a writable database and leave
 * normal DAO operations working (it re-analyzes the query planner's
 * statistics after schema/volume changes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseMaintenanceTest {

    @Test
    fun pragmaOptimizeRunsAndDatabaseStillWorks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // The same statement MaintenanceWorker.doWork() executes.
            db.openHelper.writableDatabase.query("PRAGMA optimize").use { cursor ->
                assertNotNull(cursor)
            }
            // PRAGMA optimize is a no-op statement for most invocations; it
            // must never break normal DAO traffic afterwards.
            val pruned = runBlocking { db.executionDao().pruneOlderThan(System.currentTimeMillis()) }
            assertTrue(pruned >= 0)
        } finally {
            db.close()
        }
    }
}
