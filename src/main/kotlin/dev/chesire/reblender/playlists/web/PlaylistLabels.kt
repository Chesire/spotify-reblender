package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.PlaylistNaming

/** Display text for the blend settings, and the options shown in the new-playlist form. */
object PlaylistLabels {
    fun title(range: TimeRange): String = PlaylistNaming.title(range)

    fun window(range: TimeRange): String =
        when (range) {
            TimeRange.RECENT -> "last ~4 weeks"
            TimeRange.HALF_YEAR -> "last ~6 months"
            TimeRange.ALL_TIME -> "last ~year"
        }

    fun sharedMode(mode: SharedMode): String =
        when (mode) {
            SharedMode.CAPPED -> "Cap shared songs by group size"
            SharedMode.FILL -> "Let shared songs fill it"
        }

    /** Short form for the segmented button. */
    fun sharedModeOption(mode: SharedMode): String =
        when (mode) {
            SharedMode.CAPPED -> "Cap by group size"
            SharedMode.FILL -> "Let them fill it"
        }

    val timeRangeOptions: List<TimeRangeOption> =
        TimeRange.entries.map { TimeRangeOption(it.name, title(it), window(it)) }

    val sharedModeOptions: List<SharedModeOption> =
        SharedMode.entries.map { SharedModeOption(it.name, sharedModeOption(it), sharedMode(it)) }
}

data class TimeRangeOption(
    val value: String,
    val label: String,
    val window: String,
)

data class SharedModeOption(
    val value: String,
    val label: String,
    val description: String,
)
