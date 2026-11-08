package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.accounts.models.AppUser
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendTrack
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreakdownTest {
    private val blend = Blend(
        playlistId = "p1",
        playlistName = "A & B - Recent favourites",
        playlistUrl = "https://open.spotify.com/playlist/p1",
        ownerId = "a",
        memberIds = listOf("a", "b"),
        timeRange = TimeRange.RECENT,
        size = 4,
        sharedMode = SharedMode.CAPPED,
        trackCount = 4,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val users = listOf(AppUser("a", "Alice Smith", "https://img/a"), AppUser("b", "Bob"))

    private fun track(
        id: String,
        vararg contributors: String,
    ) = BlendTrack(id, "spotify:track:$id", "Song $id", listOf("Artist $id", "Feat"), contributors.toList())

    private val tracks = listOf(track("s1", "b", "a"), track("a1", "a"), track("b1", "b"), track("a2", "a"))

    @Test
    fun `counts own and shared songs per member`() {
        val breakdown = Breakdown.of(blend, users, tracks)
        val (alice, bob) = breakdown.members
        assertEquals(2, alice.ownCount)
        assertEquals(1, alice.sharedCount)
        assertEquals(1, bob.ownCount)
        assertEquals(1, bob.sharedCount)
        assertEquals(1, breakdown.sharedTotal)
        assertEquals(25, breakdown.sharedPercent)
        assertEquals(50, breakdown.ownPercent(alice))
    }

    @Test
    fun `members keep blend order, owner flag, avatar and distinct colours`() {
        val breakdown = Breakdown.of(blend, users, tracks)
        assertEquals(listOf("a", "b"), breakdown.members.map { it.id })
        assertTrue(breakdown.members[0].isOwner)
        assertFalse(breakdown.members[1].isOwner)
        assertEquals("https://img/a", breakdown.members[0].avatarUrl)
        assertEquals(
            2,
            breakdown.members
                .map { it.colour }
                .toSet()
                .size,
        )
    }

    @Test
    fun `tracks are numbered with contributors in member order`() {
        val first = Breakdown.of(blend, users, tracks).tracks.first()
        assertEquals(1, first.position)
        assertEquals("Artist s1, Feat", first.artists)
        assertEquals(listOf("a", "b"), first.contributors.map { it.id })
        assertTrue(first.isShared)
    }

    @Test
    fun `unknown contributors are shown as former members`() {
        val breakdown = Breakdown.of(blend, users, listOf(track("x", "a", "gone")))
        assertEquals(
            "Former member",
            breakdown.tracks
                .single()
                .contributors
                .last()
                .name,
        )
    }

    @Test
    fun `initials from names`() {
        assertEquals("AS", Breakdown.initials("Alice Smith"))
        assertEquals("CH", Breakdown.initials("Chesire"))
        assertEquals("?", Breakdown.initials("  "))
    }

    @Test
    fun `empty playlist has zero percentages`() {
        assertEquals(0, Breakdown.of(blend, users, emptyList()).sharedPercent)
    }
}
