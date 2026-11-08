package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.playlists.models.BlendOutcome
import dev.chesire.reblender.playlists.models.DeleteOutcome
import org.springframework.web.client.RestClientResponseException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The wording shown to people after playlist actions. */
object PlaylistMessages {
    private val UPDATED_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private const val SPOTIFY_FORBIDDEN =
        "Spotify refused the request (403). Check everyone in the blend is on the app's allowlist in the Spotify dashboard."

    fun updated(at: Instant): String = UPDATED_FORMAT.format(at)

    fun created(
        outcome: BlendOutcome,
        nameOf: (String) -> String,
    ): String = "Created \"${outcome.blend.playlistName}\" with ${outcome.blend.trackCount} songs." + shortNote(outcome, nameOf)

    fun refreshed(
        outcome: BlendOutcome,
        nameOf: (String) -> String,
    ): String = "Updated \"${outcome.blend.playlistName}\" with ${outcome.blend.trackCount} songs." + shortNote(outcome, nameOf)

    fun deleted(
        outcome: DeleteOutcome,
        removeFromSpotify: Boolean,
    ): String {
        val name = "\"${outcome.playlistName}\""
        return when {
            !removeFromSpotify -> {
                "Stopped managing $name. It's still in your Spotify library."
            }

            outcome.removedFromSpotify -> {
                "Deleted $name from the app and your Spotify library."
            }

            else -> {
                "Removed $name from the app, but Spotify wouldn't let the app delete it " +
                    "(error ${outcome.spotifyStatus}). Delete it in Spotify yourself."
            }
        }
    }

    fun loginExpired(
        isMe: Boolean,
        name: String,
    ): String =
        if (isMe) {
            "Your Spotify login has expired. Log out and log in again."
        } else {
            "$name needs to log in to the app again (Spotify logins expire after 6 months)."
        }

    fun spotifyError(e: RestClientResponseException): String =
        when (e.statusCode.value()) {
            403 -> SPOTIFY_FORBIDDEN
            404 -> "Spotify couldn't find that playlist. It may have been deleted."
            429 -> "Spotify is rate limiting the app. Try again in a minute."
            else -> "Spotify returned an error (${e.statusCode.value()})."
        }

    /** Explains a playlist that came out shorter than requested, e.g. "Spotify only had 48 usable top tracks for you". */
    private fun shortNote(
        outcome: BlendOutcome,
        nameOf: (String) -> String,
    ): String {
        val blend = outcome.blend
        if (blend.trackCount >= blend.size) return ""
        val counts = outcome.usableTracks.entries.joinToString(", ") { (id, count) -> "${nameOf(id)}: $count" }
        val why =
            if (blend.memberIds.size == 1) {
                "Spotify only had that many usable top tracks for you"
            } else {
                "not enough usable top tracks to fill an even split"
            }
        return " That's fewer than the ${blend.size} you asked for: $why (usable top tracks: $counts)."
    }
}
