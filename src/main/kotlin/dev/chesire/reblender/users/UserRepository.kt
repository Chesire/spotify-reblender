package dev.chesire.reblender.users

import dev.chesire.reblender.security.SafeUrls
import dev.chesire.reblender.users.models.AppUser
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val jdbc: JdbcClient) {

    private val rowMapper = RowMapper { rs, _ ->
        AppUser(
            spotifyId = rs.getString("spotify_id"),
            displayName = rs.getString("display_name"),
            avatarUrl = rs.getString("avatar_url"),
            allowBlending = rs.getBoolean("allow_blending"),
        )
    }

    /** Inserts or refreshes a user's profile. Never changes [AppUser.allowBlending] for an existing user. */
    fun upsert(user: AppUser) {
        jdbc.sql(
            """
            MERGE INTO app_user (spotify_id, display_name, avatar_url, last_login_at)
            KEY (spotify_id)
            VALUES (:id, :name, :avatar, :now)
            """.trimIndent(),
        )
            .param("id", user.spotifyId)
            .param("name", user.displayName)
            .param("avatar", SafeUrls.httpsOrNull(user.avatarUrl))
            .param("now", Timestamp.from(Instant.now()))
            .update()
    }

    fun setAllowBlending(spotifyId: String, allow: Boolean) {
        jdbc.sql("UPDATE app_user SET allow_blending = :allow WHERE spotify_id = :id")
            .param("allow", allow)
            .param("id", spotifyId)
            .update()
    }

    fun delete(spotifyId: String) {
        jdbc.sql("DELETE FROM app_user WHERE spotify_id = :id").param("id", spotifyId).update()
    }

    fun findAll(): List<AppUser> =
        jdbc.sql("SELECT spotify_id, display_name, avatar_url, allow_blending FROM app_user ORDER BY display_name")
            .query(rowMapper)
            .list()

    fun findById(spotifyId: String): AppUser? =
        jdbc.sql("SELECT spotify_id, display_name, avatar_url, allow_blending FROM app_user WHERE spotify_id = :id")
            .param("id", spotifyId)
            .query(rowMapper)
            .optional()
            .orElse(null)
}
