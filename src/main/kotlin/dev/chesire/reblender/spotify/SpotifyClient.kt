package dev.chesire.reblender.spotify

import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.blend.Track
import dev.chesire.reblender.config.ReblenderProperties
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class CreatedPlaylist(val id: String, val url: String)

/**
 * Thin wrapper over the Spotify Web API endpoints this app needs.
 * Uses the post-February-2026 endpoints (`/me/playlists`, `/playlists/{id}/items`).
 * JSON is handled with kotlinx.serialization on raw strings, so we don't depend on Spring's Jackson setup.
 */
@Component
class SpotifyClient(
    properties: ReblenderProperties,
    private val tokens: SpotifyTokens,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = RestClient.builder().baseUrl(properties.spotifyApiBaseUrl).build()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Up to 50 of [spotifyId]'s top tracks for [range], best first. */
    fun topTracks(spotifyId: String, range: TimeRange): List<Track> {
        val body = rest.get()
            .uri("/me/top/tracks?time_range={range}&limit={limit}", range.spotifyValue, TOP_TRACKS_LIMIT)
            .headers { it.setBearerAuth(tokens.accessTokenFor(spotifyId)) }
            .retrieve()
            .body(String::class.java)
            ?: return emptyList()

        val items = json.decodeFromString(TopTracksResponse.serializer(), body).items
        val local = items.count { it.isLocal }
        val missingId = items.count { !it.isLocal && (it.id == null || it.uri == null) }
        val tracks = items
            .filter { !it.isLocal && it.id != null && it.uri != null }
            .map { Track(id = it.id!!, uri = it.uri!!, name = it.name, artists = it.artists.map(ArtistDto::name)) }
        log.info(
            "Top tracks for {} ({}): Spotify returned {} of {} requested; skipped {} local file(s) and {} without an ID; {} usable",
            spotifyId, range.spotifyValue, items.size, TOP_TRACKS_LIMIT, local, missingId, tracks.size,
        )
        return tracks
    }

    /** Creates a private playlist owned by [ownerId]. */
    fun createPlaylist(ownerId: String, name: String, description: String): CreatedPlaylist {
        val request = json.encodeToString(
            CreatePlaylistRequest.serializer(),
            CreatePlaylistRequest(name, description, isPublic = false),
        )
        val body = rest.post()
            .uri("/me/playlists")
            .headers { it.setBearerAuth(tokens.accessTokenFor(ownerId)) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(String::class.java)
            ?: error("Spotify returned an empty response when creating a playlist")

        val playlist = json.decodeFromString(PlaylistDto.serializer(), body)
        val url = playlist.externalUrls.spotify.ifBlank { "https://open.spotify.com/playlist/${playlist.id}" }
        return CreatedPlaylist(playlist.id, url)
    }

    /**
     * Replaces the whole playlist contents. Spotify takes at most 100 items per request, so the first
     * batch replaces and any further batches are appended in order.
     */
    fun replaceItems(ownerId: String, playlistId: String, uris: List<String>) {
        val batches = uris.chunked(MAX_ITEMS_PER_REQUEST).ifEmpty { listOf(emptyList()) }
        batches.forEachIndexed { index, batch ->
            val body = json.encodeToString(ReplaceItemsRequest.serializer(), ReplaceItemsRequest(batch))
            val request = if (index == 0) rest.put() else rest.post()
            request
                .uri("/playlists/{id}/items", playlistId)
                .headers { it.setBearerAuth(tokens.accessTokenFor(ownerId)) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()
        }
    }

    /**
     * Removes a playlist from the owner's library, which is how Spotify "deletes" a playlist you own.
     * Uses the post-February-2026 generic library endpoint. Spotify keeps deleted playlists recoverable
     * for a while at spotify.com/account/recover-playlists.
     */
    fun removePlaylistFromLibrary(ownerId: String, playlistId: String) {
        rest.delete()
            .uri("/me/library?uris={uri}", "spotify:playlist:$playlistId")
            .headers { it.setBearerAuth(tokens.accessTokenFor(ownerId)) }
            .retrieve()
            .toBodilessEntity()
    }

    companion object {
        const val TOP_TRACKS_LIMIT = 50
        const val MAX_ITEMS_PER_REQUEST = 100
    }
}
