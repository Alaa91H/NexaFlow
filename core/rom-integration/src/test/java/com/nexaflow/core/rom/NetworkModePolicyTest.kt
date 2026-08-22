package com.nexaflow.core.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atomic coverage of the network-generation mapping — the exact table that
 * used to hard-code wrong PhoneConstants values (13 = TD-SCDMA-only, not
 * LTE-only) and made the action silently no-op.
 */
class NetworkModePolicyTest {

    @Test
    fun `2G maps to GSM_ONLY legacy int and the GSM family bitmask`() {
        val req = NetworkModePolicy.request("2G")
        assertEquals("2G", req.label)
        assertEquals(1, req.legacyInt)
        assertEquals(NetworkModePolicy.BITMASK_2G, req.bitmask)
        // Pins the framework contract: GSM(1<<16)|GPRS(1<<1)|EDGE(1<<2).
        assertEquals(65_542L, req.bitmask)
    }

    @Test
    fun `3G maps to WCDMA_ONLY legacy int and the UMTS family bitmask`() {
        val req = NetworkModePolicy.request("3G")
        assertEquals(2, req.legacyInt)
        assertEquals(NetworkModePolicy.BITMASK_3G, req.bitmask)
        // UMTS(1<<3)|HSDPA(1<<8)|HSUPA(1<<9)|HSPA(1<<10)|HSPAP(1<<15).
        assertEquals(34_568L, req.bitmask)
    }

    @Test
    fun `4G maps to the correct LTE_ONLY legacy int 11 - not the old broken 13`() {
        val req = NetworkModePolicy.request("4G")
        assertEquals(11, req.legacyInt)
        assertEquals(NetworkModePolicy.BITMASK_4G, req.bitmask)
        // LTE(1<<13)|LTE_CA(1<<19).
        assertEquals(532_480L, req.bitmask)
    }

    @Test
    fun `5G maps to the NR bitmask with a version-tolerant legacy fallback`() {
        val req = NetworkModePolicy.request("5G")
        assertEquals(NetworkModePolicy.BITMASK_5G, req.bitmask)
        assertEquals(1L shl 20, req.bitmask)
        // 22 = NR+LTE+legacy in the PhoneConstants table (the closest stable entry).
        assertEquals(22, req.legacyInt)
    }

    @Test
    fun `AUTO covers every generation family`() {
        val req = NetworkModePolicy.request("AUTO")
        assertEquals("AUTO", req.label)
        assertEquals(NetworkModePolicy.BITMASK_AUTO, req.bitmask)
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_2G, req.bitmask))
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_3G, req.bitmask))
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_4G, req.bitmask))
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_5G, req.bitmask))
    }

    @Test
    fun `legacy full-auto respects NR support`() {
        assertEquals(22, NetworkModePolicy.legacyAuto(nrSupported = true))
        assertEquals(10, NetworkModePolicy.legacyAuto(nrSupported = false))
        assertEquals(22, NetworkModePolicy.request("AUTO", nrSupported = true).legacyInt)
        assertEquals(10, NetworkModePolicy.request("AUTO", nrSupported = false).legacyInt)
    }

    @Test
    fun `unknown and blank labels fall back to AUTO so stale configs cannot lock the radio`() {
        val auto = NetworkModePolicy.request("AUTO")
        assertEquals(auto, NetworkModePolicy.request(""))
        assertEquals(auto, NetworkModePolicy.request("LTE_CA"))
        assertEquals(auto, NetworkModePolicy.request("unknown"))
    }

    @Test
    fun `mode labels are exact matches - lowercase is not normalized`() {
        // "4g" is not a known label, so it must fall back to AUTO (no silent casing).
        assertEquals(NetworkModePolicy.request("AUTO"), NetworkModePolicy.request("4g"))
        assertEquals("4G", NetworkModePolicy.request("4G").label)
    }

    @Test
    fun `covers accepts exact and superset read-backs`() {
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_4G, NetworkModePolicy.BITMASK_4G))
        // Radio reports LTE + NR although only LTE was requested → still applied.
        val withExtra = NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_5G
        assertTrue(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_4G, withExtra))
    }

    @Test
    fun `covers rejects a missing generation`() {
        // Read-back lost the NR bit → 5G was not actually applied.
        assertFalse(
            NetworkModePolicy.covers(
                NetworkModePolicy.BITMASK_5G,
                NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_3G
            )
        )
        assertFalse(NetworkModePolicy.covers(NetworkModePolicy.BITMASK_AUTO, 0L))
    }

    @Test
    fun `bitmask families are mutually distinct`() {
        val masks = listOf(
            NetworkModePolicy.BITMASK_2G,
            NetworkModePolicy.BITMASK_3G,
            NetworkModePolicy.BITMASK_4G,
            NetworkModePolicy.BITMASK_5G
        )
        for (i in masks.indices) {
            for (j in masks.indices) {
                if (i != j) {
                    assertEquals(0L, masks[i] and masks[j])
                }
            }
        }
    }

    @Test
    fun `coversReadBack accepts AOSP band-name output`() {
        val req4g = NetworkModePolicy.request("4G")
        assertTrue(NetworkModePolicy.coversReadBack("LTE", req4g))
        assertTrue(NetworkModePolicy.coversReadBack("LTE|LTE_CA", req4g))
        // NR still present means 4G-only was NOT applied.
        assertFalse(NetworkModePolicy.coversReadBack("LTE|NR", req4g))
        val req5g = NetworkModePolicy.request("5G")
        assertTrue(NetworkModePolicy.coversReadBack("NR", req5g))
        assertFalse(NetworkModePolicy.coversReadBack("LTE", req5g))
    }

    @Test
    fun `coversReadBack accepts numeric read-backs from OEM ROMs`() {
        val req4g = NetworkModePolicy.request("4G")
        // Decimal: 532480 == BITMASK_4G.
        assertTrue(NetworkModePolicy.coversReadBack("532480", req4g))
        // Decimal with the NR bit still set (532480 | 1<<20) must not
        // confirm LTE-only: preferred mode is a restriction, not a minimum.
        assertFalse(NetworkModePolicy.coversReadBack("1581056", req4g))
        // Binary form of BITMASK_4G.
        assertTrue(NetworkModePolicy.coversReadBack(java.lang.Long.toString(NetworkModePolicy.BITMASK_4G, 2), req4g))
        // A read-back that lost the LTE bits must not confirm 4G.
        assertFalse(NetworkModePolicy.coversReadBack("1", req4g))
    }

    @Test
    fun `options are derived only from confirmed device support`() {
        val nrLte = NetworkModePolicy.optionsFor(
            NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G
        )
        assertTrue(nrLte.any { it.id == "AUTO" && it.allowedNetworkTypes ==
            (NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G) })
        // The complete NR/LTE mask is represented once by AUTO; its
        // narrower real profiles remain selectable independently.
        assertTrue(nrLte.any { it.id == "NR" })
        assertTrue(nrLte.any { it.id == "LTE" })
        assertFalse(nrLte.any { it.label.contains("GSM") || it.label.contains("WCDMA") })
    }

    @Test
    fun `options retain a supported TD-SCDMA combination without inventing NR`() {
        val supported = NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_TD_SCDMA or
            NetworkModePolicy.BITMASK_2G or NetworkModePolicy.BITMASK_3G
        val options = NetworkModePolicy.optionsFor(supported)
        assertTrue(options.any { it.id == "AUTO" && it.allowedNetworkTypes == supported })
        assertTrue(options.any { it.id == "LTE" })
        assertTrue(options.any { it.id == "TDSCDMA" })
        assertFalse(options.any { it.label.contains("NR") })
    }

    @Test
    fun `exact match rejects preserved types outside the requested profile`() {
        assertTrue(NetworkModePolicy.matches(NetworkModePolicy.BITMASK_4G, NetworkModePolicy.BITMASK_4G))
        assertFalse(
            NetworkModePolicy.matches(
                NetworkModePolicy.BITMASK_4G,
                NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_5G
            )
        )
    }

    @Test
    fun `per subscription snapshot round trips exact masks in stable order`() {
        val input = mapOf(
            7 to (NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_5G),
            2 to NetworkModePolicy.BITMASK_2G
        )

        val encoded = NetworkModePolicy.encodeSnapshot(input)

        assertEquals(
            "network-mask-v1:2=${NetworkModePolicy.BITMASK_2G},7=${NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_5G}",
            encoded
        )
        assertEquals(input, encoded?.let(NetworkModePolicy::decodeSnapshot))
    }

    @Test
    fun `per subscription snapshot rejects legacy empty and malformed values`() {
        assertEquals(null, NetworkModePolicy.encodeSnapshot(emptyMap()))
        assertEquals(null, NetworkModePolicy.decodeSnapshot("AUTO"))
        assertEquals(null, NetworkModePolicy.decodeSnapshot("network-mask-v1:"))
        assertEquals(null, NetworkModePolicy.decodeSnapshot("network-mask-v1:7=0"))
        assertEquals(null, NetworkModePolicy.decodeSnapshot("network-mask-v1:not-a-sub=1"))
    }

    @Test
    fun `coversReadBack rejects failure outputs`() {
        val req = NetworkModePolicy.request("4G")
        assertFalse(NetworkModePolicy.coversReadBack("", req))
        assertFalse(NetworkModePolicy.coversReadBack("-1", req))
        assertFalse(NetworkModePolicy.coversReadBack("UNKNOWN", req))
    }
}
