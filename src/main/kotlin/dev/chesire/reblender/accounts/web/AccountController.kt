package dev.chesire.reblender.accounts.web

import dev.chesire.reblender.accounts.AccountService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/account")
class AccountController(
    private val accounts: AccountService,
) {
    /** Whether other people may include this user (and so read their top tracks) in blends. */
    @PostMapping("/blending")
    fun setBlending(
        authentication: Authentication,
        @RequestParam(defaultValue = "false") allowBlending: Boolean,
        redirect: RedirectAttributes,
    ): String {
        accounts.setAllowBlending(authentication.name, allowBlending)
        redirect.addFlashAttribute(
            "message",
            if (allowBlending) {
                "Others can now include you in their blends."
            } else {
                "Others can no longer include you in blends. Existing blends that include you can't be updated."
            },
        )
        return "redirect:/"
    }

    /** Deletes this user's stored tokens and data, then logs them out. */
    @PostMapping("/forget")
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
}
