package dev.chesire.reblender.platform.security

import java.net.URI

/**
 * Only https URLs ever reach a page's href/src. Escaping alone doesn't stop a `javascript:` or `data:` URL,
 * so anything that isn't a well-formed https URL with a host is dropped.
 */
object SafeUrls {
    private const val MAX_LENGTH = 1000

    fun httpsOrNull(url: String?): String? {
        if (url.isNullOrBlank() || url.length > MAX_LENGTH) return null
        val uri = try {
                URI(url.trim())
            } catch (e: java.net.URISyntaxException) {
                return null
            }
        return if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) uri.toString() else null
    }
}
