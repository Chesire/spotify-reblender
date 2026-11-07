package dev.chesire.reblender.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyImagesTest {

    @Test
    fun `picks the smallest cover that is at least 64px`() {
        assertEquals("64", SpotifyImages.pick(listOf("640" to 640, "64" to 64, "300" to 300)))
    }

    @Test
    fun `falls back to the first image when none are big enough or sized`() {
        assertEquals("a", SpotifyImages.pick(listOf("a" to null, "b" to 32)))
    }

    @Test
    fun `no images gives null`() {
        assertNull(SpotifyImages.pick(emptyList()))
    }
}
