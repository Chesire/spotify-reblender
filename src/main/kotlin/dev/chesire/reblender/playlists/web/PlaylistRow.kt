package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.playlists.models.Blend

/** One blend in the "Your blends" list. */
data class PlaylistRow(
    val blend: Blend,
    /** "with Sam and Alex" or "just you". */
    val withLabel: String,
    val updatedLabel: String,
)
