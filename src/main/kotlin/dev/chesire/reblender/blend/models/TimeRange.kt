package dev.chesire.reblender.blend.models

/** Which listening window to blend from. These match the three windows Spotify offers for top tracks. */
enum class TimeRange {
    /** Roughly the last 4 weeks. */
    RECENT,

    /** Roughly the last 6 months. */
    HALF_YEAR,

    /** Roughly the last year (Spotify has no true "all time"). */
    ALL_TIME,
}
