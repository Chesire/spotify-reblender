package dev.chesire.reblender.security

import org.slf4j.LoggerFactory
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.SqlParameterValue
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientHolder
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientParametersMapper
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.util.StringUtils
import java.sql.ResultSet
import java.sql.Types
import java.util.function.Function

/**
 * Spring's JDBC token store, with access and refresh tokens encrypted (AES-256-GCM) before they hit the database.
 *
 * If a stored token can't be decrypted (key changed, tampering, or a row written before encryption existed),
 * the row is deleted and treated as "not logged in", so the user is simply asked to log in again.
 */
class EncryptedJdbcAuthorizedClientService(
    jdbcOperations: JdbcOperations,
    clientRegistrations: ClientRegistrationRepository,
    cipher: TokenCipher,
) : JdbcOAuth2AuthorizedClientService(jdbcOperations, clientRegistrations) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        setAuthorizedClientParametersMapper(EncryptingParametersMapper(cipher))
        setAuthorizedClientRowMapper(DecryptingRowMapper(clientRegistrations, cipher))
    }

    override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
        clientRegistrationId: String,
        principalName: String,
    ): T? = try {
        super.loadAuthorizedClient(clientRegistrationId, principalName)
    } catch (e: TokenDecryptionException) {
        log.warn("Discarding unreadable stored token for {} ({}); the user must log in again", principalName, e.message)
        removeAuthorizedClient(clientRegistrationId, principalName)
        null
    }
}

/** Wraps Spring's default column mapping and encrypts the two token BLOBs. Fails closed on anything unexpected. */
internal class EncryptingParametersMapper(
    private val cipher: TokenCipher,
) : Function<OAuth2AuthorizedClientHolder, List<SqlParameterValue>> {

    private val delegate = OAuth2AuthorizedClientParametersMapper()

    override fun apply(holder: OAuth2AuthorizedClientHolder): List<SqlParameterValue> {
        val aad = TokenCipher.associatedData(
            holder.authorizedClient.clientRegistration.registrationId,
            holder.principal.name,
        )
        return delegate.apply(holder).map { parameter ->
            if (parameter.sqlType != Types.BLOB) return@map parameter
            when (val value = parameter.value) {
                null -> parameter
                is ByteArray -> SqlParameterValue(Types.BLOB, cipher.encrypt(value, aad))
                // Never risk writing a token unencrypted if Spring changes its internal representation.
                else -> throw IllegalStateException("Unexpected token parameter type ${value::class.java.name}; refusing to store it")
            }
        }
    }
}

/** Reads a stored client and decrypts its tokens. Mirrors Spring's default row mapper. */
internal class DecryptingRowMapper(
    private val clientRegistrations: ClientRegistrationRepository,
    private val cipher: TokenCipher,
) : RowMapper<OAuth2AuthorizedClient> {

    override fun mapRow(rs: ResultSet, rowNum: Int): OAuth2AuthorizedClient {
        val registrationId = rs.getString("client_registration_id")
        val principalName = rs.getString("principal_name")
        val registration = clientRegistrations.findByRegistrationId(registrationId)
            ?: throw DataRetrievalFailureException("Client registration '$registrationId' no longer exists")
        val aad = TokenCipher.associatedData(registrationId, principalName)

        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            decryptToString(rs.getBytes("access_token_value"), aad),
            rs.getTimestamp("access_token_issued_at").toInstant(),
            rs.getTimestamp("access_token_expires_at").toInstant(),
            rs.getString("access_token_scopes")?.let(StringUtils::commaDelimitedListToSet) ?: emptySet(),
        )
        val refreshToken = rs.getBytes("refresh_token_value")?.let { bytes ->
            OAuth2RefreshToken(decryptToString(bytes, aad), rs.getTimestamp("refresh_token_issued_at")?.toInstant())
        }
        return OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken)
    }

    private fun decryptToString(bytes: ByteArray, aad: ByteArray) =
        String(cipher.decrypt(bytes, aad), Charsets.UTF_8)
}
