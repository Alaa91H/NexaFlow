package com.nexaflow.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeystoreSecureStorageTest {
    @Test
    fun readDistinguishesMissingStoredAndUnreadableValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = KeystoreSecureStorage(context, secretKeyProvider = { key })

        assertEquals(SecureStorageReadResult.Missing, storage.read("api-key"))

        storage.put("api-key", "secret")
        assertEquals(SecureStorageReadResult.Stored("secret"), storage.read("api-key"))

        context.getSharedPreferences("nexaflow_secure", Context.MODE_PRIVATE)
            .edit()
            .putString("api-key", "corrupted")
            .commit()
        assertEquals(SecureStorageReadResult.Unreadable, storage.read("api-key"))
    }
}
