package dev.chesire.reblender.blend.models

import dev.chesire.reblender.blend.BlendEngine

/** What to do when the group shares more tracks than the shared budget allows. */
enum class SharedMode(val label: String, val option: String) {
    /** Shared songs are capped by group size: 1/3 for 2 people up to 4/5 for 5 (see [BlendEngine.sharedShare]). */
    CAPPED("Cap shared songs by group size", "Cap by group size"),

    /** Shared songs may fill the playlist. */
    FILL("Let shared songs fill it", "Let them fill it"),
}
