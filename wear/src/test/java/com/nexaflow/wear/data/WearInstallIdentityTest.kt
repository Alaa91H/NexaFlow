package com.nexaflow.wear.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearInstallIdentityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val cleared = context.getSharedPreferences(
            "nexaflow_wear_identity",
            Context.MODE_PRIVATE
        ).edit()
            .clear()
            .commit()
        assertTrue("test precondition: identity store must be cleared synchronously", cleared)
    }

    @Test
    fun `install identity survives store recreation`() {
        val first = WearInstallIdentity(context).getOrCreateInstallId()
        val second = WearInstallIdentity(context).getOrCreateInstallId()

        assertEquals(first, second)
        assertEquals(first, UUID.fromString(first).toString())
    }
}
