package dev.chesire.reblender.playlists

/** A blend can't be built because [spotifyId] has turned off being included in blends. */
class MemberOptedOutException(
    val spotifyId: String,
    message: String,
) : IllegalArgumentException(message)
