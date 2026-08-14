package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip tests for [ActiveTriggerStore] — the durable active-state ledger
 * that lets monitors (battery, connectivity, ringer, …) survive a process or
 * service restart. When a task was triggered before the restart, its monitor
 * re-arms from this store; when the condition later ends, the exit behavior
 * fires. These tests pin the persistence contract that re-arm depends on:
 * writes from one store instance are readable by a fresh instance (the
 * "restart"), composite keys are cleared atomically, and sources are scoped.
 */
@RunWith(RobolectricTestRunner::class)
class ActiveTriggerStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A brand-new store instance over the same context — simulates a restart. */
    private fun freshStore() = ActiveTriggerStore(context)

    @Before
    fun clearLedger() {
        // Robolectric shares one Application (and its DataStore cache) across
        // test methods in the class, so reset the sources each test for
        // isolation.
        runBlocking {
            val store = freshStore()
            store.clearSource("battery")
            store.clearSource("connectivity")
        }
    }

    // --- Round-trip persistence across "restarts" ---------------------------

    @Test
    fun markActive_isReadableByAFreshInstance() = runBlocking {
        val writer = freshStore()
        writer.markActive("battery", "auto-1")
        writer.markActive("battery", "auto-2|AC")

        // A fresh instance (process restart) sees the marks written before it.
        val reader = freshStore()
        assertEquals(
            setOf("auto-1", "auto-2|AC"),
            reader.activeKeys("battery")
        )
    }

    @Test
    fun clearActive_removesOnlyThatKey_keepsOthers() = runBlocking {
        val store = freshStore()
        store.markActive("battery", "a")
        store.markActive("battery", "b")

        store.clearActive("battery", "a")

        assertEquals(setOf("b"), store.activeKeys("battery"))
    }

    @Test
    fun clearActive_isSafeWhenNothingMarked() = runBlocking {
        val store = freshStore()
        store.clearActive("battery", "missing")
        assertTrue(store.activeKeys("battery").isEmpty())
    }

    // --- Composite keys are cleared atomically ------------------------------

    @Test
    fun clearAutomation_removesPlainAndCompositeKeysOfThatAutomation() = runBlocking {
        val store = freshStore()
        // Battery level-only trigger (plain id) + charger-specific (id|AC).
        store.markActive("battery", "auto-1")
        store.markActive("battery", "auto-1|AC")
        store.markActive("battery", "auto-1|WIRELESS")
        store.markActive("battery", "other-auto")

        store.clearAutomation("battery", "auto-1")

        assertEquals(setOf("other-auto"), store.activeKeys("battery"))
    }

    // --- Source scoping ------------------------------------------------------

    @Test
    fun sourcesAreScoped_clearSourceOnlyTouchesItsOwnSource() = runBlocking {
        val store = freshStore()
        store.markActive("battery", "b1")
        store.markActive("connectivity", "c1")

        store.clearSource("battery")

        assertTrue(store.activeKeys("battery").isEmpty())
        assertEquals(setOf("c1"), store.activeKeys("connectivity"))
    }

    @Test
    fun sameAutomationUnderDifferentSourcesDoNotCollide() = runBlocking {
        val store = freshStore()
        store.markActive("battery", "auto-1")
        store.markActive("connectivity", "auto-1")

        store.clearAutomation("battery", "auto-1")

        assertTrue(store.activeKeys("battery").isEmpty())
        // The connectivity mark is untouched — clearing one source must not
        // clear the same automation under another monitor.
        assertEquals(setOf("auto-1"), store.activeKeys("connectivity"))
    }

    // --- Expiry: crash leftovers must not fire a late exit ------------------

    @Test
    fun purgeExpired_dropsKeysOlderThanMaxAge_keepsFreshAndLegacy() = runBlocking {
        var now = 1_000_000L
        val store = ActiveTriggerStore(context) { now }

        // Fresh key written now.
        store.markActive("battery", "fresh")
        // Old key written 8 days ago (beyond the default 7-day window).
        now -= 8L * 24 * 60 * 60 * 1000
        store.markActive("battery", "stale")
        // Reset to present so the purge's cutoff is computed against "now".
        now = 1_000_000L

        val purged = store.purgeExpired()

        assertEquals(1, purged)
        assertEquals(setOf("fresh"), store.activeKeys("battery"))
    }

    @Test
    fun purgeExpired_customWindow_dropsOnlyWhatCrossedIt() = runBlocking {
        var now = 10_000L
        val store = ActiveTriggerStore(context) { now }
        store.markActive("connectivity", "recent")
        now -= 5_000L
        store.markActive("connectivity", "older")
        now = 10_000L

        // 2-second window: only "older" (5s old) crossed it.
        assertEquals(1, store.purgeExpired(maxAgeMillis = 2_000L))
        assertEquals(setOf("recent"), store.activeKeys("connectivity"))
    }

    @Test
    fun activeKeys_hidesExpiredKeysEvenIfPurgeWasSkipped() = runBlocking {
        var now = 5_000L
        val store = ActiveTriggerStore(context) { now }
        store.markActive("battery", "survivor")
        store.markActive("battery", "zombie")
        // 2 weeks pass without the boot-time purge running.
        now += 14L * 24 * 60 * 60 * 1000

        // The stale key is invisible to a reader (default 7-day window), the
        // fresh-then-aged one too — both are > 7 days old now.
        assertTrue(store.activeKeys("battery").isEmpty())
    }

    @Test
    fun clearActive_removesKeyCompletely_noZombieAfterLaterPurge() = runBlocking {
        var now = 1_000L
        val store = ActiveTriggerStore(context) { now }
        store.markActive("battery", "k")
        store.clearActive("battery", "k")
        // A long crash window passes after the clear.
        now += 10L * 24 * 60 * 60 * 1000

        assertEquals(0, store.purgeExpired())
        assertTrue(store.activeKeys("battery").isEmpty())
    }

    @Test
    fun purgeExpired_persistsAcrossInstances_restartSeesSameResult() = runBlocking {
        var now = 1_000_000L
        val writer = ActiveTriggerStore(context) { now }
        writer.markActive("battery", "fresh")
        now -= 8L * 24 * 60 * 60 * 1000
        writer.markActive("battery", "stale")
        now = 1_000_000L

        // "Restart": a brand-new store instance over the same file runs the
        // boot purge; only the stale key is dropped.
        val restarted = ActiveTriggerStore(context) { now }
        assertEquals(1, restarted.purgeExpired())
        assertEquals(setOf("fresh"), restarted.activeKeys("battery"))
    }
}
