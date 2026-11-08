package dev.chesire.reblender.playlists.models

import dev.chesire.reblender.accounts.models.AppUser

/** A blend with its songs and its members' profiles, in member order. */
data class BlendDetails(
    val blend: Blend,
    val members: List<AppUser>,
    val tracks: List<BlendTrack>,
)
