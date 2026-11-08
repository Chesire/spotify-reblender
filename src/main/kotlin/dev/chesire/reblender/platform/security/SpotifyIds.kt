package dev.chesire.reblender.platform.security

/** Shape checks for Spotify identifiers, applied to anything that arrives from a request or from Spotify. */
object SpotifyIds {
    /** Playlist and track IDs are 22-character base62 strings. */
    private val BASE62_ID = Regex("^[A-Za-z0-9]{22}$")

    /**
     * User IDs are usually base62, but older accounts have usernames, which can contain dots, underscores and
     * hyphens. Anything else (slashes, spaces, control characters) is rejected.
     */
    private val USER_ID = Regex("^[A-Za-z0-9._-]{1,128}$")

    fun isPlaylistId(value: String?): Boolean = value != null && BASE62_ID.matches(value)

    fun isTrackId(value: String?): Boolean = value != null && BASE62_ID.matches(value)

    fun isUserId(value: String?): Boolean = value != null && USER_ID.matches(value)
}
