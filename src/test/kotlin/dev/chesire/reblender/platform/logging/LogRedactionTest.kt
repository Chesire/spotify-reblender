package dev.chesire.reblender.platform.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogRedactionTest {
    @Test
    fun `oauth callback parameters are masked`() {
        assertEquals(
            "/login/oauth2/code/spotify?code=***&state=***",
            LogRedaction.target("/login/oauth2/code/spotify", "code=AQBsecretcode&state=abc%3D"),
        )
    }

    @Test
    fun `unknown parameters are masked, known safe ones kept`() {
        assertEquals(
            "time_range=short_term&limit=50&access_token=***&something_new=***&flag",
            LogRedaction.query("time_range=short_term&limit=50&access_token=xyz&something_new=1&flag"),
        )
        assertNull(LogRedaction.query(null))
        assertEquals("/", LogRedaction.target("/", ""))
    }

    @Test
    fun `token-like values in error bodies are masked`() {
        val body = """{"error":"invalid_grant","access_token":"BQDsecret","refresh_token" : "AQCsecret", "note":"Bearer abc.def"}"""
        val out = LogRedaction.errorBody(body)
        assertFalse(out.contains("BQDsecret"))
        assertFalse(out.contains("AQCsecret"))
        assertFalse(out.contains("abc.def"))
        assertTrue(out.contains("invalid_grant"))
    }

    @Test
    fun `long error bodies are shortened to one line`() {
        val out = LogRedaction.errorBody("x".repeat(2000) + "\n" + "y")
        assertTrue(out.length <= 501)
        assertFalse(out.contains("\n"))
    }
}
