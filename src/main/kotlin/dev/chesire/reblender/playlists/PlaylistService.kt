package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.BlendEngine
import dev.chesire.reblender.blend.models.BlendedTrack
import dev.chesire.reblender.blend.models.Participant
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendOutcome
import dev.chesire.reblender.playlists.models.BlendTrack
import dev.chesire.reblender.playlists.models.DeleteOutcome
import dev.chesire.reblender.security.SpotifyIds
import dev.chesire.reblender.spotify.SpotifyClient
import dev.chesire.reblender.users.UserRepository
import dev.chesire.reblender.users.models.AppUser
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException

@Service
class PlaylistService(
    private val spotify: SpotifyClient,
    private val users: UserRepository,
    private val blends: BlendRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        // Bound and shape-check what came from the form before using it in queries or messages.
        require(partnerIds.size <= BlendEngine.MAX_PARTICIPANTS - 1) {
            "Pick at most ${BlendEngine.MAX_PARTICIPANTS - 1} other people."
        }
        val partners = partnerIds.filter { it.isNotBlank() }.distinct()
        require(partners.all(SpotifyIds::isUserId)) { "That isn't a valid person to blend with." }
        require(ownerId !in partners) { "You're always in your own blend; pick other people." }
        require(partners.size <= BlendEngine.MAX_PARTICIPANTS - 1) {
            "Pick at most ${BlendEngine.MAX_PARTICIPANTS - 1} other people."
        }
        val memberIds = listOf(ownerId) + partners
        val allowed = BlendEngine.sizeRange(memberIds.size)
        require(size in allowed) {
            "With ${memberIds.size} ${if (memberIds.size == 1) "person" else "people"} the playlist must have " +
                "between ${allowed.first} and ${allowed.last} songs."
        }
        val members = memberIds.map { id ->
            users.findById(id)
                ?: throw IllegalArgumentException(if (id == ownerId) "Unknown user. Log out and in again." else "Someone you picked hasn't logged in yet.")
        }
        // Enforced here, not just by hiding people in the form: a crafted request can't include someone who opted out.
        members.drop(1).firstOrNull { !it.allowBlending }?.let {
            throw IllegalArgumentException("${it.displayName} has turned off being included in blends.")
        }
        val names = members.map { it.displayName }

        val (blended, usable) = buildBlend(memberIds, timeRange, size, sharedMode)

        // ASCII only and well under Spotify's description limit.
        val name = "${joinNames(names)} - ${timeRange.label}"
        val description = if (names.size == 1) {
            "${names[0]}'s ${timeRange.label.lowercase()}. Rebuilt by spotify-reblender."
        } else {
            "Balanced blend of ${joinNames(names, conjunction = "and")}'s ${timeRange.label.lowercase()}. Rebuilt by spotify-reblender."
        }
        val playlist = spotify.createPlaylist(ownerId, name, description)
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
        blends.insert(blend)
        blends.replaceTracks(playlist.id, blended.map(::toStored))
        return BlendOutcome(blend, usable)
    }

    /** Re-runs the blend with fresh top tracks and replaces the playlist contents. */
    fun update(ownerId: String, playlistId: String): BlendOutcome {
        val blend = blends.findByIdAndOwner(playlistId, ownerId)
            ?: throw IllegalArgumentException("That playlist isn't one of yours.")
        // Someone who has since opted out must not have their top tracks read again.
        blend.memberIds.drop(1).mapNotNull(users::findById).firstOrNull { !it.allowBlending }?.let {
            throw IllegalArgumentException(
                "${it.displayName} has turned off being included in blends, so this one can't be updated. " +
                    "Delete it or make a new one without them.",
            )
        }
        val (blended, usable) = buildBlend(blend.memberIds, blend.timeRange, blend.size, blend.sharedMode)
        spotify.replaceItems(ownerId, playlistId, blended.map { it.track.uri })
        val now = Instant.now()
        blends.markRefreshed(playlistId, blended.size, now)
        blends.replaceTracks(playlistId, blended.map(::toStored))
        return BlendOutcome(blend.copy(trackCount = blended.size, updatedAt = now), usable)
    }

    /**
     * Stops managing a blend and, if [removeFromSpotify], also removes the playlist from the owner's Spotify library.
     * The app's record is deleted even if Spotify refuses, so the user is never stuck with a blend they can't remove.
     */
    fun delete(ownerId: String, playlistId: String, removeFromSpotify: Boolean): DeleteOutcome {
        val blend = blends.findByIdAndOwner(playlistId, ownerId)
            ?: throw IllegalArgumentException("That playlist isn't one of yours.")

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
        blends.delete(playlistId, ownerId)
        return DeleteOutcome(blend.playlistName, removed, status)
    }

    /** Everything the breakdown page needs, or null if it isn't the owner's playlist. */
    fun breakdown(ownerId: String, playlistId: String): Breakdown? {
        val blend = blends.findByIdAndOwner(playlistId, ownerId) ?: return null
        val members = blend.memberIds.map { id -> users.findById(id) ?: AppUser(id, id) }
        return Breakdown.of(blend, members, blends.findTracks(playlistId))
    }

    private fun toStored(track: BlendedTrack) = BlendTrack(
        trackId = track.track.id,
        uri = track.track.uri,
        name = track.track.name,
        artists = track.track.artists,
        contributors = track.contributors.toList(),
        imageUrl = track.track.imageUrl,
    )

    fun playlistsFor(ownerId: String): List<Blend> = blends.findByOwner(ownerId)

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

    companion object {
        /** "A", "A & B", "A, B & C" (or with "and" for prose). */
        fun joinNames(names: List<String>, conjunction: String = "&"): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " $conjunction " + names.last()
        }
    }
}
