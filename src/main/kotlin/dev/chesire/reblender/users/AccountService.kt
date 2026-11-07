package dev.chesire.reblender.users

import dev.chesire.reblender.playlists.BlendRepository
import dev.chesire.reblender.spotify.SpotifyTokens
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountService(
    private val authorizedClients: OAuth2AuthorizedClientService,
    private val users: UserRepository,
    private val blends: BlendRepository,
) {

    /** Deletes the user's stored Spotify tokens, their user record and every blend they're part of. */
    @Transactional
    fun forget(spotifyId: String) {
        authorizedClients.removeAuthorizedClient(SpotifyTokens.REGISTRATION_ID, spotifyId)
        blends.deleteInvolving(spotifyId)
        users.delete(spotifyId)
    }
}
