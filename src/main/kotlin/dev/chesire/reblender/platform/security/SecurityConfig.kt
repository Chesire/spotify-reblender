package dev.chesire.reblender.platform.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.security.web.header.writers.StaticHeadersWriter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        loginSuccessHandler: AuthenticationSuccessHandler,
        authorizationRequestResolver: DefaultOAuth2AuthorizationRequestResolver,
        authorizationCodeTokenResponseClient: OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>,
        oauth2UserService: DefaultOAuth2UserService,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/", permitAll)
                authorize("/error", permitAll)
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                loginPage = "/"
                authenticationSuccessHandler = loginSuccessHandler
                authorizationEndpoint {
                    this.authorizationRequestResolver = authorizationRequestResolver
                }
                tokenEndpoint {
                    accessTokenResponseClient = authorizationCodeTokenResponseClient
                }
                userInfoEndpoint {
                    userService = oauth2UserService
                }
            }
            logout {
                logoutSuccessUrl = "/"
            }
            exceptionHandling {
                // The JSON API answers 401 instead of redirecting to the login page.
                defaultAuthenticationEntryPointFor(
                    HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                )
            }
            headers {
                contentSecurityPolicy {
                    policyDirectives = CONTENT_SECURITY_POLICY
                }
                referrerPolicy {
                    policy = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                }
                addHeaderWriter(StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
            }
        }
        return http.build()
    }

    companion object {
        /**
         * Scripts and styles only from this app; images also from Spotify's CDNs (covers, profile pictures).
         * Inline style *attributes* are allowed for per-member colours and bar widths; inline <style>/<script> are not.
         */
        const val CONTENT_SECURITY_POLICY = "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self'; " +
                "style-src-attr 'unsafe-inline'; " +
                "img-src 'self' data: https://*.scdn.co https://*.spotifycdn.com; " +
                "font-src 'self'; " +
                "connect-src 'self'; " +
                "object-src 'none'; " +
                "base-uri 'none'; " +
                "form-action 'self'; " +
                "frame-ancestors 'none'"

        const val PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()"
    }
}
