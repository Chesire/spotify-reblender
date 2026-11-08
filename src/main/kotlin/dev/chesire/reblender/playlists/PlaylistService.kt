package dev.chesire.reblender.playlists

import dev.chesire.reblender.accounts.AccountService
import dev.chesire.reblender.accounts.models.AppUser
import dev.chesire.reblender.blend.BlendEngine
import dev.chesire.reblender.blend.models.BlendedTrack
import dev.chesire.reblender.blend.models.Participant
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.integrations.spotify.SpotifyClient
import dev.chesire.reblender.platform.security.SpotifyIds
import dev.chesire.reblender.playlists.data.BlendEntity
import dev.chesire.reblender.playlists.data.BlendRepository
import dev.chesire.reblender.playlists.data.toEntity
import dev.chesire.reblender.playlists.data.toModel
import dev.chesire.reblender.playlists.data.tracksToModel
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendDetails
import dev.chesire.reblender.playlists.models.BlendOutcome
import dev.chesire.reblender.playlists.models.BlendTrack
import dev.chesire.reblender.playlists.models.DeleteOutcome
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import java.time.Instant

@Service
class PlaylistService(
    private val spotify: SpotifyClient,
    private val accounts: AccountService,
    private val blends: BlendRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun playlistsFor(ownerId: String): List<Blend> = blends.findByOwnerIdOrderByCreatedAtDesc(ownerId).map { it.toModel() }

    /** The blend with its songs and members, or null if it isn't one of [ownerId]'s. */
    fun details(
        ownerId: String,
        playlistId: String,
    ): BlendDetails? {
        if (!SpotifyIds.isPlaylistId(playlistId)) return null
        val entity = blends.findByPlaylistIdAndOwnerId(playlistId, ownerId) ?: return null
        val blend = entity.toModel()
        val members = blend.memberIds.map { id -> accounts.find(id) ?: AppUser(id, id) }
        return BlendDetails(blend, members, entity.tracksToModel())
    }

    /**
     * Creates a playlist owned by [ownerId] from the owner plus up to four [partnerIds].
     * No partners gives a playlist of just the owner's top tracks.
     */
    fun create(
        ownerId: String,
        partnerIds: List<String>,
        timeRange: TimeRange,
        size: Int,
        sharedMode: SharedMode,
    ): BlendOutcome {
        val members = validMembers(ownerId, partnerIds)
        val memberIds = members.map { it.spotifyId }
        val allowed = BlendEngine.sizeRange(memberIds.size)
        require(size in allowed) {
            "With ${memberIds.size} ${if (memberIds.size == 1) "person" else "people"} the playlist must have " +
                "between ${allowed.first} and ${allowed.last} songs."
        }
        val names = members.map { it.displayName }

        val (blended, usable) = buildBlend(memberIds, timeRange, size, sharedMode)

        val name = PlaylistNaming.playlistName(names, timeRange)
        val playlist = spotify.createPlaylist(ownerId, name, PlaylistNaming.description(names, timeRange))
        spotify.replaceItems(ownerId, playlist.id, blended.map { it.track.uri })

        val now = Instant.now()
        val blend = Blend(
            playlistId = playlist.id,
            playlistName = name,
            playlistUrl = playlist.url,
            ownerId = ownerId,
            memberIds = memberIds,
            timeRange = timeRange,
            size = size,
            sharedMode = sharedMode,
            trackCount = blended.size,
            createdAt = now,
            updatedAt = now,
        )
        blends.insert(blend.toEntity(blended.map(::toStored)))
        return BlendOutcome(blend, usable)
    }

    /** Re-runs the blend with fresh top tracks and replaces the playlist contents. */
    fun update(
        ownerId: String,
        playlistId: String,
    ): BlendOutcome {
        val blend = ownedBlend(ownerId, playlistId).toModel()
        // Someone who has since opted out must not have their top tracks read again.
        blend.memberIds
            .drop(1)
            .mapNotNull(accounts::find)
            .firstOrNull { !it.allowBlending }
            ?.let {
                throw IllegalArgumentException(
                    "${it.displayName} has turned off being included in blends, so this one can't be updated. " +
                        "Delete it or make a new one without them.",
                )
            }
        val (blended, usable) = buildBlend(blend.memberIds, blend.timeRange, blend.size, blend.sharedMode)
        spotify.replaceItems(ownerId, playlistId, blended.map { it.track.uri })
        val updated = blend.copy(trackCount = blended.size, updatedAt = Instant.now())
        blends.save(updated.toEntity(blended.map(::toStored)))
        return BlendOutcome(updated, usable)
    }

    /**
     * Stops managing a blend and, if [removeFromSpotify], also removes the playlist from the owner's Spotify library.
     * The app's record is deleted even if Spotify refuses, so the user is never stuck with a blend they can't remove.
     */
    fun delete(
        ownerId: String,
        playlistId: String,
        removeFromSpotify: Boolean,
    ): DeleteOutcome {
        val blend = ownedBlend(ownerId, playlistId)

        var removed = false
        var status: Int? = null
        if (removeFromSpotify) {
            try {
                spotify.removePlaylistFromLibrary(ownerId, playlistId)
                removed = true
            } catch (e: RestClientResponseException) {
                if (e.statusCode.value() == 404) {
                    removed = true // already gone on Spotify's side
                } else {
                    status = e.statusCode.value()
                    log.warn("Spotify refused to remove playlist {} ({}): {}", playlistId, status, e.responseBodyAsString)
                }
            }
        }
        blends.delete(blend)
        return DeleteOutcome(blend.playlistName, removed, status)
    }

    /** The owner plus validated partners, in order. Everything here came from a request, so it's all checked. */
    private fun validMembers(
        ownerId: String,
        partnerIds: List<String>,
    ): List<AppUser> {
        val maxPartners = BlendEngine.MAX_PARTICIPANTS - 1
        require(partnerIds.size <= maxPartners) { "Pick at most $maxPartners other people." }
        val partners = partnerIds.filter { it.isNotBlank() }.distinct()
        require(partners.all(SpotifyIds::isUserId)) { "That isn't a valid person to blend with." }
        require(ownerId !in partners) { "You're always in your own blend; pick other people." }

        val owner = accounts.find(ownerId) ?: throw IllegalArgumentException("Unknown user. Log out and in again.")
        val others = partners.map { id ->
            accounts.find(id) ?: throw IllegalArgumentException("Someone you picked hasn't logged in yet.")
        }
        // Enforced here, not just by hiding people in the form: a crafted request can't include someone who opted out.
        others.firstOrNull { !it.allowBlending }?.let {
            throw IllegalArgumentException("${it.displayName} has turned off being included in blends.")
        }
        return listOf(owner) + others
    }

    private fun ownedBlend(
        ownerId: String,
        playlistId: String,
    ): BlendEntity {
        require(SpotifyIds.isPlaylistId(playlistId)) { "That isn't a valid playlist." }
        return blends.findByPlaylistIdAndOwnerId(playlistId, ownerId)
            ?: throw IllegalArgumentException("That playlist isn't one of yours.")
    }

    private fun toStored(track: BlendedTrack) = BlendTrack(
        trackId = track.track.id,
        uri = track.track.uri,
        name = track.track.name,
        artists = track.track.artists,
        contributors = track.contributors.toList(),
        imageUrl = track.track.imageUrl,
    )

    private fun buildBlend(
        memberIds: List<String>,
        timeRange: TimeRange,
        size: Int,
        sharedMode: SharedMode,
    ): Pair<List<BlendedTrack>, Map<String, Int>> {
        // Others first: if someone's login has expired we fail before touching the owner's playlist.
        val tracksById = (memberIds.drop(1) + memberIds.first())
            .associateWith { spotify.topTracks(it, timeRange) }
        val participants = memberIds.map { Participant(it, tracksById.getValue(it)) }
        val blended = BlendEngine.blend(participants, size, sharedMode)
        require(blended.isNotEmpty()) { "Nobody in this blend has any top tracks for that time range yet." }
        return blended to memberIds.associateWith { tracksById.getValue(it).size }
    }
}
