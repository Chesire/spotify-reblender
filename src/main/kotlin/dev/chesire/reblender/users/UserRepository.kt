package dev.chesire.reblender.users

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class AppUser(
    val spotifyId: String,
    val displayName: String,
)

@Repository
class UserRepository(private val jdbc: JdbcClient) {

    fun upsert(user: AppUser) {
        jdbc.sql(
            """
            MERGE INTO app_user (spotify_id, display_name, last_login_at)
            KEY (spotify_id)
            VALUES (:id, :name, :now)
            """.trimIndent(),
        )
            .param("id", user.spotifyId)
            .param("name", user.displayName)
            .param("now", Timestamp.from(Instant.now()))
            .update()
    }

    fun delete(spotifyId: String) {
        jdbc.sql("DELETE FROM app_user WHERE spotify_id = :id").param("id", spotifyId).update()
    }

    fun findAll(): List<AppUser> =
        jdbc.sql("SELECT spotify_id, display_name FROM app_user ORDER BY display_name")
            .query { rs, _ -> AppUser(rs.getString("spotify_id"), rs.getString("display_name")) }
            .list()

    fun findById(spotifyId: String): AppUser? =
        jdbc.sql("SELECT spotify_id, display_name FROM app_user WHERE spotify_id = :id")
            .param("id", spotifyId)
            .query { rs, _ -> AppUser(rs.getString("spotify_id"), rs.getString("display_name")) }
            .optional()
            .orElse(null)
}
