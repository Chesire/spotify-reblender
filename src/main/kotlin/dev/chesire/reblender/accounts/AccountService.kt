package dev.chesire.reblender.accounts

import dev.chesire.reblender.accounts.data.UserEntity
import dev.chesire.reblender.accounts.data.UserRepository
import dev.chesire.reblender.accounts.data.toModel
import dev.chesire.reblender.accounts.models.AppUser
import dev.chesire.reblender.integrations.spotify.SpotifyTokens
import dev.chesire.reblender.platform.security.SafeUrls
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AccountService(
    private val users: UserRepository,
    private val authorizedClients: OAuth2AuthorizedClientService,
    private val events: ApplicationEventPublisher,
) {
    fun find(spotifyId: String): AppUser? = users.findByIdOrNull(spotifyId)?.toModel()

    /** Everyone who has logged in, by name. */
    fun all(): List<AppUser> = users.findAllByOrderByDisplayNameAsc().map { it.toModel() }

    /** People [spotifyId] may include in a blend: everyone else who allows it. */
    fun partnersFor(spotifyId: String): List<AppUser> = all().filter { it.spotifyId != spotifyId && it.allowBlending }

    /** Records a login: creates the user, or refreshes their name and picture. Never changes their blending choice. */
    @Transactional
    fun recordLogin(profile: AppUser) {
        val now = Instant.now()
        val avatarUrl = SafeUrls.httpsOrNull(profile.avatarUrl)
        val existing = users.findByIdOrNull(profile.spotifyId)
        if (existing == null) {
            users.insert(UserEntity(profile.spotifyId, profile.displayName, avatarUrl, allowBlending = true, lastLoginAt = now))
        } else {
            users.save(existing.copy(displayName = profile.displayName, avatarUrl = avatarUrl, lastLoginAt = now))
        }
    }

    /** Whether other people may include [spotifyId] (and so read their top tracks) in blends. */
    @Transactional
    fun setAllowBlending(
        spotifyId: String,
        allow: Boolean,
    ) {
        users.findByIdOrNull(spotifyId)?.let { users.save(it.copy(allowBlending = allow)) }
    }

    /** Whether the app still holds a Spotify login for [spotifyId]. */
    fun hasStoredLogin(spotifyId: String): Boolean =
        authorizedClients.loadAuthorizedClient<OAuth2AuthorizedClient>(SpotifyTokens.REGISTRATION_ID, spotifyId) != null

    /** Deletes the user's stored Spotify tokens, their user record and (via [AccountForgotten]) everything else about them. */
    @Transactional
    fun forget(spotifyId: String) {
        authorizedClients.removeAuthorizedClient(SpotifyTokens.REGISTRATION_ID, spotifyId)
        events.publishEvent(AccountForgotten(spotifyId))
        users.deleteById(spotifyId)
    }
}
