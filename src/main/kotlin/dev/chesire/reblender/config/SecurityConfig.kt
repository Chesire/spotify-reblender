package dev.chesire.reblender.config

import dev.chesire.reblender.security.EncryptedJdbcAuthorizedClientService
import dev.chesire.reblender.security.TokenCipher
import dev.chesire.reblender.users.LoginSuccessHandler
import dev.chesire.reblender.users.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, users: UserRepository): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/", permitAll)
                authorize("/error", permitAll)
                authorize("/css/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                loginPage = "/"
                authenticationSuccessHandler = LoginSuccessHandler(users)
            }
            logout {
                logoutSuccessUrl = "/"
            }
        }
        return http.build()
    }

    /** Fails fast at startup if TOKEN_ENCRYPTION_KEY is missing or malformed. */
    @Bean
    fun tokenCipher(properties: ReblenderProperties): TokenCipher = TokenCipher(properties.tokenEncryptionKey)

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
                    .refreshToken()
                    .build(),
            )
        }
}
