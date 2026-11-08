package dev.chesire.reblender.accounts.api

import dev.chesire.reblender.accounts.AccountService
import dev.chesire.reblender.accounts.models.AppUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** JSON API for the signed-in user's account. Same session login and CSRF rules as the web pages. */
@RestController
@RequestMapping("/api/v1")
class AccountApiController(
    private val accounts: AccountService,
) {
    @GetMapping("/me")
    fun me(authentication: Authentication): ResponseEntity<MeResponse> =
        accounts.find(authentication.name)?.let { ResponseEntity.ok(MeResponse.of(it)) }
            ?: ResponseEntity.notFound().build()

    @PutMapping("/me/blending")
    fun setBlending(
        authentication: Authentication,
        @RequestBody request: BlendingRequest,
    ): ResponseEntity<MeResponse> {
        accounts.setAllowBlending(authentication.name, request.allowBlending)
        return me(authentication)
    }

    /** Forgets the user (tokens, record, blends) and ends the session. */
    @DeleteMapping("/me")
    fun forget(
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Unit> {
        accounts.forget(authentication.name)
        SecurityContextLogoutHandler().logout(request, response, authentication)
        return ResponseEntity.noContent().build()
    }

    /** People the signed-in user can include in a blend. */
    @GetMapping("/people")
    fun people(authentication: Authentication): List<PersonResponse> = accounts.partnersFor(authentication.name).map(PersonResponse::of)
}

data class MeResponse(
    val spotifyId: String,
    val displayName: String,
    val avatarUrl: String?,
    val allowBlending: Boolean,
) {
    companion object {
        fun of(user: AppUser) = MeResponse(user.spotifyId, user.displayName, user.avatarUrl, user.allowBlending)
    }
}

data class PersonResponse(
    val spotifyId: String,
    val displayName: String,
    val avatarUrl: String?,
) {
    companion object {
        fun of(user: AppUser) = PersonResponse(user.spotifyId, user.displayName, user.avatarUrl)
    }
}

data class BlendingRequest(
    val allowBlending: Boolean,
)
