package dev.chesire.reblender.accounts.data

import dev.chesire.reblender.accounts.models.AppUser
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("app_user")
data class UserEntity(
    @Id val spotifyId: String,
    val displayName: String,
    val avatarUrl: String?,
    val allowBlending: Boolean,
    val lastLoginAt: Instant,
)

fun UserEntity.toModel() = AppUser(spotifyId, displayName, avatarUrl, allowBlending)
