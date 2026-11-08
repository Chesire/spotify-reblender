package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.models.TimeRange

/** How managed playlists are named and described on Spotify. ASCII only and well under Spotify's limits. */
object PlaylistNaming {
    fun title(range: TimeRange): String = when (range) {
            TimeRange.RECENT -> "Recent favourites"
            TimeRange.HALF_YEAR -> "Half-year favourites"
            TimeRange.ALL_TIME -> "All-time favourites"
        }

    /** "Chesire & Sam - Recent favourites". */
    fun playlistName(
        names: List<String>,
        range: TimeRange,
    ): String = "${joinNames(names)} - ${title(range)}"

    fun description(
        names: List<String>,
        range: TimeRange,
    ): String {
        val what = title(range).lowercase()
        return if (names.size == 1) {
            "${names[0]}'s $what. Rebuilt by spotify-reblender."
        } else {
            "Balanced blend of ${joinNames(names, conjunction = "and")}'s $what. Rebuilt by spotify-reblender."
        }
    }

    /** "A", "A & B", "A, B & C" (or with "and" for prose). */
    fun joinNames(
        names: List<String>,
        conjunction: String = "&",
    ): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " $conjunction " + names.last()
        }
}
