package dev.chesire.reblender.users.models

data class AppUser(
    val spotifyId: String,
    val displayName: String,
    /** Spotify profile picture, if the user has one. */
    val avatarUrl: String? = null,
    /** Whether others may include this user (and so read their top tracks) in blends. */
    val allowBlending: Boolean = true,
)
