package dev.chesire.reblender.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeUrlsAndIdsTest {
    @Test
    fun `https urls pass through`() {
        assertEquals("https://i.scdn.co/image/ab67616d", SafeUrls.httpsOrNull("https://i.scdn.co/image/ab67616d"))
        assertEquals("https://open.spotify.com/playlist/x", SafeUrls.httpsOrNull("  https://open.spotify.com/playlist/x "))
    }

    @Test
    fun `anything that is not https with a host is dropped`() {
        assertNull(SafeUrls.httpsOrNull("javascript:alert(1)"))
        assertNull(SafeUrls.httpsOrNull("JaVaScRiPt:alert(1)"))
        assertNull(SafeUrls.httpsOrNull("data:image/svg+xml;base64,PHN2Zz4="))
        assertNull(SafeUrls.httpsOrNull("http://i.scdn.co/image/x"))
        assertNull(SafeUrls.httpsOrNull("//evil.example/x"))
        assertNull(SafeUrls.httpsOrNull("https:///no-host"))
        assertNull(SafeUrls.httpsOrNull("https://exa mple.com"))
        assertNull(SafeUrls.httpsOrNull(""))
        assertNull(SafeUrls.httpsOrNull(null))
        assertNull(SafeUrls.httpsOrNull("https://a.co/" + "x".repeat(1000)))
    }

    @Test
    fun `playlist and track ids are 22 base62 characters`() {
        assertTrue(SpotifyIds.isPlaylistId("37i9dQZF1DXcBWIGoYBM5M"))
        assertTrue(SpotifyIds.isTrackId("4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(SpotifyIds.isPlaylistId("37i9dQZF1DXcBWIGoYBM5"))
        assertFalse(SpotifyIds.isPlaylistId("../../me/library"))
        assertFalse(SpotifyIds.isPlaylistId("37i9dQZF1DXcBWIGoYBM5%"))
        assertFalse(SpotifyIds.isPlaylistId(null))
    }

    @Test
    fun `user ids allow legacy usernames but nothing path-like`() {
        assertTrue(SpotifyIds.isUserId("chesire"))
        assertTrue(SpotifyIds.isUserId("some.user_name-1"))
        assertTrue(SpotifyIds.isUserId("31l4abcdefghijklmnopqrstu"))
        assertFalse(SpotifyIds.isUserId("a/b"))
        assertFalse(SpotifyIds.isUserId("a b"))
        assertFalse(SpotifyIds.isUserId(""))
        assertFalse(SpotifyIds.isUserId("x".repeat(129)))
    }
}
