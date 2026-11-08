package dev.chesire.reblender.playlists.models

/** One song in the last build of a blend, and who it came from (more than one contributor = shared). */
data class BlendTrack(
    val trackId: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
    val contributors: List<String>,
    val imageUrl: String? = null,
)
