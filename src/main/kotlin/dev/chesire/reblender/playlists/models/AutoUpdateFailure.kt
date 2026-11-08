package dev.chesire.reblender.playlists.models

/** Why the last weekly update of a blend didn't happen. The detail stored alongside depends on the reason. */
enum class AutoUpdateFailure {
    /** Detail: the Spotify ID of the member whose login expired. */
    RELOGIN_REQUIRED,

    /** Detail: the Spotify ID of the member who turned blending off. */
    MEMBER_OPTED_OUT,

    /** Spotify no longer has the playlist. */
    PLAYLIST_GONE,

    /** Spotify's rate limit or quota was hit; the rest of that run was skipped too. */
    SPOTIFY_LIMIT,

    /** Detail: the HTTP status Spotify returned. */
    SPOTIFY_ERROR,

    /** Detail: a short description. */
    OTHER,
}
