package com.nexaflow.app

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract tests for the `nexaflow://run-task/{id}[?force=1]` deep-link
 * parser that drives MainActivity's manual admission vs. force-run routing.
 */
@RunWith(RobolectricTestRunner::class)
class RunTaskDeepLinkTest {

    @Test
    fun `plain run-task link parses without force`() {
        val link = parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc-123"))
        assertEquals("abc-123", link?.automationId)
        assertFalse(link?.force == true)
    }

    @Test
    fun `force=1 query parameter requests the force path`() {
        val link = parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc-123?force=1"))
        assertTrue(link?.force == true)
        assertEquals("abc-123", link?.automationId)
    }

    @Test
    fun `force values other than 1 do not request the force path`() {
        assertFalse(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/a?force=0"))?.force == true)
        assertFalse(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/a?force=true"))?.force == true)
    }

    @Test
    fun `trailing slash on the path is tolerated`() {
        assertEquals("abc", parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc/"))?.automationId)
    }

    @Test
    fun `foreign schemes and hosts are ignored`() {
        assertNull(parseRunTaskDeepLink(Uri.parse("https://run-task/abc")))
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://other-host/abc")))
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc?force=1")!!.buildUpon().scheme("http").build()))
    }

    @Test
    fun `blank or missing ids are ignored`() {
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/")))
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task")))
        assertNull(parseRunTaskDeepLink(null))
    }

    // ---- P0.2: token parameter + authorization contract ----

    @Test
    fun `token query parameter is captured`() {
        val link = parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc?token=abcDEF123-_"))
        assertEquals("abcDEF123-_", link?.token)
    }

    @Test
    fun `blank token is normalized to null`() {
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc?token="))?.token)
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc?token=%20%20"))?.token)
    }

    @Test
    fun `missing token parameter yields null token`() {
        assertNull(parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/abc"))?.token)
    }

    @Test
    fun `authorization fails closed when stored token is absent`() {
        assertFalse(deepLinkTokenAuthorized("anything", null))
        assertFalse(deepLinkTokenAuthorized("anything", ""))
    }

    @Test
    fun `authorization fails closed when presented token is absent`() {
        assertFalse(deepLinkTokenAuthorized(null, "stored-token"))
        assertFalse(deepLinkTokenAuthorized("", "stored-token"))
    }

    @Test
    fun `matching token authorizes and mismatch does not`() {
        assertTrue(deepLinkTokenAuthorized("s3cret-token", "s3cret-token"))
        assertFalse(deepLinkTokenAuthorized("s3cret-token", "other-token"))
        // Case matters: base64url alphabets are case-sensitive.
        assertFalse(deepLinkTokenAuthorized("S3CRET-TOKEN", "s3cret-token"))
    }

    @Test
    fun `ids that look like traversal or huge payloads are still just ids`() {
        // The parser keeps the raw path; authorization and routing treat an
        // unknown id as "no such task" — nothing executes for a wrong id.
        val link = parseRunTaskDeepLink(Uri.parse("nexaflow://run-task/../../etc"))
        assertEquals("../../etc", link?.automationId)
    }
}
