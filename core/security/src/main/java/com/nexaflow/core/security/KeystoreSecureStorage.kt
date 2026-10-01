package com.nexaflow.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SecureStorage] backed by Android Keystore AES-256-GCM.
 *
 * Values are written as a V2 envelope with the logical storage key authenticated
 * as AAD. Pre-V2 values are decrypted once and lazily re-encrypted without
 * requiring a destructive migration.
 */
class KeystoreSecureStorage(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SecureStorage {

    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val keyLock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    override suspend fun get(key: String): String? = withContext(ioDispatcher) {
        val encoded = prefs.getString(key, null) ?: return@withContext null
        runCatching {
            val secretKey = getOrCreateKey()
            if (AesGcmEnvelopeCodec.isV2(encoded)) {
                AesGcmEnvelopeCodec.decrypt(secretKey, key, encoded)
            } else {
                val plaintext = AesGcmEnvelopeCodec.decryptLegacy(secretKey, encoded)
                prefs.edit()
                    .putString(key, AesGcmEnvelopeCodec.encrypt(secretKey, key, plaintext))
                    .apply()
                plaintext
            }
        }.getOrNull()
    }

    override suspend fun put(key: String, value: String): Unit = withContext(ioDispatcher) {
        val encoded = AesGcmEnvelopeCodec.encrypt(getOrCreateKey(), key, value)
        prefs.edit().putString(key, encoded).apply()
    }

    override suspend fun remove(key: String): Unit = withContext(ioDispatcher) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear(): Unit = withContext(ioDispatcher) {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(keyLock) {
            cachedKey?.let { return it }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(keyAlias, null) as? SecretKey)?.let {
                cachedKey = it
                return it
            }

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
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
            return generator.generateKey().also { cachedKey = it }
        }
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "nexaflow_secure_store"
        private const val PREFS_NAME = "nexaflow_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
