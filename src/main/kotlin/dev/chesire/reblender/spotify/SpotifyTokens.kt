package dev.chesire.reblender.spotify

import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.stereotype.Component

/** Thrown when a user has no usable token (never logged in, revoked, or the 6-month refresh token expired). */
class ReloginRequiredException(val spotifyId: String) :
    RuntimeException("Spotify user $spotifyId needs to log in again")

@Component
class SpotifyTokens(
    private val clientManager: OAuth2AuthorizedClientManager,
) {

    /** A valid access token for [spotifyId], refreshed if needed. */
    fun accessTokenFor(spotifyId: String): String {
        val request = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
            .principal(spotifyId)
            .build()
        val client = try {
            clientManager.authorize(request)
        } catch (e: ClientAuthorizationException) {
            // e.g. invalid_grant: the refresh token has expired. The stored client is removed by Spring.
            throw ReloginRequiredException(spotifyId)
        }
        return client?.accessToken?.tokenValue ?: throw ReloginRequiredException(spotifyId)
    }

    companion object {
        const val REGISTRATION_ID = "spotify"
    }
}
