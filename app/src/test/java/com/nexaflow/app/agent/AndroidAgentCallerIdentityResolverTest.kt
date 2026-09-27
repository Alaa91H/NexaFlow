package com.nexaflow.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidAgentCallerIdentityResolverTest {

    @Test
    fun signerFingerprintIsStableAcrossSignerOrder() {
        val first = byteArrayOf(1, 2, 3, 4)
        val second = byteArrayOf(5, 6, 7, 8)

        assertEquals(
            AndroidAgentCallerIdentityResolver.fingerprint(
                listOf(first, second)
            ),
            AndroidAgentCallerIdentityResolver.fingerprint(
                listOf(second, first)
            )
        )
    }

    @Test
    fun signerFingerprintChangesWhenCertificateChanges() {
        val first = byteArrayOf(1, 2, 3, 4)
        val changed = byteArrayOf(1, 2, 3, 5)

        assertNotEquals(
            AndroidAgentCallerIdentityResolver.fingerprint(listOf(first)),
            AndroidAgentCallerIdentityResolver.fingerprint(listOf(changed))
        )
    }
}
