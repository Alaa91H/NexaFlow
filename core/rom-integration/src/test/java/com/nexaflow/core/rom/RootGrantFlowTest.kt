package com.nexaflow.core.rom

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the root-detection / root-grant flow used by the permission
 * manager and the task editor.
 *
 * The real `su` process spawning is replaced by the internal test seams
 * ([SystemAppStatusDetector.pathResolution], [SystemAppStatusDetector.rootProbe]
 * and [PrivilegedRunner.suProbe]) so every root manager's behavior can be
 * simulated deterministically on any host (Magisk / KernelSU / APatch expose
 * `su` on PATH; legacy SuperSU only at fixed paths; denial must not re-prompt).
 */
class RootGrantFlowTest {

    private lateinit var probeCalls: MutableList<Array<String>>

    @Before
    fun setUp() {
        probeCalls = mutableListOf()
        // The 5s probe TTL would otherwise leak a cached result between tests.
        SystemAppStatusDetector.refreshRootAvailability()
    }

    @After
    fun tearDown() {
        SystemAppStatusDetector.pathResolution = null
        SystemAppStatusDetector.rootProbe = null
        PrivilegedRunner.suProbe = null
        SystemAppStatusDetector.refreshRootAvailability()
    }

    // ──────────────────────────────────────────────────────────────
    // isSuBinaryAvailable — where does the su binary live?
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `isSuBinaryAvailable true when su is on PATH (Magisk KernelSU APatch)`() {
        SystemAppStatusDetector.pathResolution = { true }
        assertTrue(SystemAppStatusDetector.isSuBinaryAvailable())
    }

    @Test
    fun `suPaths covers Magisk KernelSU APatch and legacy locations`() {
        // Guards against a regression that drops one of the fixed su paths —
        // each root manager must stay detectable on devices where su is not
        // exposed through PATH.
        val paths = SystemAppStatusDetector.suPaths
        assertTrue("Magisk busybox path missing", paths.any { it.contains("magisk") })
        assertTrue("KernelSU su path missing", paths.any { it.contains("ksu") })
        assertTrue("APatch su path missing", paths.any { it.contains("ap/bin/su") })
        assertTrue("legacy /system/bin/su missing", paths.contains("/system/bin/su"))
    }

    @Test
    fun `isSuBinaryAvailable false when no su anywhere`() {
        SystemAppStatusDetector.pathResolution = { false }
        assertFalse(SystemAppStatusDetector.isSuBinaryAvailable())
    }

    // ──────────────────────────────────────────────────────────────
    // isRootAvailable — has root already been granted?
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `isRootAvailable true when su answers uid=0 (granted)`() {
        SystemAppStatusDetector.pathResolution = { true }
        SystemAppStatusDetector.rootProbe = { true }
        assertTrue(SystemAppStatusDetector.isRootAvailable())
    }

    @Test
    fun `isRootAvailable false when su is present but denied`() {
        SystemAppStatusDetector.pathResolution = { true }
        SystemAppStatusDetector.rootProbe = { false }
        assertFalse(SystemAppStatusDetector.isRootAvailable())
    }

    @Test
    fun `refreshRootAvailability invalidates the cached probe`() {
        // First call caches true for the 5s TTL; after invalidation the very
        // next call must re-probe and report the new (denied) reality.
        SystemAppStatusDetector.pathResolution = { true }
        SystemAppStatusDetector.rootProbe = { true }
        assertTrue(SystemAppStatusDetector.isRootAvailable())
        SystemAppStatusDetector.rootProbe = { false }
        assertTrue(
            "cached value must be reused within the TTL window",
            SystemAppStatusDetector.isRootAvailable()
        )
        SystemAppStatusDetector.refreshRootAvailability()
        assertFalse(
            "refreshRootAvailability must force a fresh probe",
            SystemAppStatusDetector.isRootAvailable()
        )
    }

    // ──────────────────────────────────────────────────────────────
    // triggerSuPrompt — the one-tap grant dialog flow
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `triggerSuPrompt granted via bare su on PATH (Magisk)`() {
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            if (cmd.contentEquals(arrayOf("su", "-c", "id"))) true else false
        }
        assertTrue(PrivilegedRunner.triggerSuPrompt())
        assertEquals("bare su attempt must be tried first", 1, probeCalls.size)
    }

    @Test
    fun `triggerSuPrompt falls back to su 0 form (APatch)`() {
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            // APatch builds may reject `su -c` but accept `su 0`.
            if (cmd.contentEquals(arrayOf("su", "-c", "id"))) null else true
        }
        assertTrue(PrivilegedRunner.triggerSuPrompt())
        assertTrue(probeCalls.any { it.contentEquals(arrayOf("su", "0", "-c", "id")) })
    }

    @Test
    fun `triggerSuPrompt falls back to system bin su when not on PATH (legacy)`() {
        // Legacy SuperSU: the binary IS available (isSuBinaryAvailable passes
        // via pathResolution=true), but the PATH-resolved `su` forms don't run
        // (null = not found) and only /system/bin/su works.
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            if (cmd[0] == "/system/bin/su") true else null
        }
        assertTrue(PrivilegedRunner.triggerSuPrompt())
        assertEquals(
            "must try bare su, su 0, then /system/bin/su",
            listOf("su", "su", "/system/bin/su"),
            probeCalls.map { it[0] }
        )
    }

    @Test
    fun `triggerSuPrompt returns false on denial without re-prompting`() {
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            false // user denied the grant dialog
        }
        assertFalse(PrivilegedRunner.triggerSuPrompt())
        assertEquals(
            "a denial must stop the loop — never spam repeated grant dialogs",
            1,
            probeCalls.size
        )
    }

    @Test
    fun `triggerSuPrompt returns false when no su binary exists`() {
        SystemAppStatusDetector.pathResolution = { false }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            true
        }
        assertFalse(PrivilegedRunner.triggerSuPrompt())
        assertTrue("no su binary → no process may even be attempted", probeCalls.isEmpty())
    }

    @Test
    fun `triggerSuPrompt returns false when every form is missing`() {
        // Binary exists somewhere, but none of the three invocation forms run.
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            null // command not found everywhere
        }
        assertFalse(PrivilegedRunner.triggerSuPrompt())
        assertEquals(3, probeCalls.size)
    }

    @Test
    fun `triggerSuPrompt granted on first attempt returns true immediately`() {
        SystemAppStatusDetector.pathResolution = { true }
        PrivilegedRunner.suProbe = { cmd ->
            probeCalls += cmd
            true
        }
        assertTrue(PrivilegedRunner.triggerSuPrompt())
        assertEquals("success must short-circuit", 1, probeCalls.size)
    }
}
