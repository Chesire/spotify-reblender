package dev.chesire.reblender.playlists

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistNamesTest {
    @Test
    fun `joins one to five names`() {
        assertEquals("A", PlaylistService.joinNames(listOf("A")))
        assertEquals("A & B", PlaylistService.joinNames(listOf("A", "B")))
        assertEquals("A, B & C", PlaylistService.joinNames(listOf("A", "B", "C")))
        assertEquals("A, B, C, D and E", PlaylistService.joinNames(listOf("A", "B", "C", "D", "E"), "and"))
    }
}
