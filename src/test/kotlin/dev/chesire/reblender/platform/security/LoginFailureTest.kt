package dev.chesire.reblender.platform.security

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginFailureTest {
    private fun wrapped(cause: Throwable) = OAuth2AuthenticationException(OAuth2Error("invalid_user_info_response"), "failed", cause)

    private fun status(code: HttpStatus) = HttpClientErrorException.create(code, "", HttpHeaders.EMPTY, ByteArray(0), Charsets.UTF_8)

    private fun oauthError(code: String) = OAuth2AuthenticationException(OAuth2Error(code))

    @Test
    fun `a 403 from Spotify's profile request means the person isn't on the allowlist`() {
        assertEquals(LoginFailure.NOT_ALLOWED, LoginFailure.reasonFor(wrapped(status(HttpStatus.FORBIDDEN))))
    }

    @Test
    fun `pressing cancel on Spotify is reported as cancelled`() {
        assertEquals(LoginFailure.CANCELLED, LoginFailure.reasonFor(oauthError("access_denied")))
    }

    @Test
    fun `anything else is a generic failure`() {
        assertEquals(LoginFailure.FAILED, LoginFailure.reasonFor(wrapped(status(HttpStatus.INTERNAL_SERVER_ERROR))))
        assertEquals(LoginFailure.FAILED, LoginFailure.reasonFor(oauthError("authorization_request_not_found")))
    }
}
