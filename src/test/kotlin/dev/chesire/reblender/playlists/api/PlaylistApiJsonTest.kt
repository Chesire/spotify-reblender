package dev.chesire.reblender.playlists.api

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.models.Blend
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The API's request and response shapes round-trip through Jackson with the Kotlin module, as Spring MVC uses them. */
class PlaylistApiJsonTest {
    private val json = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `create request uses defaults for optional fields`() {
        val request = json.readValue("""{"timeRange":"HALF_YEAR","size":30}""", CreatePlaylistRequest::class.java)
        assertEquals(CreatePlaylistRequest(emptyList(), TimeRange.HALF_YEAR, 30, SharedMode.CAPPED), request)
    }

    @Test
    fun `playlist response is plain JSON`() {
        val blend = Blend(
            playlistId = "0123456789abcdefghijkl",
            playlistName = "A & B - Recent favourites",
            playlistUrl = "https://open.spotify.com/playlist/0123456789abcdefghijkl",
            ownerId = "a",
            memberIds = listOf("a", "b"),
            timeRange = TimeRange.RECENT,
            size = 20,
            sharedMode = SharedMode.FILL,
            trackCount = 20,
            createdAt = Instant.parse("2026-11-08T12:00:00Z"),
            updatedAt = Instant.parse("2026-11-08T12:00:00Z"),
        )
        val text = json.writeValueAsString(PlaylistResponse.of(blend))
        assertContains(text, "\"id\":\"0123456789abcdefghijkl\"")
        assertContains(text, "\"timeRange\":\"RECENT\"")
        assertContains(text, "\"memberIds\":[\"a\",\"b\"]")
    }
}
