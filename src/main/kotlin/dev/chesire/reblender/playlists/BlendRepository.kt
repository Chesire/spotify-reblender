package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.playlists.models.Blend
import dev.chesire.reblender.playlists.models.BlendTrack
import dev.chesire.reblender.security.SafeUrls
import dev.chesire.reblender.spotify.SpotifyClient
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Repository
class BlendRepository(
    private val jdbc: JdbcClient,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            Blend(
                playlistId = rs.getString("playlist_id"),
                playlistName = rs.getString("playlist_name"),
                playlistUrl = rs.getString("playlist_url"),
                ownerId = rs.getString("owner_id"),
                memberIds = emptyList(),
                timeRange = TimeRange.valueOf(rs.getString("time_range")),
                size = rs.getInt("size"),
                sharedMode = SharedMode.valueOf(rs.getString("shared_mode")),
                trackCount = rs.getInt("track_count"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
            )
        }

    @Transactional
    fun insert(blend: Blend) {
        jdbc
            .sql(
                """
                INSERT INTO blend (playlist_id, playlist_name, playlist_url, owner_id,
                                   time_range, size, shared_mode, track_count, created_at, updated_at)
                VALUES (:playlistId, :name, :url, :owner, :range, :size, :mode, :count, :created, :updated)
                """.trimIndent(),
            ).param("playlistId", blend.playlistId)
            .param("name", blend.playlistName)
            .param("url", SafeUrls.httpsOrNull(blend.playlistUrl) ?: SpotifyClient.playlistUrl(blend.playlistId))
            .param("owner", blend.ownerId)
            .param("range", blend.timeRange.name)
            .param("size", blend.size)
            .param("mode", blend.sharedMode.name)
            .param("count", blend.trackCount)
            .param("created", Timestamp.from(blend.createdAt))
            .param("updated", Timestamp.from(blend.updatedAt))
            .update()

        blend.memberIds.forEachIndexed { index, memberId ->
            jdbc
                .sql("INSERT INTO blend_member (playlist_id, spotify_id, member_order) VALUES (:id, :member, :order)")
                .param("id", blend.playlistId)
                .param("member", memberId)
                .param("order", index)
                .update()
        }
    }

    fun markRefreshed(
        playlistId: String,
        trackCount: Int,
        at: Instant,
    ) {
        jdbc
            .sql("UPDATE blend SET track_count = :count, updated_at = :at WHERE playlist_id = :id")
            .param("count", trackCount)
            .param("at", Timestamp.from(at))
            .param("id", playlistId)
            .update()
    }

    fun findByOwner(ownerId: String): List<Blend> =
        jdbc
            .sql("SELECT * FROM blend WHERE owner_id = :owner ORDER BY created_at DESC")
            .param("owner", ownerId)
            .query(rowMapper)
            .list()
            .map(::withMembers)

    fun findByIdAndOwner(
        playlistId: String,
        ownerId: String,
    ): Blend? =
        jdbc
            .sql("SELECT * FROM blend WHERE playlist_id = :id AND owner_id = :owner")
            .param("id", playlistId)
            .param("owner", ownerId)
            .query(rowMapper)
            .optional()
            .map(::withMembers)
            .orElse(null)

    /** Replaces the stored song list for a blend with the latest build, in playlist order. */
    @Transactional
    fun replaceTracks(
        playlistId: String,
        tracks: List<BlendTrack>,
    ) {
        jdbc.sql("DELETE FROM blend_track WHERE playlist_id = :id").param("id", playlistId).update()
        tracks.forEachIndexed { index, track ->
            jdbc
                .sql(
                    """
                    INSERT INTO blend_track (playlist_id, track_order, track_id, track_uri, track_name, artists, contributors, image_url)
                    VALUES (:id, :order, :trackId, :uri, :name, :artists, :contributors, :image)
                    """.trimIndent(),
                ).param("id", playlistId)
                .param("order", index)
                .param("trackId", track.trackId)
                .param("uri", track.uri)
                .param("name", track.name.take(500))
                .param("artists", track.artists.joinToString(SEPARATOR).take(1000))
                .param("contributors", track.contributors.joinToString(SEPARATOR))
                .param("image", SafeUrls.httpsOrNull(track.imageUrl))
                .update()
        }
    }

    fun findTracks(playlistId: String): List<BlendTrack> =
        jdbc
            .sql("SELECT * FROM blend_track WHERE playlist_id = :id ORDER BY track_order")
            .param("id", playlistId)
            .query { rs, _ ->
                BlendTrack(
                    trackId = rs.getString("track_id"),
                    uri = rs.getString("track_uri"),
                    name = rs.getString("track_name"),
                    artists = rs.getString("artists").split(SEPARATOR).filter { it.isNotEmpty() },
                    contributors = rs.getString("contributors").split(SEPARATOR).filter { it.isNotEmpty() },
                    imageUrl = rs.getString("image_url"),
                )
            }.list()

    /** Deletes one blend (members cascade). Returns false if it wasn't the owner's. */
    fun delete(
        playlistId: String,
        ownerId: String,
    ): Boolean =
        jdbc
            .sql("DELETE FROM blend WHERE playlist_id = :id AND owner_id = :owner")
            .param("id", playlistId)
            .param("owner", ownerId)
            .update() > 0

    /** Removes every blend the user is in (as owner or member). The Spotify playlists themselves are left alone. */
    fun deleteInvolving(spotifyId: String) {
        jdbc
            .sql(
                "DELETE FROM blend WHERE playlist_id IN (SELECT playlist_id FROM blend_member WHERE spotify_id = :id)",
            ).param("id", spotifyId)
            .update()
    }

    private companion object {
        /** ASCII unit separator: can't appear in names, so lists round-trip safely. */
        const val SEPARATOR = "\u001F"
    }

    private fun withMembers(blend: Blend): Blend {
        val members =
            jdbc
                .sql("SELECT spotify_id FROM blend_member WHERE playlist_id = :id ORDER BY member_order")
                .param("id", blend.playlistId)
                .query(String::class.java)
                .list()
                .filterNotNull()
        return blend.copy(memberIds = members)
    }
}
