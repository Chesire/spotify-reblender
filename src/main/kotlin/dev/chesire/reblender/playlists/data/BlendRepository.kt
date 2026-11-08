package dev.chesire.reblender.playlists.data

import dev.chesire.reblender.platform.data.InsertableRepository
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param

interface BlendRepository :
    ListCrudRepository<BlendEntity, String>,
    InsertableRepository<BlendEntity> {
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<BlendEntity>

    fun findByPlaylistIdAndOwnerId(
        playlistId: String,
        ownerId: String,
    ): BlendEntity?

    /** Every blend [spotifyId] is in, as owner or member. Members and songs go with them (ON DELETE CASCADE). */
    @Modifying
    @Query("DELETE FROM blend WHERE playlist_id IN (SELECT playlist_id FROM blend_member WHERE spotify_id = :spotifyId)")
    fun deleteInvolving(
        @Param("spotifyId") spotifyId: String,
    )
}
