package dev.chesire.reblender.blend.models

/** A track as the blend engine sees it. Identity is the Spotify track ID. */
data class Track(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
    /** Album cover, if Spotify sent one. Display only; not used for blending. */
    val imageUrl: String? = null,
)
