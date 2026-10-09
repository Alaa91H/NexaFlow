package com.nexaflow.core.security

import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OccurrenceIdentityHmacTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "HmacSHA256")

    @Test
    fun digestUsesStableNulDelimitedHmacAndCachesKey() {
        var keyLoads = 0
        val hmac = AndroidKeystoreOccurrenceIdentityHmac(keyProvider = { keyLoads++; key })

        val actual = hmac.digest("calendar", "event-17")
        val expected = Mac.getInstance("HmacSHA256").run {
            init(key)
            HexFormat.of().formatHex(doFinal("calendar\u0000event-17".toByteArray(Charsets.UTF_8)))
        }

        assertEquals(expected, actual)
        assertEquals(actual, hmac.digest("calendar", "event-17"))
        assertNotEquals(actual, hmac.digest("calendar", "event-18"))
        assertEquals(1, keyLoads)
    }

    @Test
    fun digestRejectsMissingIdentityParts() {
        val hmac = AndroidKeystoreOccurrenceIdentityHmac(keyProvider = { key })
        assertThrows(IllegalArgumentException::class.java) { hmac.digest(" ", "event") }
        assertThrows(IllegalArgumentException::class.java) { hmac.digest("calendar", "") }
    }
}
