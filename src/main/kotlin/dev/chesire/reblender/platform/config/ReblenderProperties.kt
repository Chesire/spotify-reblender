package dev.chesire.reblender.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "reblender")
data class ReblenderProperties(
    val spotifyApiBaseUrl: String = "https://api.spotify.com/v1",
    /** Base64 AES-256 key for encrypting stored Spotify tokens. Generate with `openssl rand -base64 32`. */
    val tokenEncryptionKey: String = "",
    /** Path to a file holding the key instead (Docker secret or TPM-sealed systemd credential). */
    val tokenEncryptionKeyFile: String = "",
)
