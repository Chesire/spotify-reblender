package dev.chesire.reblender.integrations.spotify.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields we use. Everything has a default so missing or renamed fields don't break parsing.

@Serializable
data class TopTracksResponse(
    val items: List<TrackDto> = emptyList(),
)

@Serializable
data class TrackDto(
    val id: String? = null,
    val uri: String? = null,
    val name: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val album: AlbumDto? = null,
    @SerialName("is_local") val isLocal: Boolean = false,
)

@Serializable
data class AlbumDto(
    val images: List<ImageDto> = emptyList(),
)

@Serializable
data class ImageDto(
    val url: String? = null,
    val height: Int? = null,
)

@Serializable
data class ArtistDto(
    val name: String = "",
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String,
    @SerialName("public") val isPublic: Boolean,
)

@Serializable
data class PlaylistDto(
    val id: String,
    @SerialName("external_urls") val externalUrls: ExternalUrls = ExternalUrls(),
)

@Serializable
data class ExternalUrls(
    val spotify: String = "",
)

@Serializable
data class ReplaceItemsRequest(
    val uris: List<String>,
)
