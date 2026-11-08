package dev.chesire.reblender.playlists.models

/**
 * Result of deleting a blend. [spotifyStatus] is null when Spotify wasn't asked (or succeeded), otherwise
 * the HTTP status Spotify refused with; [removedFromSpotify] says whether it's gone from the library.
 */
data class DeleteOutcome(
    val playlistName: String,
    val removedFromSpotify: Boolean,
    val spotifyStatus: Int?,
)
