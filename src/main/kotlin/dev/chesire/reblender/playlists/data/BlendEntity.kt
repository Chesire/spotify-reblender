package dev.chesire.reblender.playlists.data

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.platform.security.SafeUrls
import dev.chesire.reblender.playlists.models.AutoUpdateFailure
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendTrack
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/** A managed playlist with its members and the songs from its last build, saved and loaded as one unit. */
@Table("blend")
data class BlendEntity(
    @Id val playlistId: String,
    val playlistName: String,
    val playlistUrl: String,
    val ownerId: String,
    val timeRange: TimeRange,
    val size: Int,
    val sharedMode: SharedMode,
    val isShuffled: Boolean,
    val trackCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val autoUpdate: Boolean,
    val lastAutoUpdateAt: Instant?,
    val lastAutoUpdateFailure: AutoUpdateFailure?,
    val lastAutoUpdateDetail: String?,
    /** Everyone in the blend, owner first. */
    @MappedCollection(idColumn = "playlist_id", keyColumn = "member_order")
    val members: List<BlendMemberEntity>,
    /** The songs in playlist order. */
    @MappedCollection(idColumn = "playlist_id", keyColumn = "track_order")
    val tracks: List<BlendTrackEntity>,
)

@Table("blend_member")
data class BlendMemberEntity(
    val spotifyId: String,
)

@Table("blend_track")
data class BlendTrackEntity(
    val trackId: String,
    val trackUri: String,
    val trackName: String,
    /** Joined with [SEPARATOR]. */
    val artists: String,
    /** Spotify IDs of who the song came from, joined with [SEPARATOR]. More than one means shared. */
    val contributors: String,
    val imageUrl: String?,
)

/** ASCII unit separator: can't appear in names, so lists round-trip safely. */
private const val SEPARATOR = "\u001F"

fun BlendEntity.toModel() = Blend(
    playlistId = playlistId,
    playlistName = playlistName,
    playlistUrl = playlistUrl,
    ownerId = ownerId,
    memberIds = members.map { it.spotifyId },
    timeRange = timeRange,
    size = size,
    sharedMode = sharedMode,
    isShuffled = isShuffled,
    trackCount = trackCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    autoUpdate = autoUpdate,
    lastAutoUpdateAt = lastAutoUpdateAt,
    lastAutoUpdateFailure = lastAutoUpdateFailure,
    lastAutoUpdateDetail = lastAutoUpdateDetail,
)

fun BlendEntity.tracksToModel() = tracks.map {
    BlendTrack(
        trackId = it.trackId,
        uri = it.trackUri,
        name = it.trackName,
        artists = it.artists.split(SEPARATOR).filter(String::isNotEmpty),
        contributors = it.contributors.split(SEPARATOR).filter(String::isNotEmpty),
        imageUrl = it.imageUrl,
    )
}

fun Blend.toEntity(tracks: List<BlendTrack>) = BlendEntity(
    playlistId = playlistId,
    playlistName = playlistName,
    playlistUrl = playlistUrl,
    ownerId = ownerId,
    timeRange = timeRange,
    size = size,
    sharedMode = sharedMode,
    isShuffled = isShuffled,
    trackCount = trackCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    autoUpdate = autoUpdate,
    lastAutoUpdateAt = lastAutoUpdateAt,
    lastAutoUpdateFailure = lastAutoUpdateFailure,
    lastAutoUpdateDetail = lastAutoUpdateDetail?.take(200),
    members = memberIds.map(::BlendMemberEntity),
    tracks = tracks.map(BlendTrack::toEntity),
)

private fun BlendTrack.toEntity() = BlendTrackEntity(
    trackId = trackId,
    trackUri = uri,
    trackName = name.take(500),
    artists = artists.joinToString(SEPARATOR).take(1000),
    contributors = contributors.joinToString(SEPARATOR),
    imageUrl = SafeUrls.httpsOrNull(imageUrl),
)
