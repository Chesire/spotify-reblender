package dev.chesire.reblender.playlists.api

import dev.chesire.reblender.integrations.spotify.ReloginRequiredException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientResponseException

/** Turns playlist failures into RFC 9457 problem details instead of error pages. */
@RestControllerAdvice(basePackageClasses = [PlaylistApiController::class])
class PlaylistApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler
    fun invalid(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid request.")

    /** Someone in the blend has to log in again before Spotify can be asked for their top tracks. */
    @ExceptionHandler
    fun relogin(e: ReloginRequiredException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A member needs to log in to the app again.").apply {
            setProperty("spotifyId", e.spotifyId)
        }

    @ExceptionHandler
    fun spotify(e: RestClientResponseException): ProblemDetail {
        log.warn("Spotify API error {}: {}", e.statusCode, e.responseBodyAsString)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Spotify refused the request.").apply {
            setProperty("spotifyStatus", e.statusCode.value())
        }
    }
}
