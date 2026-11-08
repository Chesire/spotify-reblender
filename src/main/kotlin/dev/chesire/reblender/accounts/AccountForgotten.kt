package dev.chesire.reblender.accounts

/** Published when a user asks the app to forget them, so other features can delete their own data about them. */
data class AccountForgotten(
    val spotifyId: String,
)
