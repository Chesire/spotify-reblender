package dev.chesire.reblender.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyImagesTest {

    private fun url(name: String) = "https://i.scdn.co/image/$name"

    @Test
    fun `picks the smallest cover that is at least 64px`() {
        assertEquals(url("64"), SpotifyImages.pick(listOf(url("640") to 640, url("64") to 64, url("300") to 300)))
    }

    @Test
    fun `falls back to the first image when none are big enough or sized`() {
        assertEquals(url("a"), SpotifyImages.pick(listOf(url("a") to null, url("b") to 32)))
    }

    @Test
    fun `no images gives null`() {
        assertNull(SpotifyImages.pick(emptyList()))
    }

    @Test
    fun `only https images are ever picked`() {
        assertEquals(
            url("ok"),
            SpotifyImages.pick(listOf("javascript:alert(1)" to 640, "http://i.scdn.co/image/x" to 64, url("ok") to 300)),
        )
        assertNull(SpotifyImages.pick(listOf("data:image/svg+xml,<svg/>" to 64)))
    }
}
