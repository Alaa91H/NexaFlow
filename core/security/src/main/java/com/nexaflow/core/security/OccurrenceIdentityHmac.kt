package com.nexaflow.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Keyed digest boundary so durable temporal history never stores a raw source event ID. */
fun interface OccurrenceIdentityHmac {
    fun digest(sourceId: String, stableEventId: String): String
}

class AndroidKeystoreOccurrenceIdentityHmac(
    private val alias: String = DEFAULT_ALIAS,
    private val keyProvider: (() -> SecretKey)? = null
) : OccurrenceIdentityHmac {
    @Volatile private var cachedKey: SecretKey? = null

    override fun digest(sourceId: String, stableEventId: String): String {
        require(sourceId.isNotBlank() && stableEventId.isNotBlank())
        val bytes = Mac.getInstance("HmacSHA256").run {
            init(getOrCreateKey())
            doFinal("$sourceId\u0000$stableEventId".toByteArray(Charsets.UTF_8))
        }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        keyProvider?.invoke()?.let { return it.also { key -> cachedKey = key } }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it.also { key -> cachedKey = key } }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey().also { cachedKey = it }
    }

    companion object { const val DEFAULT_ALIAS = "nexaflow_trigger_occurrence_hmac" }
}
