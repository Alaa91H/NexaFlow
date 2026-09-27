package com.nexaflow.core.agentrelay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRelaySignerTest {

    @Test
    fun signThenVerifyRoundTrip() {
        val key = AgentRelaySigner.newLinkKey()
        val canonical = "nexaflow-relay-v1\ndevice-1".toByteArray(Charsets.UTF_8)

        val signature = AgentRelaySigner.sign(key, canonical)

        assertTrue(AgentRelaySigner.verify(key, canonical, signature))
    }

    @Test
    fun tamperedPayloadFailsVerification() {
        val key = AgentRelaySigner.newLinkKey()
        val canonical = "nexaflow-relay-v1\ndevice-1".toByteArray(Charsets.UTF_8)
        val signature = AgentRelaySigner.sign(key, canonical)

        assertFalse(
            AgentRelaySigner.verify(
                key,
                "nexaflow-relay-v1\ndevice-2".toByteArray(Charsets.UTF_8),
                signature
            )
        )
    }

    @Test
    fun wrongKeyFailsVerification() {
        val canonical = "payload".toByteArray(Charsets.UTF_8)
        val signature = AgentRelaySigner.sign(AgentRelaySigner.newLinkKey(), canonical)

        assertFalse(AgentRelaySigner.verify(AgentRelaySigner.newLinkKey(), canonical, signature))
    }

    @Test
    fun malformedSignatureFailsClosed() {
        val key = AgentRelaySigner.newLinkKey()

        assertFalse(AgentRelaySigner.verify(key, "x".toByteArray(Charsets.UTF_8), "!!!"))
        assertFalse(AgentRelaySigner.verify(key, "x".toByteArray(Charsets.UTF_8), ""))
    }

    @Test
    fun keyEncodingRoundTrip() {
        val key = AgentRelaySigner.newLinkKey()

        assertTrue(AgentRelaySigner.decodeKey(AgentRelaySigner.encodeKey(key))!!.contentEquals(key))
    }

    @Test
    fun shortKeysAreRejected() {
        assertTrue(AgentRelaySigner.decodeKey(AgentRelaySigner.encodeKey(ByteArray(8))) == null)
    }
}
