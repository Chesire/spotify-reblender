package dev.chesire.reblender.users

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler

/**
 * Records every Spotify user who logs in, so they show up in the "pick partner" list.
 * The OAuth2 tokens themselves are persisted by the JDBC authorized-client service.
 */
class LoginSuccessHandler(
    private val users: UserRepository,
) : SimpleUrlAuthenticationSuccessHandler("/") {
    init {
        setAlwaysUseDefaultTargetUrl(true)
    }

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        users.upsert(SpotifyProfile.toAppUser(authentication.name, authentication.principal as? OAuth2User))
        super.onAuthenticationSuccess(request, response, authentication)
    }
}
