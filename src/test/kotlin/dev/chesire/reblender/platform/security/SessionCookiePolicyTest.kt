package dev.chesire.reblender.platform.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCookiePolicyTest {
    @Test
    fun `secure follows the base url scheme by default`() {
        assertTrue(SessionCookiePolicy.secure(null, "https://reblender.example.com"))
        assertTrue(SessionCookiePolicy.secure("", "HTTPS://reblender.example.com"))
        assertFalse(SessionCookiePolicy.secure(null, "http://127.0.0.1:8080"))
        assertFalse(SessionCookiePolicy.secure(null, null))
    }

    @Test
    fun `an explicit override wins`() {
        assertTrue(SessionCookiePolicy.secure("true", "http://127.0.0.1:8080"))
        assertFalse(SessionCookiePolicy.secure(" FALSE ", "https://reblender.example.com"))
    }
}
