package dev.chesire.reblender.accounts

import dev.chesire.reblender.accounts.models.AppUser
import dev.chesire.reblender.integrations.spotify.SpotifyImages
import org.springframework.security.oauth2.core.user.OAuth2User

/** Turns the Spotify `/me` profile (the OAuth2 user attributes) into an [AppUser]. */
object SpotifyProfile {
    fun toAppUser(
        spotifyId: String,
        user: OAuth2User?,
    ): AppUser {
        val displayName = user?.getAttribute<String>("display_name")?.takeIf { it.isNotBlank() } ?: spotifyId
        return AppUser(spotifyId, displayName, avatarUrl(user?.attributes?.get("images")))
    }

    /**
     * Spotify gives `images: [{url, height, width}, ...]` in no guaranteed order.
     * Pick the smallest image that is at least 64px (it's shown small), else whatever exists.
     */
    internal fun avatarUrl(images: Any?): String? {
        val candidates = (images as? List<*>)
            .orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { image ->
                val url = image["url"] as? String ?: return@mapNotNull null
                url to (image["height"] as? Number)?.toInt()
            }
        return SpotifyImages.pick(candidates)
    }
}
