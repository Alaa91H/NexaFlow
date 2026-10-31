package com.nexaflow.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderHttpFailureTest {

    @Test
    fun parsesOpenAiAndGeminiErrorEnvelopes() {
        val openAi = AiProviderHttpFailure.parse(
            """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error","code":"invalid_api_key"}}"""
        )
        assertEquals("invalid_api_key", openAi?.code)
        assertEquals("Incorrect API key provided", openAi?.message)

        val gemini = AiProviderHttpFailure.parse(
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        )
        assertEquals("INVALID_ARGUMENT", gemini?.code)
        assertTrue(gemini!!.message!!.startsWith("API key not valid"))

        val anthropic = AiProviderHttpFailure.parse(
            """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        )
        assertEquals("authentication_error", anthropic?.code)

        assertNull(AiProviderHttpFailure.parse(""))
        assertNull(AiProviderHttpFailure.parse("<html>bad gateway</html>"))
        assertNull(AiProviderHttpFailure.parse("""{"unrelated":true}"""))
    }

    @Test
    fun bodyMessageDecidesWhenStatusAloneIsMisleading() {
        // Gemini reports an invalid key as HTTP 400, not 401.
        assertEquals(
            AiConnectionFailure.AUTHENTICATION,
            AiProviderFailureClassifier.fromHttpStatus(
                400,
                "API key not valid. Please pass a valid API key."
            )
        )
        // A 404 that names a model is a model problem, not a missing endpoint.
        assertEquals(
            AiConnectionFailure.MODEL_NOT_FOUND,
            AiProviderFailureClassifier.fromHttpStatus(
                404,
                "models/gemini-9 does not exist or you do not have access"
            )
        )
        assertEquals(
            AiConnectionFailure.QUOTA_EXCEEDED,
            AiProviderFailureClassifier.fromHttpStatus(
                429,
                "You exceeded your current quota, please check your plan"
            )
        )
        assertEquals(
            AiConnectionFailure.RATE_LIMITED,
            AiProviderFailureClassifier.fromHttpStatus(429, "Too many requests")
        )
        assertEquals(
            AiConnectionFailure.INVALID_REQUEST,
            AiProviderFailureClassifier.fromHttpStatus(400, "Request contains an invalid argument")
        )
        // Status-only behaviour is unchanged for callers without a body.
        assertEquals(
            AiConnectionFailure.ENDPOINT_NOT_FOUND,
            AiProviderFailureClassifier.fromHttpStatus(404)
        )
    }

    @Test
    fun failureCarriesBoundedRedactedDetails() {
        val failure = AiProviderHttpFailure.of(
            status = 401,
            body = """{"error":{"message":"Bad key sk-abcdef1234567890 for x-api-key","code":"bad \"code\" !!"}}""",
            retryAfterMs = 1_500L
        )

        assertEquals(AiConnectionFailure.AUTHENTICATION, failure.failure)
        assertEquals(401, failure.httpStatus)
        assertEquals(1_500L, failure.retryAfterMs)
        assertEquals("bad_code", failure.providerCode)
        val message = requireNotNull(failure.providerMessage)
        assertTrue(message.contains("[REDACTED]"))
        assertTrue(message.length <= AiProviderRequestException.MAX_ERROR_MESSAGE_CHARS)
    }

    @Test
    fun retryAfterSupportsSecondsAndHttpDates() {
        val now = 1_700_000_000_000L
        assertEquals(5_000L, AiRetryAfter.parse("5", now))
        assertEquals(0L, AiRetryAfter.parse("0", now))
        assertNull(AiRetryAfter.parse(null, now))
        assertNull(AiRetryAfter.parse("-3", now))
        assertNull(AiRetryAfter.parse("not-a-date", now))

        val date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
            java.time.Instant.ofEpochMilli(now + 30_000L)
                .atZone(java.time.ZoneOffset.UTC)
        )
        assertEquals(30_000L, AiRetryAfter.parse(date, now))
        // A date in the past means "retry now", never a negative cooldown.
        assertEquals(
            0L,
            AiRetryAfter.parse(
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    java.time.Instant.ofEpochMilli(now - 30_000L).atZone(java.time.ZoneOffset.UTC)
                ),
                now
            )
        )
    }
}
