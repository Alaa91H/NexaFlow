package com.nexaflow.core.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Versioned AES-GCM envelope used by [KeystoreSecureStorage].
 *
 * V2 authenticates the logical storage key as AAD, preventing a valid
 * ciphertext from being moved to a different secret slot.
 */
internal object AesGcmEnvelopeCodec {
    const val PREFIX = "v2:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12
    private const val AAD_PREFIX = "nexaflow-secure-store:"

    fun isV2(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(key: SecretKey, storageKey: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad(storageKey))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return PREFIX + Base64.getEncoder().withoutPadding().encodeToString(payload)
    }

    fun decrypt(key: SecretKey, storageKey: String, encoded: String): String {
        require(isV2(encoded)) { "Unsupported secure-storage envelope" }
        val payload = Base64.getDecoder().decode(encoded.removePrefix(PREFIX))
        require(payload.size > IV_LENGTH) { "Secure-storage envelope is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, IV_LENGTH))
        )
        cipher.updateAAD(aad(storageKey))
        val plain = cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
        return String(plain, Charsets.UTF_8)
    }

    /** Reads the pre-V2 base64(iv || ciphertext) format for lazy migration. */
    fun decryptLegacy(key: SecretKey, encoded: String): String {
        val payload = Base64.getDecoder().decode(encoded)
        require(payload.size > IV_LENGTH) { "Legacy secure-storage value is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, IV_LENGTH))
        )
        val plain = cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
        return String(plain, Charsets.UTF_8)
    }

    private fun aad(storageKey: String): ByteArray =
        "$AAD_PREFIX$storageKey".toByteArray(Charsets.UTF_8)
}
