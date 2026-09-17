package com.nexaflow.domain.variables

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for `%NAME` placeholder resolution: single-pass expansion,
 * unknown-placeholder preservation, `%%` escaping, placeholder discovery, and
 * the async secret-substitution path of [SecureVariableResolver].
 */
class VariableResolverSecretsTest {

    @Test
    fun `known placeholders are substituted`() {
        assertEquals(
            "Hello World",
            VariableResolver.resolve("Hello %NAME", mapOf("NAME" to "World"))
        )
    }

    @Test
    fun `unknown placeholders are preserved verbatim`() {
        assertEquals(
            "Hi %UNKNOWN",
            VariableResolver.resolve("Hi %UNKNOWN", mapOf("NAME" to "World"))
        )
    }

    @Test
    fun `escaped percent survives resolution`() {
        assertEquals(
            "100% done",
            VariableResolver.resolve("100%% done", mapOf("NAME" to "World"))
        )
    }

    @Test
    fun `values containing percent are never re-parsed`() {
        assertEquals(
            "got %NAME and %OTHER",
            VariableResolver.resolve("got %NAME", mapOf("NAME" to "%NAME and %OTHER"))
        )
    }

    @Test
    fun `referenced placeholders are listed in first-appearance order`() {
        assertEquals(
            listOf("A", "B", "C"),
            VariableResolver.referencedPlaceholders("%A %B %A %C")
        )
    }

    @Test
    fun `text without percent yields no references`() {
        assertTrue(VariableResolver.referencedPlaceholders("no placeholders here").isEmpty())
    }

    @Test
    fun `secrets are substituted through the secure resolver`() = runBlocking {
        val store = object : SecretStore {
            override suspend fun store(referenceKey: String, value: String) = Unit
            override suspend fun resolve(referenceKey: String): String? =
                if (referenceKey == "kw-1") "s3cret" else null
            override suspend fun delete(referenceKey: String) = Unit
        }
        val resolver = SecureVariableResolver(store)
        val out = resolver.resolveWithSecrets(
            text = "token=%api_key user=%user",
            standardVariables = mapOf("user" to "alice"),
            secretVariables = mapOf("api_key" to SecretReference("kw-1"))
        )
        assertEquals("token=s3cret user=alice", out)
    }

    @Test
    fun `missing secret leaves its placeholder intact`() = runBlocking {
        val store = object : SecretStore {
            override suspend fun store(referenceKey: String, value: String) = Unit
            override suspend fun resolve(referenceKey: String): String? = null
            override suspend fun delete(referenceKey: String) = Unit
        }
        val resolver = SecureVariableResolver(store)
        val out = resolver.resolveWithSecrets(
            text = "token=%api_key",
            standardVariables = emptyMap(),
            secretVariables = mapOf("api_key" to SecretReference("missing"))
        )
        assertEquals("token=%api_key", out)
    }
}
