package dev.chesire.reblender.platform.logging

/**
 * Everything that goes into a request log passes through here. The rule is "allowlist, not denylist":
 * a query parameter's value is only logged if its name is known to be harmless, so a token, OAuth `code`,
 * `state`, PKCE value or anything new is masked by default. Headers and request bodies are never logged at all.
 */
object LogRedaction {
    const val MASK = "***"

    /** Query parameters whose values are safe to show (Spotify API paging/filters and this app's own flags). */
    private val SAFE_PARAMS =
        setOf(
            "time_range",
            "limit",
            "offset",
            "uris",
            "market",
            "fields",
            "expired",
            "error",
            "returnTo",
            "continue",
            "logout",
        )

    /** Keys whose values must never appear, if they show up in a JSON error body. */
    private val SECRET_JSON_KEY =
        Regex(
            "\"(access_token|refresh_token|id_token|token|client_secret|code|code_verifier|assertion)\"\\s*:\\s*\"[^\"]*\"",
            RegexOption.IGNORE_CASE,
        )

    /** Anything shaped like a bearer credential, wherever it appears. */
    private val BEARER = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")

    private const val MAX_BODY_CHARS = 500

    /** `a=1&code=xyz` -> `a=1&code=***`. Returns null for no query. */
    fun query(rawQuery: String?): String? {
        if (rawQuery.isNullOrEmpty()) return null
        return rawQuery.split('&').joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            when {
                '=' !in pair -> name
                name in SAFE_PARAMS -> pair
                else -> "$name=$MASK"
            }
        }
    }

    /** Path plus redacted query, e.g. `/login/oauth2/code/spotify?code=***&state=***`. */
    fun target(
        path: String,
        rawQuery: String?,
    ): String = query(rawQuery)?.let { "$path?$it" } ?: path

    /** An error body, shortened, with token-like values masked. Only ever called for error responses. */
    fun errorBody(body: String): String {
        val masked = BEARER.replace(SECRET_JSON_KEY.replace(body) { m -> "\"${m.groupValues[1]}\":\"$MASK\"" }, "Bearer $MASK")
        val oneLine = masked.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length > MAX_BODY_CHARS) oneLine.take(MAX_BODY_CHARS) + "…" else oneLine
    }
}
