package dev.chesire.reblender.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs every request the app receives and what it answered:
 * `IN  POST /playlists -> 302 (412 ms) user=abc123`
 *
 * Runs before Spring Security so redirects and refusals from the security chain are logged too.
 * Only the method, path, allowlisted query values ([LogRedaction]), status, duration and Spotify user ID are logged:
 * never headers (cookies, CSRF tokens), request bodies (form fields include the CSRF token) or OAuth parameters.
 */
class IncomingRequestLogFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger("reblender.http.in")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val start = System.nanoTime()
        val target = LogRedaction.target(request.requestURI, request.queryString)
        try {
            chain.doFilter(request, response)
            log.info("IN  {} {} -> {} ({} ms){}", request.method, target, response.status, elapsedMs(start), user(request))
        } catch (e: Exception) {
            log.warn("IN  {} {} -> failed: {} ({} ms){}", request.method, target, e.javaClass.simpleName, elapsedMs(start), user(request))
            throw e
        }
    }

    // Async/error dispatches would log the same request twice.
    override fun shouldNotFilterErrorDispatch(): Boolean = true

    /** The logged-in Spotify user, read from the session after the request (the security context is cleared by then). */
    private fun user(request: HttpServletRequest): String {
        val context = runCatching {
            request.getSession(false)
                ?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) as? SecurityContext
        }.getOrNull() // the session may have just been invalidated (logout)
        return context?.authentication?.name?.let { " user=$it" } ?: ""
    }

    private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000
}

@Configuration
class IncomingRequestLogConfig {
    @Bean
    fun incomingRequestLogFilter(): FilterRegistrationBean<IncomingRequestLogFilter> =
        FilterRegistrationBean(IncomingRequestLogFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE + 10
            addUrlPatterns("/*")
        }
}
