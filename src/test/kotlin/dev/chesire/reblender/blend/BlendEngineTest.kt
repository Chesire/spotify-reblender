package dev.chesire.reblender.blend

import dev.chesire.reblender.blend.models.BlendedTrack
import dev.chesire.reblender.blend.models.Participant
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlendEngineTest {
    private fun tracks(
        prefix: String,
        count: Int,
    ) = (1..count).map { Track(id = "$prefix$it", uri = "spotify:track:$prefix$it", name = "$prefix$it", artists = listOf("x")) }

    private fun track(id: String) = Track(id = id, uri = "spotify:track:$id", name = id, artists = listOf("x"))

    private fun p(
        id: String,
        tracks: List<Track>,
    ) = Participant(id, tracks)

    private fun blend2(
        a: List<Track>,
        b: List<Track>,
        size: Int,
        mode: SharedMode = SharedMode.CAPPED,
    ) = BlendEngine.blend(listOf(p("a", a), p("b", b)), size, mode)

    private fun List<BlendedTrack>.shared() = count { it.isShared }

    private fun List<BlendedTrack>.soloOf(id: String) = count { it.contributors == setOf(id) }

    /** S for shared, otherwise the contributor's id upper-cased. */
    private fun List<BlendedTrack>.pattern() = joinToString("") { if (it.isShared) "S" else it.contributors.single().uppercase() }

    // ---- Two people (original rules) ----

    @Test
    fun `two people with no overlap split evenly`() {
        val result = blend2(tracks("a", 50), tracks("b", 50), 20)
        assertEquals(20, result.size)
        assertEquals(10, result.soloOf("a"))
        assertEquals(10, result.soloOf("b"))
        assertEquals(0, result.shared())
    }

    @Test
    fun `picks each person's top tracks in rank order`() {
        val result = blend2(tracks("a", 50), tracks("b", 50), 6)
        assertEquals(listOf("a1", "a2", "a3"), result.filter { it.contributors == setOf("a") }.map { it.track.id })
        assertEquals(listOf("b1", "b2", "b3"), result.filter { it.contributors == setOf("b") }.map { it.track.id })
    }

    @Test
    fun `odd size with no overlap drops one track`() {
        val result = blend2(tracks("a", 50), tracks("b", 50), 11)
        assertEquals(10, result.size)
        assertEquals(5, result.soloOf("a"))
        assertEquals(5, result.soloOf("b"))
    }

    @Test
    fun `odd remainder goes to a spare shared track`() {
        val shared = tracks("s", 5)
        val result = blend2(shared + tracks("a", 40), shared + tracks("b", 40), 10)
        assertEquals(10, result.size)
        assertEquals(4, result.shared())
        assertEquals(3, result.soloOf("a"))
        assertEquals(3, result.soloOf("b"))
    }

    @Test
    fun `two people cap shared at a third`() {
        val shared = tracks("s", 30)
        val result = blend2(shared + tracks("a", 20), shared + tracks("b", 20), 30)
        assertEquals(30, result.size)
        assertEquals(10, result.shared())
        assertEquals(10, result.soloOf("a"))
        assertEquals(10, result.soloOf("b"))
    }

    @Test
    fun `fill mode lets shared tracks fill the playlist`() {
        val shared = tracks("s", 30)
        val result = blend2(shared + tracks("a", 20), shared + tracks("b", 20), 20, SharedMode.FILL)
        assertEquals(20, result.size)
        assertEquals(20, result.shared())
    }

    @Test
    fun `fill mode with fewer shared than size splits the rest evenly`() {
        val shared = tracks("s", 4)
        val result = blend2(shared + tracks("a", 40), shared + tracks("b", 40), 20, SharedMode.FILL)
        assertEquals(20, result.size)
        assertEquals(4, result.shared())
        assertEquals(8, result.soloOf("a"))
        assertEquals(8, result.soloOf("b"))
    }

    @Test
    fun `shared tracks are ordered by average rank`() {
        // x is 1st for A and 3rd for B (avg 1); y is 2nd for A and 1st for B (avg 0.5).
        val a = listOf(track("x"), track("y"), track("a1"))
        val b = listOf(track("y"), track("b1"), track("x"))
        val result = blend2(a, b, 2, SharedMode.FILL)
        assertEquals(listOf("y", "x"), result.map { it.track.id })
    }

    @Test
    fun `when one person is short everyone is capped at their count`() {
        val result = blend2(tracks("a", 3), tracks("b", 50), 20)
        assertEquals(6, result.size)
        assertEquals(3, result.soloOf("a"))
        assertEquals(3, result.soloOf("b"))
    }

    @Test
    fun `when one person is short spare shared songs fill the freed slots`() {
        val shared = tracks("s", 20)
        val result = blend2(shared + tracks("a", 3), shared + tracks("b", 50), 21)
        // Budget 7 shared; 14 left -> 7 each, capped at a's 3; the 8 freed slots go to spare shared songs.
        assertEquals(21, result.size)
        assertEquals(15, result.shared())
        assertEquals(3, result.soloOf("a"))
        assertEquals(3, result.soloOf("b"))
    }

    @Test
    fun `shared budget uses exact fractions`() {
        assertEquals(1, BlendEngine.sharedBudget(2, 3))
        assertEquals(16, BlendEngine.sharedBudget(2, 50))
        assertEquals(20, BlendEngine.sharedBudget(5, 25))
        assertEquals(0, BlendEngine.sharedBudget(1, 50))
    }

    @Test
    fun `when both are short leftover shared tracks fill the gap`() {
        val shared = tracks("s", 20)
        val result = blend2(shared + tracks("a", 4), shared + tracks("b", 4), 30)
        assertEquals(28, result.size)
        assertEquals(20, result.shared())
    }

    @Test
    fun `full overlap in capped mode still returns shared tracks`() {
        val shared = tracks("s", 50)
        val result = blend2(shared, shared, 30)
        assertEquals(30, result.size)
        assertTrue(result.all { it.isShared })
    }

    @Test
    fun `sources are interleaved rather than grouped`() {
        val shared = tracks("s", 5)
        val result = blend2(shared + tracks("a", 40), shared + tracks("b", 40), 10)
        assertEquals("SABSABSABS", result.pattern())
    }

    @Test
    fun `no person has a long run in a large playlist`() {
        val result = blend2(tracks("a", 50), tracks("b", 50), 100)
        assertTrue(result.zipWithNext().none { (x, y) -> x.contributors == y.contributors })
    }

    // ---- One person (local testing) ----

    @Test
    fun `single person gets their own top tracks in order`() {
        val result = BlendEngine.blend(listOf(p("a", tracks("a", 50))), 10, SharedMode.CAPPED)
        assertEquals((1..10).map { "a$it" }, result.map { it.track.id })
        assertTrue(result.all { it.contributors == setOf("a") })
    }

    @Test
    fun `single person with fewer tracks than size gets them all`() {
        val result = BlendEngine.blend(listOf(p("a", tracks("a", 5))), 10, SharedMode.FILL)
        assertEquals(5, result.size)
    }

    // ---- Three to five people ----

    @Test
    fun `shared budget grows with group size`() {
        assertEquals(1 to 3, BlendEngine.sharedShare(2))
        assertEquals(1 to 2, BlendEngine.sharedShare(3))
        assertEquals(2 to 3, BlendEngine.sharedShare(4))
        assertEquals(4 to 5, BlendEngine.sharedShare(5))
    }

    @Test
    fun `three people prefer songs shared by everyone, then by fewer`() {
        val all3 = tracks("t", 6)
        val ab = tracks("ab", 20)
        val people = listOf(
            p("a", all3 + ab + tracks("a", 30)),
            p("b", all3 + ab + tracks("b", 30)),
            p("c", all3 + tracks("c", 30)),
        )
        val result = BlendEngine.blend(people, 30, SharedMode.CAPPED)
        assertEquals(30, result.size)
        assertEquals(15, result.shared()) // 50% of 30
        val sharedInOrder = result.filter { it.isShared }
        assertTrue(sharedInOrder.take(6).all { it.contributors.size == 3 })
        assertTrue(sharedInOrder.drop(6).all { it.contributors == setOf("a", "b") })
        assertEquals(5, result.soloOf("a"))
        assertEquals(5, result.soloOf("b"))
        assertEquals(5, result.soloOf("c"))
    }

    @Test
    fun `four people cap shared at two thirds`() {
        val shared = tracks("s", 40)
        val people = (1..4).map { p("u$it", shared + tracks("u${it}_", 30)) }
        val result = BlendEngine.blend(people, 12, SharedMode.CAPPED)
        assertEquals(12, result.size)
        assertEquals(8, result.shared())
        people.forEach { assertEquals(1, result.soloOf(it.id)) }
    }

    @Test
    fun `five people with few shared songs fall back to solo picks`() {
        val shared = tracks("s", 3)
        val people = (1..5).map { p("u$it", shared + tracks("u${it}_", 30)) }
        val result = BlendEngine.blend(people, 25, SharedMode.CAPPED)
        // budget 20 but only 3 shared; 22 left, 2 don't divide by 5 and no shared remain -> 4 each.
        assertEquals(23, result.size)
        assertEquals(3, result.shared())
        people.forEach { assertEquals(4, result.soloOf(it.id)) }
    }

    @Test
    fun `five people use lower tiers when not enough songs are shared by all`() {
        val all5 = tracks("all", 4)
        val trio = tracks("trio", 20)
        val people = (1..5).map { i ->
            p("u$i", all5 + (if (i <= 3) trio else emptyList()) + tracks("u${i}_", 30))
        }
        val result = BlendEngine.blend(people, 20, SharedMode.CAPPED)
        val shared = result.filter { it.isShared }
        // Budget is 16 (80%); the 4 slots left can't split 5 ways, so they go to spare shared songs too.
        assertEquals(20, result.size)
        assertEquals(20, shared.size)
        assertTrue(shared.take(4).all { it.contributors.size == 5 })
        assertTrue(shared.drop(4).all { it.contributors.size == 3 })
    }

    @Test
    fun `five people with a larger playlist mix shared and solo`() {
        val all5 = tracks("all", 4)
        val trio = tracks("trio", 20)
        val people = (1..5).map { i ->
            p("u$i", all5 + (if (i <= 3) trio else emptyList()) + tracks("u${i}_", 30))
        }
        val result = BlendEngine.blend(people, 30, SharedMode.CAPPED)
        // Budget 24 shared and exactly 24 exist; 6 left -> 1 each, and the uneven slot is dropped (no spare shared).
        assertEquals(29, result.size)
        assertEquals(24, result.shared())
        people.forEach { assertEquals(1, result.soloOf(it.id)) }
    }

    @Test
    fun `shared tracks list every contributor`() {
        val shared = tracks("s", 2)
        val people = listOf(p("a", shared + tracks("a", 5)), p("b", shared), p("c", shared.take(1)))
        val result = BlendEngine.blend(people, 3, SharedMode.FILL)
        assertEquals(setOf("a", "b", "c"), result.first { it.track.id == "s1" }.contributors)
        assertEquals(setOf("a", "b"), result.first { it.track.id == "s2" }.contributors)
    }

    // ---- Validation ----

    @Test
    fun `no duplicates even when inputs repeat`() {
        val result = blend2(tracks("a", 10) + tracks("a", 10), tracks("b", 10), 20)
        assertEquals(result.size, result.map { it.track.id }.toSet().size)
    }

    @Test
    fun `empty inputs give an empty playlist`() {
        assertEquals(0, blend2(emptyList(), emptyList(), 10).size)
    }

    @Test
    fun `size range is one to fifty songs per person`() {
        assertEquals(1..50, BlendEngine.sizeRange(1))
        assertEquals(2..100, BlendEngine.sizeRange(2))
        assertEquals(5..250, BlendEngine.sizeRange(5))
    }

    @Test
    fun `size below the number of people is rejected`() {
        assertFailsWith<IllegalArgumentException> { blend2(tracks("a", 5), tracks("b", 5), 1) }
    }

    @Test
    fun `size above fifty per person is rejected`() {
        assertFailsWith<IllegalArgumentException> { blend2(tracks("a", 50), tracks("b", 50), 101) }
        assertFailsWith<IllegalArgumentException> {
            BlendEngine.blend(listOf(p("a", tracks("a", 50))), 51, SharedMode.CAPPED)
        }
    }

    @Test
    fun `a single song solo playlist is allowed`() {
        val result = BlendEngine.blend(listOf(p("a", tracks("a", 50))), 1, SharedMode.CAPPED)
        assertEquals(listOf("a1"), result.map { it.track.id })
    }

    @Test
    fun `five people can fill the maximum playlist`() {
        val people = (1..5).map { p("u$it", tracks("u${it}_", 50)) }
        val result = BlendEngine.blend(people, 250, SharedMode.CAPPED)
        assertEquals(250, result.size)
        people.forEach { assertEquals(50, result.soloOf(it.id)) }
    }

    @Test
    fun `more than five people is rejected`() {
        val people = (1..6).map { p("u$it", tracks("u$it", 5)) }
        assertFailsWith<IllegalArgumentException> { BlendEngine.blend(people, 10, SharedMode.CAPPED) }
    }

    @Test
    fun `the same person twice is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BlendEngine.blend(listOf(p("a", tracks("a", 5)), p("a", tracks("a", 5))), 10, SharedMode.CAPPED)
        }
    }
}
