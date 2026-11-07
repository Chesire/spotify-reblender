package dev.chesire.reblender.blend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlendEngineTest {

    private fun tracks(prefix: String, count: Int) =
        (1..count).map { Track(id = "$prefix$it", uri = "spotify:track:$prefix$it", name = "$prefix$it", artists = listOf("x")) }

    private fun track(id: String) = Track(id = id, uri = "spotify:track:$id", name = id, artists = listOf("x"))

    private fun List<BlendedTrack>.count(source: Source) = count { it.source == source }

    @Test
    fun `even size with no overlap splits evenly`() {
        val result = BlendEngine.blend(tracks("a", 50), tracks("b", 50), 20, SharedMode.CAP_THIRD)
        assertEquals(20, result.size)
        assertEquals(10, result.count(Source.A))
        assertEquals(10, result.count(Source.B))
        assertEquals(0, result.count(Source.SHARED))
    }

    @Test
    fun `picks each user's top tracks in rank order`() {
        val result = BlendEngine.blend(tracks("a", 50), tracks("b", 50), 6, SharedMode.CAP_THIRD)
        assertEquals(listOf("a1", "a2", "a3"), result.filter { it.source == Source.A }.map { it.track.id })
        assertEquals(listOf("b1", "b2", "b3"), result.filter { it.source == Source.B }.map { it.track.id })
    }

    @Test
    fun `odd size with no overlap drops one track`() {
        val result = BlendEngine.blend(tracks("a", 50), tracks("b", 50), 11, SharedMode.CAP_THIRD)
        assertEquals(10, result.size)
        assertEquals(5, result.count(Source.A))
        assertEquals(5, result.count(Source.B))
    }

    @Test
    fun `odd remainder goes to a spare shared track`() {
        // size 10, cap 3, 5 shared -> 3 shared leaves 7 (odd) -> 4 shared, 3 + 3.
        val shared = tracks("s", 5)
        val result = BlendEngine.blend(shared + tracks("a", 40), shared + tracks("b", 40), 10, SharedMode.CAP_THIRD)
        assertEquals(10, result.size)
        assertEquals(4, result.count(Source.SHARED))
        assertEquals(3, result.count(Source.A))
        assertEquals(3, result.count(Source.B))
    }

    @Test
    fun `cap third limits shared tracks when overlap is large`() {
        val shared = tracks("s", 30)
        val result = BlendEngine.blend(shared + tracks("a", 20), shared + tracks("b", 20), 30, SharedMode.CAP_THIRD)
        assertEquals(30, result.size)
        assertEquals(10, result.count(Source.SHARED))
        assertEquals(10, result.count(Source.A))
        assertEquals(10, result.count(Source.B))
    }

    @Test
    fun `fill mode lets shared tracks fill the playlist`() {
        val shared = tracks("s", 30)
        val result = BlendEngine.blend(shared + tracks("a", 20), shared + tracks("b", 20), 20, SharedMode.FILL)
        assertEquals(20, result.size)
        assertEquals(20, result.count(Source.SHARED))
    }

    @Test
    fun `fill mode with fewer shared than size splits the rest evenly`() {
        val shared = tracks("s", 4)
        val result = BlendEngine.blend(shared + tracks("a", 40), shared + tracks("b", 40), 20, SharedMode.FILL)
        assertEquals(20, result.size)
        assertEquals(4, result.count(Source.SHARED))
        assertEquals(8, result.count(Source.A))
        assertEquals(8, result.count(Source.B))
    }

    @Test
    fun `shared tracks are ordered by combined rank`() {
        // x is 1st for A and 3rd for B (sum 2); y is 2nd for A and 1st for B (sum 1).
        val a = listOf(track("x"), track("y"), track("a1"))
        val b = listOf(track("y"), track("b1"), track("x"))
        val result = BlendEngine.blend(a, b, 2, SharedMode.FILL)
        assertEquals(listOf("y", "x"), result.map { it.track.id })
    }

    @Test
    fun `when one user is short the other fills the gap`() {
        val result = BlendEngine.blend(tracks("a", 3), tracks("b", 50), 20, SharedMode.CAP_THIRD)
        assertEquals(20, result.size)
        assertEquals(3, result.count(Source.A))
        assertEquals(17, result.count(Source.B))
    }

    @Test
    fun `when both are short leftover shared tracks fill the gap beyond the cap`() {
        val shared = tracks("s", 10)
        // size 30, cap 10 -> 10 shared; 20 left -> 10 each, but each user only has 3 of their own.
        val result = BlendEngine.blend(shared + tracks("a", 3), shared + tracks("b", 3), 30, SharedMode.CAP_THIRD)
        assertEquals(16, result.size)
        assertEquals(10, result.count(Source.SHARED))
    }

    @Test
    fun `when both are short and shared is spare it is used`() {
        val shared = tracks("s", 20)
        // size 30, cap 10 -> 10 shared; 20 left -> 10 each, users have 4 own each -> gap 12 -> shared up to 20.
        val result = BlendEngine.blend(shared + tracks("a", 4), shared + tracks("b", 4), 30, SharedMode.CAP_THIRD)
        assertEquals(28, result.size)
        assertEquals(20, result.count(Source.SHARED))
        assertEquals(4, result.count(Source.A))
        assertEquals(4, result.count(Source.B))
    }

    @Test
    fun `full overlap in cap mode still returns shared tracks`() {
        val shared = tracks("s", 50)
        val result = BlendEngine.blend(shared, shared, 30, SharedMode.CAP_THIRD)
        assertEquals(30, result.size)
        assertTrue(result.all { it.source == Source.SHARED })
    }

    @Test
    fun `empty inputs give an empty playlist`() {
        assertEquals(0, BlendEngine.blend(emptyList(), emptyList(), 10, SharedMode.CAP_THIRD).size)
    }

    @Test
    fun `no duplicates even when inputs repeat`() {
        val a = tracks("a", 10) + tracks("a", 10)
        val result = BlendEngine.blend(a, tracks("b", 10), 20, SharedMode.CAP_THIRD)
        assertEquals(result.size, result.map { it.track.id }.toSet().size)
    }

    @Test
    fun `sources are interleaved rather than grouped`() {
        val shared = tracks("s", 5)
        val result = BlendEngine.blend(shared + tracks("a", 40), shared + tracks("b", 40), 10, SharedMode.CAP_THIRD)
        val pattern = result.joinToString("") { it.source.name.first().toString() }
        assertEquals("SABSABSABS", pattern)
    }

    @Test
    fun `no source has a long run in a large playlist`() {
        val result = BlendEngine.blend(tracks("a", 50), tracks("b", 50), 100, SharedMode.CAP_THIRD)
        val longestRun = result.fold(Pair(0, 0) to null as Source?) { (acc, prev), item ->
            val run = if (item.source == prev) acc.first + 1 else 1
            Pair(run, maxOf(acc.second, run)) to item.source
        }.first.second
        assertTrue(longestRun <= 1, "longest run was $longestRun")
    }

    @Test
    fun `size below two is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BlendEngine.blend(tracks("a", 5), tracks("b", 5), 1, SharedMode.CAP_THIRD)
        }
    }
}
