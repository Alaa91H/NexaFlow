package com.nexaflow.core.common

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Completes the common-module contract surface: the [Outcome] algebra, the
 * testable dispatcher bundle, display-info generation mapping, and the
 * AUTO-matching predicate used by the connectivity trigger.
 */
class CommonModuleContractTest {

    @Test
    fun `outcome success and failure expose the right flags and accessors`() {
        val success: Outcome<Int> = Outcome.Success(3)
        val failure: Outcome<Int> = Outcome.Failure("nope", IllegalStateException("why"))

        assertTrue(success.isSuccess)
        assertFalse(failure.isSuccess)
        assertEquals(3, success.getOrNull())
        assertNull(failure.getOrNull())
        assertEquals("nope", (failure as Outcome.Failure).message)
        assertTrue((failure as Outcome.Failure).cause is IllegalStateException)
    }

    @Test
    fun `app dispatchers bundle accepts a test dispatcher set`() {
        val testDispatcher = StandardTestDispatcher()
        val dispatchers = AppDispatchers(
            io = testDispatcher,
            default = testDispatcher,
            main = testDispatcher
        )
        assertEquals(testDispatcher, dispatchers.io)
        assertEquals(testDispatcher, dispatchers.default)
        assertEquals(testDispatcher, dispatchers.main)
    }

    @Test
    fun `AUTO matches any known generation but not an unreadable state`() {
        assertTrue(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, CellularNetworkReader.GENERATION_5G))
        assertTrue(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, CellularNetworkReader.GENERATION_2G))
        assertFalse(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.AUTO, null))
        assertTrue(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.GENERATION_4G, CellularNetworkReader.GENERATION_4G))
        assertFalse(CellularNetworkReader.matchesNetworkMode(CellularNetworkReader.GENERATION_4G, CellularNetworkReader.GENERATION_5G))
    }
}

/** Display-info mapping requires SDK 30+ semantics — pinned via shadow objects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CellularDisplayInfoTest {

    private fun displayInfo(networkType: Int, override: Int): TelephonyDisplayInfo {
        // Shadows cannot construct this final class directly; build via
        // reflection over its two-arg constructor (API 30+).
        val ctor = TelephonyDisplayInfo::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        ctor.isAccessible = true
        return ctor.newInstance(networkType, override)
    }

    @Test
    fun `display override NR variants map to 5G`() {
        assertEquals(
            CellularNetworkReader.GENERATION_5G,
            CellularNetworkReader.generationOf(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED)
            )
        )
        assertEquals(
            CellularNetworkReader.GENERATION_5G,
            CellularNetworkReader.generationOf(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA)
            )
        )
    }

    @Test
    fun `non-NR display override falls back to the underlying network type`() {
        assertEquals(
            CellularNetworkReader.GENERATION_4G,
            CellularNetworkReader.generationOf(
                displayInfo(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE)
            )
        )
    }
}
