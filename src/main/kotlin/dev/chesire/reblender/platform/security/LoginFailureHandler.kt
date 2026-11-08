package dev.chesire.reblender.platform.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

/** Why a Spotify login failed, as shown to the person. [code] goes in the `login-error` query parameter. */
enum class LoginFailure(
    val code: String,
) {
    /** Spotify refused the profile request: in Development Mode, everyone must be on the app's allowlist. */
    NOT_ALLOWED("not-allowed"),

    /** The person pressed Cancel on Spotify's approval page. */
    CANCELLED("cancelled"),
    FAILED("failed"),
    ;

    companion object {
        fun reasonFor(exception: Throwable): LoginFailure {
            if ((exception as? OAuth2AuthenticationException)?.error?.errorCode == "access_denied") return CANCELLED
            val forbidden = generateSequence(exception) { it.cause }
                .any { it is HttpStatusCodeException && it.statusCode.value() == 403 }
            return if (forbidden) NOT_ALLOWED else FAILED
        }
    }
}

/** Sends a failed login back to the home page with a reason it can explain, instead of Spring's bare `?error`. */
@Component
class LoginFailureHandler : AuthenticationFailureHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val reason = LoginFailure.reasonFor(exception)
        // Only the OAuth error code: descriptions can echo request details.
        log.warn(
            "Spotify login failed ({}): {}",
            reason.code,
            (exception as? OAuth2AuthenticationException)?.error?.errorCode ?: exception.javaClass.simpleName,
        )
        response.sendRedirect(request.contextPath + "/?login-error=" + reason.code)
    }
}
