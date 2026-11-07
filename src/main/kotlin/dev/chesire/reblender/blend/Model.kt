package dev.chesire.reblender.blend

/** A track as the blend engine sees it. Identity is the Spotify track ID. */
data class Track(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
)

/** Which Spotify top-items window to use. */
enum class TimeRange(val spotifyValue: String, val label: String) {
    /** Spotify's short_term: roughly the last 4 weeks. */
    RECENT("short_term", "Recent favourites"),

    /** Spotify's long_term: roughly the last year (Spotify has no true "all time"). */
    ALL_TIME("long_term", "All-time favourites"),
}

/** What to do when both users share more tracks than the playlist needs. */
enum class SharedMode(val label: String) {
    /** At most a third of the playlist is shared tracks; the rest are each user's own picks. */
    CAP_THIRD("Cap shared songs at a third"),

    /** Shared tracks may fill the playlist. */
    FILL("Let shared songs fill it"),
}

enum class Source { A, B, SHARED }

data class BlendedTrack(val track: Track, val source: Source)
