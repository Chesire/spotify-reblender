package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.playlists.models.Blend

/** One blend in the "Your blends" list. */
data class PlaylistRow(
    val blend: Blend,
    /** "with Sam and Alex" or "just you". */
    val withLabel: String,
    val updatedLabel: String,
    val autoUpdate: AutoUpdateStatus,
)

/** The weekly-update line for a blend, e.g. "updates weekly" or "weekly update skipped: Sam needs to log in again". */
data class AutoUpdateStatus(
    val text: String,
    /** Shown as an error: the last weekly update didn't happen. */
    val problem: Boolean,
)
