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

/** What to do when the group shares more tracks than the shared budget allows. */
enum class SharedMode(val label: String) {
    /** Shared songs are capped by group size: 1/3 for 2 people up to 4/5 for 5 (see [BlendEngine.sharedShare]). */
    CAPPED("Cap shared songs by group size"),

    /** Shared songs may fill the playlist. */
    FILL("Let shared songs fill it"),
}

/** One person in a blend, with their ranked top tracks (best first). */
data class Participant(val id: String, val tracks: List<Track>)

/** A chosen track and everyone whose top list it came from. More than one contributor = shared. */
data class BlendedTrack(val track: Track, val contributors: Set<String>) {
    val isShared: Boolean get() = contributors.size > 1
}
