package dev.chesire.reblender.accounts.web

import dev.chesire.reblender.platform.security.LoginFailure

/** What to tell someone whose Spotify login failed. */
object LoginMessages {
    private const val NOT_ALLOWED =
        "Spotify won't let this app use your account yet. Reblender is invite-only on Spotify's side: ask the person who runs it " +
            "to add your Spotify account's email in their Spotify developer dashboard, then log in again."
    private const val CANCELLED = "Login was cancelled on Spotify."
    private const val FAILED = "Logging in with Spotify didn't work. Try again."

    fun forCode(code: String?): String? = when (LoginFailure.entries.firstOrNull { it.code == code }) {
        LoginFailure.NOT_ALLOWED -> NOT_ALLOWED
        LoginFailure.CANCELLED -> CANCELLED
        LoginFailure.FAILED -> FAILED
        null -> null
    }
}
