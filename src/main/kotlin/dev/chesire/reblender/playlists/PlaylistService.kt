package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.BlendEngine
import dev.chesire.reblender.blend.BlendedTrack
import dev.chesire.reblender.blend.Participant
import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.spotify.SpotifyClient
import dev.chesire.reblender.users.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

/** A created/updated blend plus how many usable top tracks each member had (to explain short playlists). */
data class BlendOutcome(val blend: Blend, val usableTracks: Map<String, Int>)

@Service
class PlaylistService(
    private val spotify: SpotifyClient,
    private val users: UserRepository,
    private val blends: BlendRepository,
) {

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
        val partners = partnerIds.filter { it.isNotBlank() }.distinct()
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
        val names = memberIds.map { id ->
            users.findById(id)?.displayName
                ?: throw IllegalArgumentException(if (id == ownerId) "Unknown user. Log out and in again." else "Someone you picked hasn't logged in yet.")
        }

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
        return BlendOutcome(blend, usable)
    }

    /** Re-runs the blend with fresh top tracks and replaces the playlist contents. */
    fun update(ownerId: String, playlistId: String): BlendOutcome {
        val blend = blends.findByIdAndOwner(playlistId, ownerId)
            ?: throw IllegalArgumentException("That playlist isn't one of yours.")
        val (blended, usable) = buildBlend(blend.memberIds, blend.timeRange, blend.size, blend.sharedMode)
        spotify.replaceItems(ownerId, playlistId, blended.map { it.track.uri })
        val now = Instant.now()
        blends.markRefreshed(playlistId, blended.size, now)
        return BlendOutcome(blend.copy(trackCount = blended.size, updatedAt = now), usable)
    }

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
