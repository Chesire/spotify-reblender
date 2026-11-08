package dev.chesire.reblender.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyProfileTest {
    @Test
    fun `picks the smallest image that is at least 64px`() {
        val images =
            listOf(
                mapOf("url" to "https://i.scdn.co/image/big", "height" to 300, "width" to 300),
                mapOf("url" to "https://i.scdn.co/image/small", "height" to 64, "width" to 64),
            )
        assertEquals("https://i.scdn.co/image/small", SpotifyProfile.avatarUrl(images))
    }

    @Test
    fun `falls back to any image when none have a height`() {
        assertEquals(
            "https://i.scdn.co/image/only",
            SpotifyProfile.avatarUrl(
                listOf(
                    mapOf(
                        "url" to "https://i.scdn.co/image/only",
                        "height" to null,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `non-https pictures are ignored`() {
        assertNull(SpotifyProfile.avatarUrl(listOf(mapOf("url" to "javascript:alert(1)", "height" to 64))))
    }

    @Test
    fun `no images gives no avatar`() {
        assertNull(SpotifyProfile.avatarUrl(emptyList<Any>()))
        assertNull(SpotifyProfile.avatarUrl(null))
    }
}
