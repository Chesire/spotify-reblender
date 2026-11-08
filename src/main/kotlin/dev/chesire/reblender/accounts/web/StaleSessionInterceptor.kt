package dev.chesire.reblender.accounts.web

import dev.chesire.reblender.accounts.AccountService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * A logged-in session is only honoured while the app still holds that user's record and stored Spotify login.
 * If either is gone ("Forget me" from another browser, tokens deleted, database reset), the session is ended
 * instead of silently recreating the user from whatever the old session remembers.
 */
@Component
class StaleSessionInterceptor(
    private val accounts: AccountService,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return true
        val name = auth.name
        if (accounts.find(name) != null && accounts.hasStoredLogin(name)) return true

        log.info("Ending a session for {}: their user record or stored Spotify login is gone", name)
        SecurityContextLogoutHandler().logout(request, response, auth)
        if (request.requestURI.startsWith(request.contextPath + "/api/")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value())
        } else {
            response.sendRedirect(request.contextPath + "/?expired")
        }
        return false
    }
}

@Configuration
class StaleSessionWebConfig(
    private val interceptor: StaleSessionInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).excludePathPatterns("/css/**", "/js/**", "/error")
    }
}
