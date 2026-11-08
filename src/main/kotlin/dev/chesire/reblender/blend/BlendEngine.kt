package dev.chesire.reblender.blend

import dev.chesire.reblender.blend.models.BlendedTrack
import dev.chesire.reblender.blend.models.Participant
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.Track
import kotlin.math.floor

/**
 * Builds a balanced playlist for 1–5 people from each person's ranked top tracks.
 *
 * Rules, in order:
 * 1. **Shared songs** are songs in two or more people's lists. They're ranked by how many people share them
 *    (everyone first, then one fewer, down to pairs), then by average rank, then by best single rank.
 * 2. **Shared budget**: [SharedMode.CAPPED] allows `floor(size × share)` where share grows with group size
 *    (1/3 for 2 people, 1/2 for 3, 2/3 for 4, 4/5 for 5; a single person has no shared songs).
 *    [SharedMode.FILL] allows shared songs to take every slot. Either way, only as many as actually exist.
 * 3. **Even split**: the remaining slots are divided equally between everyone, using songs only they have.
 *    Slots that don't divide evenly go to more shared songs if any are left; otherwise the playlist is
 *    shorter so the split stays equal.
 * 4. **Shortfall**: if someone runs out of their own songs, the others fill their slots (round-robin).
 *    If everyone runs out, leftover shared songs fill in (even beyond the cap).
 * 5. **Interleave**: each source (shared, then each person) is spread evenly through the playlist.
 *
 * Pure and deterministic: same inputs always give the same playlist.
 */
object BlendEngine {
    const val MAX_PARTICIPANTS = 5

    /** Spotify returns at most 50 top tracks per person, so a playlist can hold at most 50 per person. */
    const val TRACKS_PER_PERSON = 50

    /** Allowed playlist sizes for a group: at least one song per person, at most [TRACKS_PER_PERSON] each. */
    fun sizeRange(participantCount: Int): IntRange = participantCount..participantCount * TRACKS_PER_PERSON

    /** Target fraction of the playlist that shared songs may take, by group size. */
    fun sharedShare(participantCount: Int): Double =
        when (participantCount) {
            1 -> 0.0
            2 -> 1.0 / 3
            3 -> 1.0 / 2
            4 -> 2.0 / 3
            else -> 4.0 / 5
        }

    fun blend(
        participants: List<Participant>,
        size: Int,
        sharedMode: SharedMode,
    ): List<BlendedTrack> {
        require(participants.size in 1..MAX_PARTICIPANTS) { "A blend needs 1 to $MAX_PARTICIPANTS people" }
        val allowed = sizeRange(participants.size)
        require(size in allowed) {
            "With ${participants.size} ${if (participants.size == 1) "person" else "people"} the playlist must have " +
                "between ${allowed.first} and ${allowed.last} songs"
        }
        require(participants.map { it.id }.toSet().size == participants.size) { "Each person can only be in a blend once" }

        val people = participants.map { it.copy(tracks = it.tracks.distinctBy(Track::id)) }
        val n = people.size

        // Who has each track, and at what rank.
        val trackById = LinkedHashMap<String, Track>()
        val holders = HashMap<String, MutableList<Pair<Int, Int>>>() // trackId -> (personIndex, rank)
        people.forEachIndexed { personIndex, person ->
            person.tracks.forEachIndexed { rank, track ->
                trackById.putIfAbsent(track.id, track)
                holders.getOrPut(track.id) { mutableListOf() } += personIndex to rank
            }
        }

        // Step 1.
        val shared =
            holders.filterValues { it.size >= 2 }.keys.sortedWith(
                compareByDescending<String> { holders.getValue(it).size }
                    .thenBy { holders.getValue(it).map { (_, rank) -> rank }.average() }
                    .thenBy { holders.getValue(it).minOf { (_, rank) -> rank } }
                    .thenBy { it },
            )
        val solo = people.map { person -> person.tracks.filter { holders.getValue(it.id).size == 1 } }

        // Steps 2 and 3.
        val budget =
            when {
                n == 1 -> 0
                sharedMode == SharedMode.FILL -> size
                else -> floor(size * sharedShare(n)).toInt()
            }
        var sharedCount = minOf(shared.size, budget)
        var remaining = size - sharedCount
        val uneven = remaining % n
        if (uneven > 0) {
            sharedCount += minOf(uneven, shared.size - sharedCount)
            remaining -= uneven
        }
        val quota = remaining / n

        // Step 4.
        val take = IntArray(n) { minOf(quota, solo[it].size) }
        var deficit = n * quota - take.sum()
        while (deficit > 0) {
            var filled = false
            for (i in 0 until n) {
                if (deficit > 0 && take[i] < solo[i].size) {
                    take[i]++
                    deficit--
                    filled = true
                }
            }
            if (!filled) break
        }
        if (deficit > 0) sharedCount = minOf(shared.size, sharedCount + deficit)

        // Step 5.
        val sharedPicks =
            shared.take(sharedCount).map { id ->
                BlendedTrack(trackById.getValue(id), holders.getValue(id).mapTo(LinkedHashSet()) { people[it.first].id })
            }
        val soloPicks =
            people.mapIndexed { i, person ->
                solo[i].take(take[i]).map { BlendedTrack(it, setOf(person.id)) }
            }
        return interleave(listOf(sharedPicks) + soloPicks)
    }

    /**
     * Spreads each source evenly: each slot goes to the source furthest behind its fair share
     * (lowest `(taken + 0.5) / quota`). Ties go to the earlier source (shared first, then people in order).
     */
    private fun interleave(sources: List<List<BlendedTrack>>): List<BlendedTrack> {
        val taken = IntArray(sources.size)
        val total = sources.sumOf { it.size }
        val result = ArrayList<BlendedTrack>(total)
        repeat(total) {
            var best = -1
            var bestScore = Double.MAX_VALUE
            sources.forEachIndexed { index, tracks ->
                if (taken[index] < tracks.size) {
                    val score = (taken[index] + 0.5) / tracks.size
                    if (score < bestScore) {
                        bestScore = score
                        best = index
                    }
                }
            }
            result += sources[best][taken[best]]
            taken[best]++
        }
        return result
    }
}
