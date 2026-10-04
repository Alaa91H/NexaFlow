package com.nexaflow.core.security

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AesGcmEnvelopeCodecTest {

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun roundTripUsesVersionedEnvelope() {
        val key = key()
        val encoded = AesGcmEnvelopeCodec.encrypt(key, "provider-a", "secret")
        assertTrue(encoded.startsWith(AesGcmEnvelopeCodec.PREFIX))
        assertEquals("secret", AesGcmEnvelopeCodec.decrypt(key, "provider-a", encoded))
    }

    @Test
    fun ciphertextCannotMoveBetweenStorageKeys() {
        val key = key()
        val encoded = AesGcmEnvelopeCodec.encrypt(key, "provider-a", "secret")
        val failure = runCatching {
            AesGcmEnvelopeCodec.decrypt(key, "provider-b", encoded)
        }.exceptionOrNull()
        assertTrue(failure is AEADBadTagException)
    }

    @Test
    fun tamperingFailsAuthentication() {
        val key = key()
        val encoded = AesGcmEnvelopeCodec.encrypt(key, "slot", "secret")
        val payload = Base64.getDecoder().decode(
            encoded.removePrefix(AesGcmEnvelopeCodec.PREFIX)
        )
        val ciphertextIndex = payload.lastIndex
        payload[ciphertextIndex] = (payload[ciphertextIndex].toInt() xor 0x01).toByte()
        val mutated = AesGcmEnvelopeCodec.PREFIX +
            Base64.getEncoder().withoutPadding().encodeToString(payload)
        assertTrue(runCatching {
            AesGcmEnvelopeCodec.decrypt(key, "slot", mutated)
        }.isFailure)
    }

    @Test
    fun legacyEnvelopeCanBeDecryptedForMigration() {
        val key = key()
        val encrypted = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        }
        val plaintext = "legacy-secret".toByteArray(Charsets.UTF_8)
        val payload = encrypted.iv + encrypted.doFinal(plaintext)
        val legacyValue = Base64.getEncoder().withoutPadding().encodeToString(payload)

        assertFalse(AesGcmEnvelopeCodec.isV2(legacyValue))
        assertEquals("legacy-secret", AesGcmEnvelopeCodec.decryptLegacy(key, legacyValue))
    }

    @Test
    fun decryptRejectsUnsupportedEnvelopeVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmEnvelopeCodec.decrypt(key(), "slot", "v1:encoded")
        }
    }

    @Test
    fun decryptRejectsTruncatedVersionedAndLegacyPayloads() {
        val key = key()
        val truncated = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(12))

        assertThrows(IllegalArgumentException::class.java) {
            AesGcmEnvelopeCodec.decrypt(key, "slot", AesGcmEnvelopeCodec.PREFIX + truncated)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmEnvelopeCodec.decryptLegacy(key, truncated)
        }
    }
}
