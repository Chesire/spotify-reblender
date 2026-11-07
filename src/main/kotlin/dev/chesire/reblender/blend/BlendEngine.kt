package dev.chesire.reblender.blend

/**
 * Builds a balanced two-person playlist from each user's ranked top tracks.
 *
 * Rules, in order:
 * 1. Shared tracks (in both lists) are ranked by combined position (sum of both ranks, then best single rank).
 * 2. Shared slots: [SharedMode.CAP_THIRD] allows at most `size / 3`; [SharedMode.FILL] allows up to `size`.
 * 3. The remaining slots are split evenly between A and B. If that leaves an odd slot, it goes to one more
 *    shared track if one is left; otherwise the playlist drops a track so the split stays even.
 * 4. If one user runs out of their own tracks, the other user fills the gap.
 *    If both run out, leftover shared tracks fill whatever is left (even beyond the cap).
 * 5. The result is interleaved so each source is spread evenly through the playlist.
 *
 * Pure and deterministic: same inputs always give the same playlist.
 */
object BlendEngine {

    const val MIN_SIZE = 2

    fun blend(
        tracksA: List<Track>,
        tracksB: List<Track>,
        size: Int,
        sharedMode: SharedMode,
    ): List<BlendedTrack> {
        require(size >= MIN_SIZE) { "Playlist size must be at least $MIN_SIZE" }

        val listA = tracksA.distinctBy { it.id }
        val listB = tracksB.distinctBy { it.id }
        val rankA = listA.withIndex().associate { (index, track) -> track.id to index }
        val rankB = listB.withIndex().associate { (index, track) -> track.id to index }

        val shared = listA
            .filter { it.id in rankB }
            .sortedWith(
                compareBy<Track>(
                    { rankA.getValue(it.id) + rankB.getValue(it.id) },
                    { minOf(rankA.getValue(it.id), rankB.getValue(it.id)) },
                ),
            )
        val sharedIds = shared.mapTo(HashSet()) { it.id }
        val onlyA = listA.filter { it.id !in sharedIds }
        val onlyB = listB.filter { it.id !in sharedIds }

        // Steps 2 and 3: shared slots, then an even split of what's left.
        val cap = when (sharedMode) {
            SharedMode.CAP_THIRD -> size / 3
            SharedMode.FILL -> size
        }
        var sharedCount = minOf(shared.size, cap)
        var remaining = size - sharedCount
        if (remaining % 2 == 1) {
            if (shared.size > sharedCount) sharedCount++
            remaining--
        }
        val half = remaining / 2

        // Step 4: shortfall handling.
        val shortA = half - minOf(half, onlyA.size)
        val shortB = half - minOf(half, onlyB.size)
        val takeA = minOf(onlyA.size, half + shortB)
        val takeB = minOf(onlyB.size, half + shortA)
        val gap = remaining - takeA - takeB
        if (gap > 0) sharedCount = minOf(shared.size, sharedCount + gap)

        // Step 5.
        return interleave(
            listOf(
                Source.SHARED to shared.take(sharedCount),
                Source.A to onlyA.take(takeA),
                Source.B to onlyB.take(takeB),
            ),
        )
    }

    /**
     * Spreads each source evenly: each slot goes to the source furthest behind its fair share
     * (lowest `(taken + 0.5) / quota`). Ties go to SHARED, then A, then B.
     */
    private fun interleave(sources: List<Pair<Source, List<Track>>>): List<BlendedTrack> {
        val taken = IntArray(sources.size)
        val total = sources.sumOf { it.second.size }
        val result = ArrayList<BlendedTrack>(total)
        repeat(total) {
            var best = -1
            var bestScore = Double.MAX_VALUE
            sources.forEachIndexed { index, (_, tracks) ->
                if (taken[index] < tracks.size) {
                    val score = (taken[index] + 0.5) / tracks.size
                    if (score < bestScore) {
                        bestScore = score
                        best = index
                    }
                }
            }
            val (source, tracks) = sources[best]
            result += BlendedTrack(tracks[taken[best]], source)
            taken[best]++
        }
        return result
    }
}
