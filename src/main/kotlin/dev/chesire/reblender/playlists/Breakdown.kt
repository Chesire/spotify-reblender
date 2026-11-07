package dev.chesire.reblender.playlists

import dev.chesire.reblender.users.AppUser

/** A person in a blend as shown on the breakdown page. */
data class MemberView(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    /** Shown when there's no avatar, e.g. "CH" or "JS". */
    val initials: String,
    /** Distinct per member within a blend, used for initials avatars and the balance bar. */
    val colour: String,
    val isOwner: Boolean,
    /** Songs that came only from this person. */
    val ownCount: Int,
    /** Shared songs this person is part of. */
    val sharedCount: Int,
)

/** One song row on the breakdown page. */
data class TrackView(
    val position: Int,
    val trackId: String,
    val name: String,
    val artists: String,
    val contributors: List<MemberView>,
    /** Album cover, or null for songs stored before covers were recorded. */
    val imageUrl: String? = null,
) {
    val isShared: Boolean get() = contributors.size > 1

    val trackUrl: String get() = "https://open.spotify.com/track/$trackId"

    val contributorNames: List<String> get() = contributors.map { it.name }
}

/** Who each song in a blend came from, plus per-person totals to show the balance. */
data class Breakdown(
    val blend: Blend,
    val members: List<MemberView>,
    val tracks: List<TrackView>,
) {
    val sharedTotal: Int get() = tracks.count { it.isShared }

    /** Percentage of the playlist that is shared songs, for the balance bar. */
    val sharedPercent: Int get() = percent(sharedTotal)

    fun ownPercent(member: MemberView): Int = percent(member.ownCount)

    private fun percent(count: Int) = if (tracks.isEmpty()) 0 else Math.round(count * 100.0 / tracks.size).toInt()

    companion object {
        /** Colours that read well on the dark background and are easy to tell apart. */
        val PALETTE = listOf("#1db954", "#4f9cf9", "#e3a008", "#e5534b", "#b07cf7")

        fun of(blend: Blend, users: List<AppUser>, tracks: List<BlendTrack>): Breakdown {
            val usersById = users.associateBy { it.spotifyId }
            val baseMembers = blend.memberIds.mapIndexed { index, id ->
                val user = usersById[id]
                val name = user?.displayName ?: id
                MemberView(
                    id = id,
                    name = name,
                    avatarUrl = user?.avatarUrl,
                    initials = initials(name),
                    colour = PALETTE[index % PALETTE.size],
                    isOwner = id == blend.ownerId,
                    ownCount = tracks.count { it.contributors == listOf(id) },
                    sharedCount = tracks.count { it.contributors.size > 1 && id in it.contributors },
                )
            }
            val membersById = baseMembers.associateBy { it.id }
            val unknown = { id: String ->
                MemberView(id, "Former member", null, "?", "#6b6b6b", false, 0, 0)
            }
            val trackViews = tracks.mapIndexed { index, track ->
                TrackView(
                    position = index + 1,
                    trackId = track.trackId,
                    name = track.name,
                    artists = track.artists.joinToString(", "),
                    imageUrl = track.imageUrl,
                    // Show contributors in member order so stacks look consistent.
                    contributors = track.contributors
                        .map { membersById[it] ?: unknown(it) }
                        .sortedBy { member -> blend.memberIds.indexOf(member.id).let { if (it < 0) Int.MAX_VALUE else it } },
                )
            }
            return Breakdown(blend, baseMembers, trackViews)
        }

        /** "Chesire" -> "CH", "Jane Smith" -> "JS", "" -> "?". */
        fun initials(name: String): String {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> "?"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> (words[0].take(1) + words[1].take(1)).uppercase()
            }
        }
    }
}
