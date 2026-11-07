package dev.chesire.reblender.spotify

/** Picks an image URL from Spotify's `images` arrays, which come in several sizes in no guaranteed order. */
object SpotifyImages {

    /** Smallest image at least [minHeight] px tall (they're shown small), else the first one, else null. */
    fun pick(images: List<Pair<String, Int?>>, minHeight: Int = 64): String? =
        images.filter { (it.second ?: 0) >= minHeight }.minByOrNull { it.second ?: 0 }?.first
            ?: images.firstOrNull()?.first
}
