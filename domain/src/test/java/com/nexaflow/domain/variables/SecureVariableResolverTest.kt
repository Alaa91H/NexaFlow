package com.nexaflow.domain.variables

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SecureVariableResolverTest {

    private val fakeStore = object : SecretStore {
        private val secrets = mutableMapOf("api_key" to "s3cr3t", "token" to "t0k%n")
        override suspend fun store(referenceKey: String, value: String) { secrets[referenceKey] = value }
        override suspend fun resolve(referenceKey: String): String? = secrets[referenceKey]
        override suspend fun delete(referenceKey: String) { secrets.remove(referenceKey) }
    }

    @Test
    fun `resolves standard variables and secrets together`() = runBlocking {
        val resolver = SecureVariableResolver(fakeStore)
        
        val text = "Auth: %auth_type, Key: %api_key"
        val standardVars = mapOf("auth_type" to "Bearer")
        val secretVars = mapOf("api_key" to SecretReference("api_key"))

        val result = resolver.resolveWithSecrets(text, standardVars, secretVars)

        assertEquals("Auth: Bearer, Key: s3cr3t", result)
    }

    @Test
    fun `escapes percent signs in secrets`() = runBlocking {
        val resolver = SecureVariableResolver(fakeStore)
        
        val text = "Request: %token"
        val secretVars = mapOf("token" to SecretReference("token"))

        val result = resolver.resolveWithSecrets(text, emptyMap(), secretVars)

        // The secret is "t0k%n", it should not be treated as a placeholder
        assertEquals("Request: t0k%n", result)
    }
}
