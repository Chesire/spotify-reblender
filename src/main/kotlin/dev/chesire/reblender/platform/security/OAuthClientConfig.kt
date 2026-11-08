package dev.chesire.reblender.platform.security

import dev.chesire.reblender.platform.config.ReblenderProperties
import dev.chesire.reblender.platform.logging.OutgoingRequestLogger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate

/** Spotify as an OAuth client: encrypted token storage, refresh, PKCE, and logged calls to Spotify's endpoints. */
@Configuration
class OAuthClientConfig {
    /** Login with PKCE (S256) on top of the client secret. */
    @Bean
    fun authorizationRequestResolver(clientRegistrations: ClientRegistrationRepository): DefaultOAuth2AuthorizationRequestResolver =
        DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/oauth2/authorization").apply {
            setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
        }

    /** Exchanges the login code for tokens. */
    @Bean
    fun authorizationCodeTokenResponseClient(): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> =
        RestClientAuthorizationCodeTokenResponseClient().apply { setRestClient(loggingTokenRestClient()) }

    /** Loads the Spotify profile after login. */
    @Bean
    fun oauth2UserService(): DefaultOAuth2UserService =
        DefaultOAuth2UserService().apply { setRestOperations(loggingUserInfoRestTemplate()) }

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
    ): OAuth2AuthorizedClientService = EncryptedJdbcAuthorizedClientService(jdbcTemplate, clientRegistrations, tokenCipher)

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
                OAuth2AuthorizedClientProviderBuilder
                    .builder()
                    .refreshToken { refresh ->
                        refresh.accessTokenResponseClient(
                            RestClientRefreshTokenTokenResponseClient().apply { setRestClient(loggingTokenRestClient()) },
                        )
                    }.build(),
            )
        }

    /** Mirrors Spring Security's default token-endpoint RestClient, with [OutgoingRequestLogger] added. */
    private fun loggingTokenRestClient(): RestClient =
        RestClient
            .builder()
            .configureMessageConverters { converters ->
                converters.addCustomConverter(FormHttpMessageConverter())
                converters.addCustomConverter(OAuth2AccessTokenResponseHttpMessageConverter())
            }.defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
            .requestInterceptor(OutgoingRequestLogger())
            .build()

    /** Mirrors DefaultOAuth2UserService's default RestTemplate, with [OutgoingRequestLogger] added. */
    private fun loggingUserInfoRestTemplate(): RestTemplate =
        RestTemplate().apply {
            errorHandler = OAuth2ErrorResponseErrorHandler()
            interceptors.add(OutgoingRequestLogger())
        }
}
