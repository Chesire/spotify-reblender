package dev.chesire.reblender.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields we use. Everything has a default so Spotify's 2026 field removals/renames don't break parsing.

@Serializable
internal data class TopTracksResponse(
    val items: List<TrackDto> = emptyList(),
)

@Serializable
internal data class TrackDto(
    val id: String? = null,
    val uri: String? = null,
    val name: String = "",
    val artists: List<ArtistDto> = emptyList(),
    @SerialName("is_local") val isLocal: Boolean = false,
)

@Serializable
internal data class ArtistDto(
    val name: String = "",
)

@Serializable
internal data class CreatePlaylistRequest(
    val name: String,
    val description: String,
    @SerialName("public") val isPublic: Boolean,
)

@Serializable
internal data class PlaylistDto(
    val id: String,
    @SerialName("external_urls") val externalUrls: ExternalUrls = ExternalUrls(),
)

@Serializable
internal data class ExternalUrls(
    val spotify: String = "",
)

@Serializable
internal data class ReplaceItemsRequest(
    val uris: List<String>,
)
