package dev.chesire.reblender.config

import dev.chesire.reblender.logging.OutgoingRequestLogger
import dev.chesire.reblender.security.EncryptedJdbcAuthorizedClientService
import dev.chesire.reblender.security.EncryptionKeySource
import dev.chesire.reblender.security.TokenCipher
import dev.chesire.reblender.users.LoginSuccessHandler
import dev.chesire.reblender.users.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.security.web.header.writers.StaticHeadersWriter
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        users: UserRepository,
        clientRegistrations: ClientRegistrationRepository,
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
                authenticationSuccessHandler = LoginSuccessHandler(users)
                authorizationEndpoint {
                    authorizationRequestResolver = pkceAuthorizationRequestResolver(clientRegistrations)
                }
                tokenEndpoint {
                    accessTokenResponseClient = RestClientAuthorizationCodeTokenResponseClient().apply {
                        setRestClient(loggingTokenRestClient())
                    }
                }
                userInfoEndpoint {
                    userService = DefaultOAuth2UserService().apply { setRestOperations(loggingUserInfoRestTemplate()) }
                }
            }
            logout {
                logoutSuccessUrl = "/"
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

    private fun pkceAuthorizationRequestResolver(clientRegistrations: ClientRegistrationRepository) =
        DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization").apply {
            setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
        }

    /** Fails fast at startup if the key is missing, unreadable or malformed. */
    @Bean
    fun tokenCipher(properties: ReblenderProperties): TokenCipher =
        TokenCipher(EncryptionKeySource.resolve(properties.tokenEncryptionKey, properties.tokenEncryptionKeyFile))

    /**
     * Persist tokens in the database (encrypted) so the app can act for a partner who isn't logged in.
     */
    @Bean
    fun authorizedClientService(
        jdbcTemplate: JdbcTemplate,
        clientRegistrations: ClientRegistrationRepository,
        tokenCipher: TokenCipher,
    ): OAuth2AuthorizedClientService =
        EncryptedJdbcAuthorizedClientService(jdbcTemplate, clientRegistrations, tokenCipher)

    /**
     * Works outside an HTTP request/session: loads a stored client by principal name
     * and refreshes its access token when it has expired.
     */
    @Bean
    fun authorizedClientManager(
        clientRegistrations: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClientService).apply {
            setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                    .refreshToken { refresh ->
                        refresh.accessTokenResponseClient(
                            RestClientRefreshTokenTokenResponseClient().apply { setRestClient(loggingTokenRestClient()) },
                        )
                    }
                    .build(),
            )
        }

    /** Mirrors Spring Security's default token-endpoint RestClient, with [OutgoingRequestLogger] added. */
    private fun loggingTokenRestClient(): RestClient =
        RestClient.builder()
            .configureMessageConverters { converters ->
                converters.addCustomConverter(FormHttpMessageConverter())
                converters.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter())
            }
            .defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
            .requestInterceptor(OutgoingRequestLogger())
            .build()

    /** Mirrors DefaultOAuth2UserService's default RestTemplate, with [OutgoingRequestLogger] added. */
    private fun loggingUserInfoRestTemplate(): RestTemplate =
        RestTemplate().apply {
            errorHandler = OAuth2ErrorResponseErrorHandler()
            interceptors.add(OutgoingRequestLogger())
        }

    companion object {
        /**
         * Scripts and styles only from this app; images also from Spotify's CDNs (covers, profile pictures).
         * Inline style *attributes* are allowed for per-member colours and bar widths; inline <style>/<script> are not.
         */
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
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

        const val PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()"
    }
}
