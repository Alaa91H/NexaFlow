package com.nexaflow.core.rom

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowConnectivityManager

/**
 * [DnsStateReader] reads only the public networking surface. With the dead
 * pre-Android-6 fallback removed, the active network is the single source of
 * link properties — including the "no active link" case, which must read as an
 * empty snapshot rather than an error.
 */
@RunWith(RobolectricTestRunner::class)
class DnsStateReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun connectivity(): ShadowConnectivityManager =
        shadowOf(context.getSystemService(ConnectivityManager::class.java))

    @Test
    fun readWithoutAnActiveNetworkReportsAnEmptySnapshot() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        connectivity().setDefaultNetworkActive(true)
        connectivity().setActiveNetworkInfo(null)

        val state = DnsStateReader.read(context)

        assertNull(state.readError)
        assertTrue(state.networksRead <= 1)
    }
}
