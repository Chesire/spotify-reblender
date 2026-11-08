package dev.chesire.reblender.platform.security

import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * The session cookie is marked Secure whenever the app's public address is HTTPS, so a deployment
 * behind TLS can't accidentally send the session over plain HTTP. SESSION_COOKIE_SECURE=true/false overrides it.
 */
@Configuration
class SessionCookieConfig {
    @Bean
    fun secureSessionCookie(env: Environment): ServletContextInitializer {
        val baseUrl = env.getProperty("APP_BASE_URL")
        val secure = SessionCookiePolicy.secure(env.getProperty("SESSION_COOKIE_SECURE"), baseUrl)
        if (!secure && SessionCookiePolicy.isHttps(baseUrl)) {
            log.warn("APP_BASE_URL is https but SESSION_COOKIE_SECURE=false: the session cookie will be sent over plain HTTP too.")
        }
        log.info("Session cookie Secure flag: {}", secure)
        return ServletContextInitializer { context -> context.sessionCookieConfig.isSecure = secure }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SessionCookieConfig::class.java)
    }
}

object SessionCookiePolicy {
    /** An explicit "true"/"false" override wins; otherwise Secure follows the scheme of the public base URL. */
    fun secure(
        override: String?,
        baseUrl: String?,
    ): Boolean =
        when (override?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> isHttps(baseUrl)
        }

    fun isHttps(baseUrl: String?): Boolean = baseUrl?.trim()?.startsWith("https://", ignoreCase = true) == true
}
