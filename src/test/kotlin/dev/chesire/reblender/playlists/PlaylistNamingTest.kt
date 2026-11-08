package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.models.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistNamingTest {
    @Test
    fun `joins one to five names`() {
        assertEquals("A", PlaylistNaming.joinNames(listOf("A")))
        assertEquals("A & B", PlaylistNaming.joinNames(listOf("A", "B")))
        assertEquals("A, B & C", PlaylistNaming.joinNames(listOf("A", "B", "C")))
        assertEquals("A, B, C, D and E", PlaylistNaming.joinNames(listOf("A", "B", "C", "D", "E"), "and"))
    }

    @Test
    fun `names and describes solo and group playlists`() {
        assertEquals("Chesire - Recent favourites", PlaylistNaming.playlistName(listOf("Chesire"), TimeRange.RECENT))
        assertEquals(
            "Chesire's all-time favourites. Rebuilt by spotify-reblender.",
            PlaylistNaming.description(listOf("Chesire"), TimeRange.ALL_TIME),
        )
        assertEquals(
            "Balanced blend of A and B's half-year favourites. Rebuilt by spotify-reblender.",
            PlaylistNaming.description(listOf("A", "B"), TimeRange.HALF_YEAR),
        )
    }
}
