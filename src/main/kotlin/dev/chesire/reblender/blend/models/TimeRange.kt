package dev.chesire.reblender.blend.models

/** Which Spotify top-items window to use. These are the only three Spotify offers. */
enum class TimeRange(val spotifyValue: String, val label: String, val window: String) {
    /** Spotify's short_term: roughly the last 4 weeks. */
    RECENT("short_term", "Recent favourites", "last ~4 weeks"),

    /** Spotify's medium_term: roughly the last 6 months. */
    HALF_YEAR("medium_term", "Half-year favourites", "last ~6 months"),

    /** Spotify's long_term: roughly the last year (Spotify has no true "all time"). */
    ALL_TIME("long_term", "All-time favourites", "last ~year"),
}
