package com.nexaflow.core.security

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
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
        val mutated = encoded.dropLast(1) + if (encoded.last() == 'A') "B" else "A"
        assertTrue(runCatching {
            AesGcmEnvelopeCodec.decrypt(key, "slot", mutated)
        }.isFailure)
    }
}
