package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/** A playlist this app manages: who it's for and how to rebuild it. */
data class Pairing(
    val playlistId: String,
    val playlistName: String,
    val playlistUrl: String,
    val ownerId: String,
    val partnerId: String,
    val timeRange: TimeRange,
    val size: Int,
    val sharedMode: SharedMode,
    val trackCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Repository
class PairingRepository(private val jdbc: JdbcClient) {

    private val mapper = RowMapper { rs, _ ->
        Pairing(
            playlistId = rs.getString("playlist_id"),
            playlistName = rs.getString("playlist_name"),
            playlistUrl = rs.getString("playlist_url"),
            ownerId = rs.getString("owner_id"),
            partnerId = rs.getString("partner_id"),
            timeRange = TimeRange.valueOf(rs.getString("time_range")),
            size = rs.getInt("size"),
            sharedMode = SharedMode.valueOf(rs.getString("shared_mode")),
            trackCount = rs.getInt("track_count"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    fun insert(pairing: Pairing) {
        jdbc.sql(
            """
            INSERT INTO pairing (playlist_id, playlist_name, playlist_url, owner_id, partner_id,
                                 time_range, size, shared_mode, track_count, created_at, updated_at)
            VALUES (:playlistId, :name, :url, :owner, :partner, :range, :size, :mode, :count, :created, :updated)
            """.trimIndent(),
        )
            .param("playlistId", pairing.playlistId)
            .param("name", pairing.playlistName)
            .param("url", pairing.playlistUrl)
            .param("owner", pairing.ownerId)
            .param("partner", pairing.partnerId)
            .param("range", pairing.timeRange.name)
            .param("size", pairing.size)
            .param("mode", pairing.sharedMode.name)
            .param("count", pairing.trackCount)
            .param("created", Timestamp.from(pairing.createdAt))
            .param("updated", Timestamp.from(pairing.updatedAt))
            .update()
    }

    fun markRefreshed(playlistId: String, trackCount: Int, at: Instant) {
        jdbc.sql("UPDATE pairing SET track_count = :count, updated_at = :at WHERE playlist_id = :id")
            .param("count", trackCount)
            .param("at", Timestamp.from(at))
            .param("id", playlistId)
            .update()
    }

    /** Removes every pairing the user owns or is the partner in. The Spotify playlists themselves are left alone. */
    fun deleteInvolving(spotifyId: String) {
        jdbc.sql("DELETE FROM pairing WHERE owner_id = :id OR partner_id = :id")
            .param("id", spotifyId)
            .update()
    }

    fun findByOwner(ownerId: String): List<Pairing> =
        jdbc.sql("SELECT * FROM pairing WHERE owner_id = :owner ORDER BY created_at DESC")
            .param("owner", ownerId)
            .query(mapper)
            .list()

    fun findByIdAndOwner(playlistId: String, ownerId: String): Pairing? =
        jdbc.sql("SELECT * FROM pairing WHERE playlist_id = :id AND owner_id = :owner")
            .param("id", playlistId)
            .param("owner", ownerId)
            .query(mapper)
            .optional()
            .orElse(null)
}
