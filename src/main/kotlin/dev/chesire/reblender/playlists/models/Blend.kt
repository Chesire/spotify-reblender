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
    val isShuffled: Boolean = false,
    val trackCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Whether the weekly job rebuilds this blend. */
    val autoUpdate: Boolean = true,
    /** When the weekly job last tried this blend, whether or not it worked. */
    val lastAutoUpdateAt: Instant? = null,
    /** Why that attempt didn't update the playlist, or null if it did. */
    val lastAutoUpdateFailure: AutoUpdateFailure? = null,
    val lastAutoUpdateDetail: String? = null,
)
