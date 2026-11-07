package dev.chesire.reblender.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "reblender")
data class ReblenderProperties(
    val spotifyApiBaseUrl: String = "https://api.spotify.com/v1",
)
