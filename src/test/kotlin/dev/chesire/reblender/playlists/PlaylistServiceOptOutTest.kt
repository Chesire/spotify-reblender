package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.blend.Track
import dev.chesire.reblender.spotify.CreatedPlaylist
import dev.chesire.reblender.spotify.SpotifyClient
import dev.chesire.reblender.users.AppUser
import dev.chesire.reblender.users.UserRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** S1: people who turned off blending can't be included, and their top tracks are never fetched. */
class PlaylistServiceOptOutTest {

    private val spotify = mock(SpotifyClient::class.java)
    private val users = mock(UserRepository::class.java)
    private val blends = mock(BlendRepository::class.java)
    private val service = PlaylistService(spotify, users, blends)

    private val owner = AppUser("owner", "Owner")
    private val optedOut = AppUser("quiet", "Quiet Person", allowBlending = false)
    private val willing = AppUser("friend", "Friend")

    init {
        `when`(users.findById("owner")).thenReturn(owner)
        `when`(users.findById("quiet")).thenReturn(optedOut)
        `when`(users.findById("friend")).thenReturn(willing)
    }

    @Test
    fun `cannot create a blend with someone who opted out`() {
        val error = assertFailsWith<IllegalArgumentException> {
            service.create("owner", listOf("quiet"), TimeRange.RECENT, 10, SharedMode.CAPPED)
        }
        assertContains(error.message.orEmpty(), "Quiet Person")
        verifyNoInteractions(spotify)
    }

    @Test
    fun `cannot update a blend once a member has opted out`() {
        val blend = Blend(
            playlistId = "0123456789abcdefghijkl",
            playlistName = "Owner & Quiet Person - Recent favourites",
            playlistUrl = "https://open.spotify.com/playlist/0123456789abcdefghijkl",
            ownerId = "owner",
            memberIds = listOf("owner", "quiet"),
            timeRange = TimeRange.RECENT,
            size = 10,
            sharedMode = SharedMode.CAPPED,
            trackCount = 10,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        `when`(blends.findByIdAndOwner(blend.playlistId, "owner")).thenReturn(blend)

        assertFailsWith<IllegalArgumentException> { service.update("owner", blend.playlistId) }
        verifyNoInteractions(spotify)
    }

    @Test
    fun `people who have not opted out can still be included`() {
        fun tracks(prefix: String) = (1..10).map { Track("$prefix$it".padEnd(22, 'x'), "spotify:track:$prefix$it", "$prefix$it", listOf("a")) }
        `when`(spotify.topTracks("friend", TimeRange.RECENT)).thenReturn(tracks("f"))
        `when`(spotify.topTracks("owner", TimeRange.RECENT)).thenReturn(tracks("o"))
        `when`(spotify.createPlaylist("owner", "Owner & Friend - Recent favourites", "Balanced blend of Owner and Friend's recent favourites. Rebuilt by spotify-reblender."))
            .thenReturn(CreatedPlaylist("0123456789abcdefghijkl", "https://open.spotify.com/playlist/0123456789abcdefghijkl"))

        val outcome = service.create("owner", listOf("friend"), TimeRange.RECENT, 10, SharedMode.CAPPED)
        assertEquals(listOf("owner", "friend"), outcome.blend.memberIds)
    }
}
