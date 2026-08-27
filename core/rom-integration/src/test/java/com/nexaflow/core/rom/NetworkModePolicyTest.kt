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
        // AOSP NetworkTypeBitMask: GSM(1<<15)|GPRS(1<<0)|EDGE(1<<1).
        assertEquals(32_771L, req.bitmask)
    }

    @Test
    fun `3G maps to WCDMA_ONLY legacy int and the UMTS family bitmask`() {
        val req = NetworkModePolicy.request("3G")
        assertEquals(2, req.legacyInt)
        assertEquals(NetworkModePolicy.BITMASK_3G, req.bitmask)
        // AOSP NetworkTypeBitMask: UMTS(1<<2)|HSDPA(1<<7)|HSUPA(1<<8)|HSPA(1<<9)|HSPAP(1<<14).
        assertEquals(17_284L, req.bitmask)
    }

    @Test
    fun `4G maps to the correct LTE_ONLY legacy int 11 - not the old broken 13`() {
        val req = NetworkModePolicy.request("4G")
        assertEquals(11, req.legacyInt)
        assertEquals(NetworkModePolicy.BITMASK_4G, req.bitmask)
        // AOSP NetworkTypeBitMask: LTE(1<<12)|LTE_CA(1<<18).
        assertEquals(266_240L, req.bitmask)
    }

    @Test
    fun `5G maps to the NR bitmask with a version-tolerant legacy fallback`() {
        val req = NetworkModePolicy.request("5G")
        assertEquals(NetworkModePolicy.BITMASK_5G, req.bitmask)
        assertEquals(1L shl 19, req.bitmask)
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
        // AOSP decimal: 266240 == LTE(1<<12) | LTE_CA(1<<18).
        assertTrue(NetworkModePolicy.coversReadBack("266240", req4g))
        // Decimal with the AOSP NR bit still set (266240 | 1<<19) must not
        // confirm LTE-only: preferred mode is a restriction, not a minimum.
        assertFalse(NetworkModePolicy.coversReadBack("790528", req4g))
        // Binary form of BITMASK_4G.
        assertTrue(NetworkModePolicy.coversReadBack(java.lang.Long.toString(NetworkModePolicy.BITMASK_4G, 2), req4g))
        // A read-back that lost the LTE bits must not confirm 4G.
        assertFalse(NetworkModePolicy.coversReadBack("1", req4g))
    }

    @Test
    fun `shell read-back parser preserves confirmed decimal binary and named masks`() {
        val nrLte = NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G

        assertEquals(nrLte, NetworkModePolicy.parseReadBackMask(nrLte.toString()))
        assertEquals(
            NetworkModePolicy.BITMASK_4G,
            NetworkModePolicy.parseReadBackMask(
                java.lang.Long.toString(NetworkModePolicy.BITMASK_4G, 2)
            )
        )
        assertEquals(nrLte, NetworkModePolicy.parseReadBackMask("LTE|NR"))
        assertEquals(
            NetworkModePolicy.BITMASK_4G,
            NetworkModePolicy.parseReadBackMask("LTE|LTE_CA")
        )
        assertEquals(NetworkModePolicy.BITMASK_5G, NetworkModePolicy.parseReadBackMask("NR"))
        assertEquals(
            NetworkModePolicy.BITMASK_4G,
            NetworkModePolicy.parseReadBackMask(
                "Allowed network types for slot 0: ${NetworkModePolicy.BITMASK_4G}"
            )
        )
        assertEquals(null, NetworkModePolicy.parseReadBackMask(""))
        assertEquals(null, NetworkModePolicy.parseReadBackMask("-1"))
        assertEquals(null, NetworkModePolicy.parseReadBackMask("unknown option"))
        assertEquals(null, NetworkModePolicy.parseReadBackMask("command failed: 1581056"))
    }

    @Test
    fun `AOSP default network profile maps known modem modes without inventing support`() {
        assertEquals(
            NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_2G or NetworkModePolicy.BITMASK_3G,
            NetworkModePolicy.defaultNetworkModeMask(9)
        )
        assertEquals(
            NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_TD_SCDMA or
                NetworkModePolicy.BITMASK_CDMA or NetworkModePolicy.BITMASK_EVDO or
                NetworkModePolicy.BITMASK_2G or NetworkModePolicy.BITMASK_3G,
            NetworkModePolicy.defaultNetworkModeMask(22)
        )
        assertEquals(NetworkModePolicy.BITMASK_5G, NetworkModePolicy.defaultNetworkModeMask(23))
        assertEquals(
            NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G,
            NetworkModePolicy.defaultNetworkModeMask(24)
        )
        assertEquals(null, NetworkModePolicy.defaultNetworkModeMask(-1))
        assertEquals(null, NetworkModePolicy.defaultNetworkModeMask(34))
    }

    @Test
    fun `default network property is slot-aware and rejects malformed or ambiguous values`() {
        val lteGsmWcdma = NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_2G or
            NetworkModePolicy.BITMASK_3G
        val nrLte = NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G

        assertEquals(lteGsmWcdma, NetworkModePolicy.defaultNetworkMaskFromProperty("9", 0))
        assertEquals(nrLte, NetworkModePolicy.defaultNetworkMaskFromProperty("9,24", 1))
        assertEquals(null, NetworkModePolicy.defaultNetworkMaskFromProperty("9,24", -1))
        assertEquals(null, NetworkModePolicy.defaultNetworkMaskFromProperty("9,unknown", 0))
        assertEquals(null, NetworkModePolicy.defaultNetworkMaskFromProperty("34", 0))
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
    fun `effective mask intersects user and carrier restrictions without inventing a result`() {
        val user = NetworkModePolicy.BITMASK_5G or NetworkModePolicy.BITMASK_4G
        val carrier = NetworkModePolicy.BITMASK_4G or NetworkModePolicy.BITMASK_3G

        assertEquals(NetworkModePolicy.BITMASK_4G, NetworkModePolicy.effectiveMask(user, carrier))
        assertEquals(null, NetworkModePolicy.effectiveMask(user, null))
        assertEquals(null, NetworkModePolicy.effectiveMask(null, carrier))
        assertEquals(
            null,
            NetworkModePolicy.effectiveMask(NetworkModePolicy.BITMASK_5G, NetworkModePolicy.BITMASK_4G)
        )
    }

    @Test
    fun `subscription selection preserves a valid saved SIM before the active data SIM`() {
        assertEquals(
            7,
            NetworkModePolicy.selectSubscriptionId(
                savedSubscriptionId = 7,
                activeDataSubscriptionId = 2,
                availableSubscriptionIds = listOf(2, 7)
            )
        )
    }

    @Test
    fun `subscription selection chooses active data SIM then first confirmed SIM`() {
        assertEquals(
            7,
            NetworkModePolicy.selectSubscriptionId(
                savedSubscriptionId = 99,
                activeDataSubscriptionId = 7,
                availableSubscriptionIds = listOf(2, 7)
            )
        )
        assertEquals(
            2,
            NetworkModePolicy.selectSubscriptionId(
                savedSubscriptionId = null,
                activeDataSubscriptionId = 99,
                availableSubscriptionIds = listOf(2, 7)
            )
        )
        assertEquals(
            null,
            NetworkModePolicy.selectSubscriptionId(
                savedSubscriptionId = null,
                activeDataSubscriptionId = null,
                availableSubscriptionIds = emptyList()
            )
        )
    }

    @Test
    fun `coversReadBack rejects failure outputs`() {
        val req = NetworkModePolicy.request("4G")
        assertFalse(NetworkModePolicy.coversReadBack("", req))
        assertFalse(NetworkModePolicy.coversReadBack("-1", req))
        assertFalse(NetworkModePolicy.coversReadBack("UNKNOWN", req))
    }
}
