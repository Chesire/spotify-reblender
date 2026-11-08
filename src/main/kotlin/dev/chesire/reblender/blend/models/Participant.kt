package dev.chesire.reblender.blend.models

/** One person in a blend, with their ranked top tracks (best first). */
data class Participant(val id: String, val tracks: List<Track>)
