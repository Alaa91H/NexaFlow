package com.nexaflow.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val secretKeyProvider: (() -> SecretKey)? = null
) : SecureStorage {

    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val keyLock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    override suspend fun get(key: String): String? = when (val result = read(key)) {
        is SecureStorageReadResult.Stored -> result.value
        SecureStorageReadResult.Missing, SecureStorageReadResult.Unreadable -> null
    }

    override suspend fun read(key: String): SecureStorageReadResult = withContext(ioDispatcher) {
        val encoded = prefs.getString(key, null) ?: return@withContext SecureStorageReadResult.Missing
        try {
            val secretKey = getOrCreateKey()
            val value = if (AesGcmEnvelopeCodec.isV2(encoded)) {
                AesGcmEnvelopeCodec.decrypt(secretKey, key, encoded)
            } else {
                val plaintext = AesGcmEnvelopeCodec.decryptLegacy(secretKey, encoded)
                prefs.edit()
                    .putString(key, AesGcmEnvelopeCodec.encrypt(secretKey, key, plaintext))
                    .apply()
                plaintext
            }
            SecureStorageReadResult.Stored(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w(TAG, "Encrypted secure-storage entry is unreadable; re-enter the affected secret")
            SecureStorageReadResult.Unreadable
        }
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

            secretKeyProvider?.invoke()?.let {
                cachedKey = it
                return it
            }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(keyAlias, null) as? SecretKey)?.let {
                cachedKey = it
                return it
            }

            if (prefs.all.isNotEmpty()) {
                Log.w(TAG, "Keystore key is missing while encrypted entries exist; secrets may need re-entry")
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
        private const val TAG = "KeystoreSecureStorage"
        private const val PREFS_NAME = "nexaflow_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
