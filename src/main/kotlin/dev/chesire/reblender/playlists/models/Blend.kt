package dev.chesire.reblender.playlists.models

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import java.time.Instant

/** A playlist this app manages: who's in it and how to rebuild it. */
data class Blend(
    val playlistId: String,
    val playlistName: String,
    val playlistUrl: String,
    val ownerId: String,
    /** Everyone in the blend, owner first. One entry means a solo playlist. */
    val memberIds: List<String>,
    val timeRange: TimeRange,
    val size: Int,
    val sharedMode: SharedMode,
    val trackCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
