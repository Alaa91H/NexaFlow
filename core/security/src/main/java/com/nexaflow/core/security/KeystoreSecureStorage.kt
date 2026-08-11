package com.nexaflow.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecureStorage] backed by the Android Keystore (AES-256-GCM). Keys never
 * leave secure hardware where available, and the master key is created once
 * and reused. Values are stored as `base64(iv || ciphertext)`.
 *
 * Chosen over EncryptedSharedPreferences (deprecated in 2026 due to keyset
 * corruption on OEM devices): a single Keystore AES key avoids that class of
 * failure and is a small, dependency-free implementation.
 *
 * Note: Keystore keys do NOT survive app uninstall (backup restores lose the
 * key), so callers must treat this as session-scoped encryption — secrets are
 * re-encrypted under a fresh key on reinstall via the fallback path.
 */
class KeystoreSecureStorage(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : SecureStorage {

    // Lazily opened: Hilt services (e.g. NotificationListener) construct this
    // on the main thread at service creation, and touching disk there trips
    // the debug StrictMode watchdog (penaltyDeath) — an avoidable open-FC.
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull()
    }

    override suspend fun put(key: String, value: String) {
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    override suspend fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val ciphertext = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) })
            .getKey(keyAlias, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "nexaflow_secure_store"
        private const val PREFS_NAME = "nexaflow_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH = 12
    }
}
