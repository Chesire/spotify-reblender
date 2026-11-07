package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.spotify.SpotifyClient
import dev.chesire.reblender.users.UserRepository
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistServiceDeleteTest {

    private val spotify = mock(SpotifyClient::class.java)
    private val users = mock(UserRepository::class.java)
    private val blends = mock(BlendRepository::class.java)
    private val service = PlaylistService(spotify, users, blends)

    private val id = "0123456789abcdefghijkl"
    private val blend = Blend(
        playlistId = id,
        playlistName = "Owner - Recent favourites",
        playlistUrl = "https://open.spotify.com/playlist/$id",
        ownerId = "owner",
        memberIds = listOf("owner"),
        timeRange = TimeRange.RECENT,
        size = 10,
        sharedMode = SharedMode.CAPPED,
        trackCount = 10,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    init {
        `when`(blends.findByIdAndOwner(id, "owner")).thenReturn(blend)
    }

    private fun forbidden(body: String) = HttpClientErrorException.create(
        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, body.toByteArray(), Charsets.UTF_8,
    )

    @Test
    fun `a login without the delete scope keeps the blend so it can be retried`() {
        doThrow(forbidden("""{"error": {"status": 403, "message": "Insufficient client scope" } }"""))
            .`when`(spotify).removePlaylistFromLibrary("owner", id)

        val outcome = service.delete("owner", id, removeFromSpotify = true)

        assertTrue(outcome.needsRelogin)
        assertFalse(outcome.removedFromSpotify)
        verify(blends, never()).delete(id, "owner")
    }

    @Test
    fun `other refusals still stop managing the blend`() {
        doThrow(forbidden("""{"error": {"status": 403, "message": "Forbidden" } }"""))
            .`when`(spotify).removePlaylistFromLibrary("owner", id)

        val outcome = service.delete("owner", id, removeFromSpotify = true)

        assertFalse(outcome.needsRelogin)
        verify(blends).delete(id, "owner")
    }
}
