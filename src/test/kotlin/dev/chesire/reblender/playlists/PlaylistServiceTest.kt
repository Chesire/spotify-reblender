package dev.chesire.reblender.playlists

import dev.chesire.reblender.accounts.AccountService
import dev.chesire.reblender.accounts.models.AppUser
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.blend.models.Track
import dev.chesire.reblender.integrations.spotify.SpotifyClient
import dev.chesire.reblender.integrations.spotify.models.CreatedPlaylist
import dev.chesire.reblender.playlists.data.BlendEntity
import dev.chesire.reblender.playlists.data.BlendRepository
import dev.chesire.reblender.playlists.data.toEntity
import dev.chesire.reblender.playlists.models.Blend
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistServiceTest {
    private val spotify = mock(SpotifyClient::class.java)
    private val accounts = mock(AccountService::class.java)
    private val blends = mock(BlendRepository::class.java)
    private val service = PlaylistService(spotify, accounts, blends)

    private val playlistId = "0123456789abcdefghijkl"

    init {
        `when`(accounts.find("owner")).thenReturn(AppUser("owner", "Owner"))
        `when`(accounts.find("quiet")).thenReturn(AppUser("quiet", "Quiet Person", allowBlending = false))
        `when`(accounts.find("friend")).thenReturn(AppUser("friend", "Friend"))
    }

    private fun stored(vararg memberIds: String): BlendEntity = Blend(
        playlistId = playlistId,
        playlistName = "A playlist",
        playlistUrl = "https://open.spotify.com/playlist/$playlistId",
        ownerId = "owner",
        memberIds = memberIds.toList(),
        timeRange = TimeRange.RECENT,
        size = 10,
        sharedMode = SharedMode.CAPPED,
        trackCount = 10,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    ).toEntity(emptyList()).also { `when`(blends.findByPlaylistIdAndOwnerId(playlistId, "owner")).thenReturn(it) }

    private fun tracks(prefix: String) =
        (1..10).map { Track("$prefix$it".padEnd(22, 'x'), "spotify:track:$prefix$it".padEnd(36, 'x'), "$prefix$it", listOf("a")) }

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
        stored("owner", "quiet")
        assertFailsWith<IllegalArgumentException> { service.update("owner", playlistId) }
        verifyNoInteractions(spotify)
    }

    @Test
    fun `creating saves the blend with its members and songs`() {
        `when`(spotify.topTracks("friend", TimeRange.RECENT)).thenReturn(tracks("f"))
        `when`(spotify.topTracks("owner", TimeRange.RECENT)).thenReturn(tracks("o"))
        `when`(
            spotify.createPlaylist(
                "owner",
                "Owner & Friend - Recent favourites",
                "Balanced blend of Owner and Friend's recent favourites. Rebuilt by spotify-reblender.",
            ),
        ).thenReturn(CreatedPlaylist(playlistId, "https://open.spotify.com/playlist/$playlistId"))

        val outcome = service.create("owner", listOf("friend"), TimeRange.RECENT, 10, SharedMode.CAPPED)

        assertEquals(listOf("owner", "friend"), outcome.blend.memberIds)
        val saved = mockingDetails(blends)
            .invocations
            .single { it.method.name == "insert" }
            .arguments
            .single() as BlendEntity
        assertEquals(listOf("owner", "friend"), saved.members.map { it.spotifyId })
        assertEquals(10, saved.tracks.size)
        assertTrue(saved.isShuffled, "new blends are shuffled unless asked not to be")
    }

    @Test
    fun `updating keeps the blend's shuffle setting`() {
        `when`(spotify.topTracks("friend", TimeRange.RECENT)).thenReturn(tracks("f"))
        `when`(spotify.topTracks("owner", TimeRange.RECENT)).thenReturn(tracks("o"))
        val entity = stored("owner", "friend").copy(isShuffled = true)
        `when`(blends.findByPlaylistIdAndOwnerId(playlistId, "owner")).thenReturn(entity)

        val outcome = service.update("owner", playlistId)

        assertTrue(outcome.blend.isShuffled)
        val saved = mockingDetails(blends)
            .invocations
            .single { it.method.name == "save" }
            .arguments
            .single() as BlendEntity
        assertTrue(saved.isShuffled)
        assertEquals((tracks("o").take(5) + tracks("f").take(5)).map { it.id }.toSet(), saved.tracks.map { it.trackId }.toSet())
    }

    @Test
    fun `malformed or too many partner ids are rejected before anything else happens`() {
        assertFailsWith<IllegalArgumentException> {
            service.create("owner", listOf("../../me"), TimeRange.RECENT, 10, SharedMode.CAPPED)
        }
        assertFailsWith<IllegalArgumentException> {
            service.create("owner", listOf("a", "b", "c", "d", "e"), TimeRange.RECENT, 10, SharedMode.CAPPED)
        }
        verifyNoInteractions(spotify)
    }

    @Test
    fun `malformed playlist ids are rejected and never looked up`() {
        assertFailsWith<IllegalArgumentException> { service.update("owner", "../etc") }
        assertNull(service.details("owner", "../etc"))
        verifyNoInteractions(blends)
    }

    @Test
    fun `a Spotify refusal still stops managing the blend and reports the status`() {
        val blend = stored("owner")
        doThrow(
            HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders.EMPTY,
                """{"error": {"status": 403, "message": "Insufficient client scope" } }""".toByteArray(),
                Charsets.UTF_8,
            ),
        ).`when`(spotify).removePlaylistFromLibrary("owner", playlistId)

        val outcome = service.delete("owner", playlistId, removeFromSpotify = true)

        assertFalse(outcome.removedFromSpotify)
        assertEquals(403, outcome.spotifyStatus)
        verify(blends).delete(blend)
    }
}
