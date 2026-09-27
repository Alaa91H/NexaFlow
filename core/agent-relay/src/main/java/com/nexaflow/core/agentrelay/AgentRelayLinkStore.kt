package com.nexaflow.core.agentrelay

import com.nexaflow.core.security.SecureStorage

/**
 * Provisioning for the relay link key.
 *
 * Presence of a key means Remote Access is provisioned; deleting it
 * unprovisions the device without touching agent grants or automations.
 * The raw key only ever lives in [SecureStorage] and process memory.
 */
class AgentRelayLinkStore(
    private val secureStorage: SecureStorage
) {
    suspend fun isProvisioned(): Boolean = linkKey() != null

    suspend fun linkKey(): ByteArray? {
        val encoded = secureStorage.get(LINK_KEY) ?: return null
        return AgentRelaySigner.decodeKey(encoded)
    }

    suspend fun provision(): ByteArray {
        val key = AgentRelaySigner.newLinkKey()
        secureStorage.put(LINK_KEY, AgentRelaySigner.encodeKey(key))
        return key
    }

    suspend fun rotate(): ByteArray = provision()

    /** Stores caller-provided key material (e.g. restored from backup). */
    suspend fun import(encoded: String): Boolean {
        val key = AgentRelaySigner.decodeKey(encoded) ?: return false
        secureStorage.put(LINK_KEY, AgentRelaySigner.encodeKey(key))
        return true
    }

    suspend fun deprovision() {
        secureStorage.remove(LINK_KEY)
    }

    private companion object {
        const val LINK_KEY = "agent_relay_link_key"
    }
}
