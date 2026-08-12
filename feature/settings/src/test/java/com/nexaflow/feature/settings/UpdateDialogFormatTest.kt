package com.nexaflow.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Pure formatting logic of the update dialog. */
@RunWith(JUnit4::class)
class UpdateDialogFormatTest {

    @Test
    fun formatBytes_handlesBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun formatBytes_handlesKb() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("64.0 KB", formatBytes(64 * 1024))
    }

    @Test
    fun formatBytes_handlesMb() {
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("3.7 MB", formatBytes(3_880_000))
    }

    @Test
    fun formatBytes_handlesGb() {
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }
}
