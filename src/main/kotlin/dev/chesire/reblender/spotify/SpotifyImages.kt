package dev.chesire.reblender.spotify

import dev.chesire.reblender.security.SafeUrls

/** Picks an image URL from Spotify's `images` arrays, which come in several sizes in no guaranteed order. */
object SpotifyImages {
    /**
     * Smallest image at least [minHeight] px tall (they're shown small), else the first one, else null.
     * Only https URLs are considered, so nothing from the API can turn into a javascript:, data: or http: link.
     */
    fun pick(
        images: List<Pair<String, Int?>>,
        minHeight: Int = 64,
    ): String? =
        images
            .mapNotNull { (url, height) -> SafeUrls.httpsOrNull(url)?.let { it to height } }
            .let { safe -> pickFrom(safe, minHeight) }

    private fun pickFrom(
        images: List<Pair<String, Int?>>,
        minHeight: Int,
    ): String? =
        images.filter { (it.second ?: 0) >= minHeight }.minByOrNull { it.second ?: 0 }?.first
            ?: images.firstOrNull()?.first
}
