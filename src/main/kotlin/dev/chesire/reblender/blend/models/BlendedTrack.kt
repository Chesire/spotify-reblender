package dev.chesire.reblender.blend.models

/** A chosen track and everyone whose top list it came from. More than one contributor = shared. */
data class BlendedTrack(val track: Track, val contributors: Set<String>) {
    val isShared: Boolean get() = contributors.size > 1
}
