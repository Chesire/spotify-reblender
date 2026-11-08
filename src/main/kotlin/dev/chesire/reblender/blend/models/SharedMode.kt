package dev.chesire.reblender.blend.models

import dev.chesire.reblender.blend.BlendEngine

/** What to do when the group shares more tracks than the shared budget allows. */
enum class SharedMode {
    /** Shared songs are capped by group size: 1/3 for 2 people up to 4/5 for 5 (see [BlendEngine.sharedShare]). */
    CAPPED,

    /** Shared songs may fill the playlist. */
    FILL,
}
