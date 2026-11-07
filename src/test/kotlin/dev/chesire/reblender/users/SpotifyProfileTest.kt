package dev.chesire.reblender.users

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyProfileTest {

    @Test
    fun `picks the smallest image that is at least 64px`() {
        val images = listOf(
            mapOf("url" to "big", "height" to 300, "width" to 300),
            mapOf("url" to "small", "height" to 64, "width" to 64),
        )
        assertEquals("small", SpotifyProfile.avatarUrl(images))
    }

    @Test
    fun `falls back to any image when none have a height`() {
        assertEquals("only", SpotifyProfile.avatarUrl(listOf(mapOf("url" to "only", "height" to null))))
    }

    @Test
    fun `no images gives no avatar`() {
        assertNull(SpotifyProfile.avatarUrl(emptyList<Any>()))
        assertNull(SpotifyProfile.avatarUrl(null))
    }
}
