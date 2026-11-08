package dev.chesire.reblender.playlists.api

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.PlaylistService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** JSON API for blends. Same session login and CSRF rules as the web pages; errors come back as problem details. */
@RestController
@RequestMapping("/api/v1/playlists")
class PlaylistApiController(
    private val playlists: PlaylistService,
) {
    @GetMapping
    fun list(authentication: Authentication): List<PlaylistResponse> = playlists.playlistsFor(authentication.name).map(PlaylistResponse::of)

    @PostMapping
    fun create(
        authentication: Authentication,
        @RequestBody request: CreatePlaylistRequest,
    ): ResponseEntity<PlaylistResultResponse> {
        val outcome =
            playlists.create(authentication.name, request.partnerIds, request.timeRange, request.size, request.sharedMode)
        return ResponseEntity.status(HttpStatus.CREATED).body(PlaylistResultResponse.of(outcome))
    }

    @GetMapping("/{playlistId}")
    fun details(
        authentication: Authentication,
        @PathVariable playlistId: String,
    ): ResponseEntity<PlaylistDetailsResponse> =
        playlists.details(authentication.name, playlistId)?.let { ResponseEntity.ok(PlaylistDetailsResponse.of(it)) }
            ?: ResponseEntity.notFound().build()

    /** Rebuilds the playlist from everyone's current top tracks. */
    @PostMapping("/{playlistId}/refresh")
    fun refresh(
        authentication: Authentication,
        @PathVariable playlistId: String,
    ): PlaylistResultResponse = PlaylistResultResponse.of(playlists.update(authentication.name, playlistId))

    @DeleteMapping("/{playlistId}")
    fun delete(
        authentication: Authentication,
        @PathVariable playlistId: String,
        @RequestParam(defaultValue = "false") removeFromSpotify: Boolean,
    ): DeleteResponse = DeleteResponse.of(playlists.delete(authentication.name, playlistId, removeFromSpotify))
}

data class CreatePlaylistRequest(
    val partnerIds: List<String> = emptyList(),
    val timeRange: TimeRange,
    val size: Int,
    val sharedMode: SharedMode = SharedMode.CAPPED,
)
