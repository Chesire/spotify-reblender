package dev.chesire.reblender.playlists.models

/** A created/updated blend plus how many usable top tracks each member had (to explain short playlists). */
data class BlendOutcome(
    val blend: Blend,
    val usableTracks: Map<String, Int>,
)
