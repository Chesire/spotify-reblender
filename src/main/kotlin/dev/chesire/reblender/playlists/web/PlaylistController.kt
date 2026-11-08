package dev.chesire.reblender.playlists.web

import dev.chesire.reblender.accounts.AccountService
import dev.chesire.reblender.blend.BlendEngine
import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.platform.security.SpotifyIds
import dev.chesire.reblender.playlists.PlaylistNaming
import dev.chesire.reblender.playlists.PlaylistService
import dev.chesire.reblender.spotify.ReloginRequiredException
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class PlaylistController(
    private val playlists: PlaylistService,
    private val accounts: AccountService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun home(
        @AuthenticationPrincipal principal: OAuth2User?,
        @RequestParam(required = false) expired: String?,
        model: Model,
    ): String {
        if (principal == null) {
            model.addAttribute("loggedIn", false)
            // Set by StaleSessionInterceptor when a session outlived the user's stored login.
            model.addAttribute("expired", expired != null)
            return "index"
        }
        val me = principal.name
        val everyone = accounts.all()
        val names = everyone.associate { it.spotifyId to it.displayName }
        model.addAttribute("loggedIn", true)
        model.addAttribute("me", names[me] ?: me)
        // Only people who allow it can be picked (also enforced in PlaylistService).
        model.addAttribute("partners", everyone.filter { it.spotifyId != me && it.allowBlending })
        model.addAttribute("allowBlending", everyone.firstOrNull { it.spotifyId == me }?.allowBlending ?: true)
        model.addAttribute("maxPartners", BlendEngine.MAX_PARTICIPANTS - 1)
        model.addAttribute(
            "playlists",
            playlists.playlistsFor(me).map { blend ->
                val others = blend.memberIds.drop(1).map { names[it] ?: it }
                PlaylistRow(
                    blend = blend,
                    withLabel = if (others.isEmpty()) "just you" else "with " + PlaylistNaming.joinNames(others, "and"),
                    updatedLabel = PlaylistMessages.updated(blend.updatedAt),
                )
            },
        )
        model.addAttribute("timeRanges", PlaylistLabels.timeRangeOptions)
        model.addAttribute("sharedModes", PlaylistLabels.sharedModeOptions)
        model.addAttribute("defaultSize", DEFAULT_SIZE)
        model.addAttribute("tracksPerPerson", BlendEngine.TRACKS_PER_PERSON)
        return "index"
    }

    @PostMapping("/playlists")
    fun create(
        authentication: Authentication,
        @RequestParam(name = "partnerIds", required = false) partnerIds: List<String>?,
        @RequestParam timeRange: TimeRange,
        @RequestParam size: Int,
        @RequestParam sharedMode: SharedMode,
        redirect: RedirectAttributes,
    ): String {
        handle(authentication.name, redirect) {
            val outcome = playlists.create(authentication.name, partnerIds.orEmpty(), timeRange, size, sharedMode)
            PlaylistMessages.created(outcome, ::nameOf)
        }
        return "redirect:/"
    }

    /** Who each song came from, and the balance between members. */
    @GetMapping("/playlists/{playlistId}")
    fun breakdown(
        authentication: Authentication,
        @PathVariable playlistId: String,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val details = playlists.details(authentication.name, playlistId)
        if (details == null) {
            redirect.addFlashAttribute("error", "That playlist isn't one of yours.")
            return "redirect:/"
        }
        model.addAttribute("b", Breakdown.of(details))
        model.addAttribute("updatedLabel", PlaylistMessages.updated(details.blend.updatedAt))
        return "breakdown"
    }

    @PostMapping("/playlists/{playlistId}/update")
    fun update(
        authentication: Authentication,
        @PathVariable playlistId: String,
        @RequestParam(required = false) returnTo: String?,
        redirect: RedirectAttributes,
    ): String {
        handle(authentication.name, redirect) {
            PlaylistMessages.refreshed(playlists.update(authentication.name, playlistId), ::nameOf)
        }
        // Only ever redirect to our own pages.
        return if (returnTo == "breakdown" && SpotifyIds.isPlaylistId(playlistId)) "redirect:/playlists/$playlistId" else "redirect:/"
    }

    @PostMapping("/playlists/{playlistId}/delete")
    fun delete(
        authentication: Authentication,
        @PathVariable playlistId: String,
        @RequestParam(defaultValue = "false") removeFromSpotify: Boolean,
        redirect: RedirectAttributes,
    ): String {
        handle(authentication.name, redirect) {
            PlaylistMessages.deleted(playlists.delete(authentication.name, playlistId, removeFromSpotify), removeFromSpotify)
        }
        return "redirect:/"
    }

    private fun nameOf(spotifyId: String): String = accounts.find(spotifyId)?.displayName ?: spotifyId

    /** Runs [action], flashing its message, or a readable error. */
    private fun handle(
        me: String,
        redirect: RedirectAttributes,
        action: () -> String,
    ) {
        val error =
            try {
                redirect.addFlashAttribute("message", action())
                return
            } catch (e: ReloginRequiredException) {
                PlaylistMessages.loginExpired(e.spotifyId == me, nameOf(e.spotifyId))
            } catch (e: RestClientResponseException) {
                log.warn("Spotify API error {}: {}", e.statusCode, e.responseBodyAsString)
                PlaylistMessages.spotifyError(e)
            } catch (e: IllegalArgumentException) {
                e.message ?: "Invalid request."
            }
        redirect.addFlashAttribute("error", error)
    }

    companion object {
        const val DEFAULT_SIZE = 40
    }
}
