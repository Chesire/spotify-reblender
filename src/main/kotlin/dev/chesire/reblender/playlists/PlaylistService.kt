package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.BlendEngine
import dev.chesire.reblender.blend.BlendedTrack
import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.spotify.SpotifyClient
import dev.chesire.reblender.users.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PlaylistService(
    private val spotify: SpotifyClient,
    private val users: UserRepository,
    private val pairings: PairingRepository,
) {

    fun create(ownerId: String, partnerId: String, timeRange: TimeRange, size: Int, sharedMode: SharedMode): Pairing {
        require(ownerId != partnerId) { "Pick someone other than yourself." }
        require(size in BlendEngine.MIN_SIZE..MAX_SIZE) { "Size must be between ${BlendEngine.MIN_SIZE} and $MAX_SIZE." }
        val owner = users.findById(ownerId) ?: throw IllegalArgumentException("Unknown user. Log out and in again.")
        val partner = users.findById(partnerId) ?: throw IllegalArgumentException("That partner hasn't logged in yet.")

        val blended = buildBlend(ownerId, partnerId, timeRange, size, sharedMode)

        // ASCII only and well under Spotify's description limit.
        val name = "${owner.displayName} & ${partner.displayName} - ${timeRange.label}"
        val description = "Balanced blend of ${owner.displayName} and ${partner.displayName}'s " +
            "${timeRange.label.lowercase()}. Rebuilt by spotify-reblender."
        val playlist = spotify.createPlaylist(ownerId, name, description)
        spotify.replaceItems(ownerId, playlist.id, blended.map { it.track.uri })

        val now = Instant.now()
        return Pairing(
            playlistId = playlist.id,
            playlistName = name,
            playlistUrl = playlist.url,
            ownerId = ownerId,
            partnerId = partnerId,
            timeRange = timeRange,
            size = size,
            sharedMode = sharedMode,
            trackCount = blended.size,
            createdAt = now,
            updatedAt = now,
        ).also(pairings::insert)
    }

    /** Re-runs the blend with fresh top tracks and replaces the playlist contents. */
    fun update(ownerId: String, playlistId: String): Pairing {
        val pairing = pairings.findByIdAndOwner(playlistId, ownerId)
            ?: throw IllegalArgumentException("That playlist isn't one of yours.")
        val blended = buildBlend(ownerId, pairing.partnerId, pairing.timeRange, pairing.size, pairing.sharedMode)
        spotify.replaceItems(ownerId, playlistId, blended.map { it.track.uri })
        val now = Instant.now()
        pairings.markRefreshed(playlistId, blended.size, now)
        return pairing.copy(trackCount = blended.size, updatedAt = now)
    }

    fun playlistsFor(ownerId: String): List<Pairing> = pairings.findByOwner(ownerId)

    private fun buildBlend(
        ownerId: String,
        partnerId: String,
        timeRange: TimeRange,
        size: Int,
        sharedMode: SharedMode,
    ): List<BlendedTrack> {
        // Partner first: if their login has expired we fail before touching anything.
        val partnerTracks = spotify.topTracks(partnerId, timeRange)
        val ownerTracks = spotify.topTracks(ownerId, timeRange)
        val blended = BlendEngine.blend(ownerTracks, partnerTracks, size, sharedMode)
        require(blended.isNotEmpty()) { "Neither of you has any top tracks for that time range yet." }
        return blended
    }

    companion object {
        /** Two users x 50 top tracks, and Spotify's 100-item limit for a single replace. */
        const val MAX_SIZE = 100
    }
}
