package dev.chesire.reblender.playlists

import dev.chesire.reblender.accounts.AccountForgotten
import dev.chesire.reblender.playlists.data.BlendRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Deletes every blend a forgotten user was in. Runs inside the "forget" transaction. The Spotify playlists are left alone. */
@Component
class ForgottenAccountCleanup(
    private val blends: BlendRepository,
) {
    @EventListener
    fun on(event: AccountForgotten) {
        blends.deleteInvolving(event.spotifyId)
    }
}
