package dev.chesire.reblender.playlists.api

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.models.AutoUpdateFailure
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendDetails
import dev.chesire.reblender.playlists.models.BlendOutcome
import dev.chesire.reblender.playlists.models.DeleteOutcome
import java.time.Instant

data class PlaylistResponse(
    val id: String,
    val name: String,
    val url: String,
    val ownerId: String,
    /** Everyone in the blend, owner first. */
    val memberIds: List<String>,
    val timeRange: TimeRange,
    val size: Int,
    val sharedMode: SharedMode,
    val trackCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Whether the weekly job rebuilds it. */
    val autoUpdate: Boolean,
    val lastAutoUpdateAt: Instant?,
    /** Why the last weekly update didn't happen, or null if it did (or hasn't run yet). */
    val lastAutoUpdateFailure: AutoUpdateFailure?,
    val lastAutoUpdateDetail: String?,
) {
    companion object {
        fun of(blend: Blend) = PlaylistResponse(
            id = blend.playlistId,
            name = blend.playlistName,
            url = blend.playlistUrl,
            ownerId = blend.ownerId,
            memberIds = blend.memberIds,
            timeRange = blend.timeRange,
            size = blend.size,
            sharedMode = blend.sharedMode,
            trackCount = blend.trackCount,
            createdAt = blend.createdAt,
            updatedAt = blend.updatedAt,
            autoUpdate = blend.autoUpdate,
            lastAutoUpdateAt = blend.lastAutoUpdateAt,
            lastAutoUpdateFailure = blend.lastAutoUpdateFailure,
            lastAutoUpdateDetail = blend.lastAutoUpdateDetail,
        )
    }
}

/** A created or refreshed playlist, plus how many usable top tracks each member had (explains short playlists). */
data class PlaylistResultResponse(
    val playlist: PlaylistResponse,
    val usableTracks: Map<String, Int>,
) {
    companion object {
        fun of(outcome: BlendOutcome) = PlaylistResultResponse(PlaylistResponse.of(outcome.blend), outcome.usableTracks)
    }
}

data class PlaylistDetailsResponse(
    val playlist: PlaylistResponse,
    val members: List<MemberResponse>,
    val tracks: List<TrackResponse>,
) {
    companion object {
        fun of(details: BlendDetails) = PlaylistDetailsResponse(
            playlist = PlaylistResponse.of(details.blend),
            members = details.members.map { MemberResponse(it.spotifyId, it.displayName, it.avatarUrl) },
            tracks = details.tracks.map {
                TrackResponse(it.trackId, it.uri, it.name, it.artists, it.contributors, it.imageUrl)
            },
        )
    }
}

data class MemberResponse(
    val spotifyId: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class TrackResponse(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
    /** Spotify IDs of the members it came from. More than one means it's shared. */
    val contributors: List<String>,
    val imageUrl: String?,
)

data class DeleteResponse(
    val name: String,
    val removedFromSpotify: Boolean,
    /** The HTTP status Spotify refused the removal with, if it did. */
    val spotifyStatus: Int?,
) {
    companion object {
        fun of(outcome: DeleteOutcome) = DeleteResponse(outcome.playlistName, outcome.removedFromSpotify, outcome.spotifyStatus)
    }
}
