package dev.chesire.reblender.web

import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.playlists.Pairing
import dev.chesire.reblender.playlists.PlaylistService
import dev.chesire.reblender.spotify.ReloginRequiredException
import dev.chesire.reblender.users.AccountService
import dev.chesire.reblender.users.AppUser
import dev.chesire.reblender.users.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller
class HomeController(
    private val users: UserRepository,
    private val playlists: PlaylistService,
    private val accounts: AccountService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/")
    fun home(@AuthenticationPrincipal principal: OAuth2User?, model: Model): String {
        if (principal == null) {
            model.addAttribute("loggedIn", false)
            return "index"
        }
        val me = principal.name
        if (users.findById(me) == null) {
            // Session outlived the database (e.g. the volume was reset): re-record the user.
            users.upsert(AppUser(me, principal.getAttribute<String>("display_name")?.takeIf { it.isNotBlank() } ?: me))
        }
        val allUsers = users.findAll()
        val names = allUsers.associate { it.spotifyId to it.displayName }
        model.addAttribute("loggedIn", true)
        model.addAttribute("me", names[me] ?: me)
        model.addAttribute("partners", allUsers.filter { it.spotifyId != me })
        model.addAttribute("playlists", playlists.playlistsFor(me).map {
                PlaylistRow(it, names[it.partnerId] ?: it.partnerId, UPDATED_FORMAT.format(it.updatedAt))
            })
        model.addAttribute("timeRanges", TimeRange.entries)
        model.addAttribute("sharedModes", SharedMode.entries)
        model.addAttribute("defaultSize", DEFAULT_SIZE)
        model.addAttribute("maxSize", PlaylistService.MAX_SIZE)
        return "index"
    }

    @PostMapping("/playlists")
    fun create(
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestParam partnerId: String,
        @RequestParam timeRange: TimeRange,
        @RequestParam size: Int,
        @RequestParam sharedMode: SharedMode,
        redirect: RedirectAttributes,
    ): String {
        handle(principal.name, redirect) {
            val pairing = playlists.create(principal.name, partnerId, timeRange, size, sharedMode)
            "Created \"${pairing.playlistName}\" with ${pairing.trackCount} songs."
        }
        return "redirect:/"
    }

    @PostMapping("/playlists/{playlistId}/update")
    fun update(
        @AuthenticationPrincipal principal: OAuth2User,
        @PathVariable playlistId: String,
        redirect: RedirectAttributes,
    ): String {
        handle(principal.name, redirect) {
            val pairing = playlists.update(principal.name, playlistId)
            "Updated \"${pairing.playlistName}\" with ${pairing.trackCount} songs."
        }
        return "redirect:/"
    }

    /** Deletes this user's stored tokens and data, then logs them out. */
    @PostMapping("/account/forget")
    fun forget(
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
        redirect: RedirectAttributes,
    ): String {
        accounts.forget(authentication.name)
        SecurityContextLogoutHandler().logout(request, response, authentication)
        redirect.addFlashAttribute("message", "Your stored Spotify login and blends have been deleted from this app.")
        return "redirect:/"
    }

    private fun handle(me: String, redirect: RedirectAttributes, action: () -> String) {
        try {
            redirect.addFlashAttribute("message", action())
        } catch (e: ReloginRequiredException) {
            val text = if (e.spotifyId == me) {
                "Your Spotify login has expired. Log out and log in again."
            } else {
                val who = users.findById(e.spotifyId)?.displayName ?: e.spotifyId
                "$who needs to log in to the app again (Spotify logins expire after 6 months)."
            }
            redirect.addFlashAttribute("error", text)
        } catch (e: RestClientResponseException) {
            log.warn("Spotify API error {}: {}", e.statusCode, e.responseBodyAsString)
            val text = when (e.statusCode.value()) {
                403 -> "Spotify refused the request (403). Check both users are on the app's allowlist in the Spotify dashboard."
                404 -> "Spotify couldn't find that playlist. It may have been deleted."
                429 -> "Spotify is rate limiting the app. Try again in a minute."
                else -> "Spotify returned an error (${e.statusCode.value()})."
            }
            redirect.addFlashAttribute("error", text)
        } catch (e: IllegalArgumentException) {
            redirect.addFlashAttribute("error", e.message ?: "Invalid request.")
        }
    }

    data class PlaylistRow(val pairing: Pairing, val partnerName: String, val updatedLabel: String)

    companion object {
        const val DEFAULT_SIZE = 40
        private val UPDATED_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }
}

