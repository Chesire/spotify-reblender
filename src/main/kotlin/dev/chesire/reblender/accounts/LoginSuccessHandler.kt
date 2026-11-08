package dev.chesire.reblender.accounts

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Records every Spotify user who logs in, so they show up in the "pick partner" list.
 * The OAuth2 tokens themselves are persisted by the JDBC authorized-client service.
 */
@Component
class LoginSuccessHandler(
    private val accounts: AccountService,
) : SimpleUrlAuthenticationSuccessHandler("/") {
    init {
        setAlwaysUseDefaultTargetUrl(true)
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        accounts.recordLogin(SpotifyProfile.toAppUser(authentication.name, authentication.principal as? OAuth2User))
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
