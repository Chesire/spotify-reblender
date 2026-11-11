package dev.chesire.reblender.playlists.data

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.platform.data.JdbcMappingConfig
import dev.chesire.reblender.playlists.models.AutoUpdateFailure
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs the real repository against H2 with schema.sql. */
@DataJdbcTest
@Import(JdbcMappingConfig::class)
class BlendRepositoryTest {
    @Autowired
    lateinit var blends: BlendRepository

    private fun blend(
        id: String,
        owner: String = "owner",
        members: List<String> = listOf(owner, "friend"),
        createdAt: Instant = Instant.parse("2026-11-08T10:00:00Z"),
        tracks: List<BlendTrackEntity> = listOf(track("t1", "owner", "friend"), track("t2", "owner"), track("t3", "friend")),
    ) = BlendEntity(
        playlistId = id,
        playlistName = "Owner & Friend - Recent favourites",
        playlistUrl = "https://open.spotify.com/playlist/$id",
        ownerId = owner,
        timeRange = TimeRange.HALF_YEAR,
        size = 3,
        sharedMode = SharedMode.FILL,
        isShuffled = true,
        trackCount = tracks.size,
        createdAt = createdAt,
        updatedAt = createdAt,
        autoUpdate = true,
        lastAutoUpdateAt = null,
        lastAutoUpdateFailure = null,
        lastAutoUpdateDetail = null,
        members = members.map(::BlendMemberEntity),
        tracks = tracks,
    )

    private fun track(
        id: String,
        vararg who: String,
    ) = BlendTrackEntity(id, "spotify:track:$id", "Song $id", "Artist A\u001FArtist B", who.joinToString("\u001F"), null)

    @Test
    fun `a blend round-trips with its members and songs in order`() {
        val stored = blend("0123456789abcdefghijkl")
        blends.insert(stored)

        val loaded = blends.findByPlaylistIdAndOwnerId("0123456789abcdefghijkl", "owner")!!
        assertEquals(stored, loaded)
        assertEquals(listOf("owner", "friend"), loaded.toModel().memberIds)
        assertEquals(listOf("Artist A", "Artist B"), loaded.tracksToModel().first().artists)
        assertEquals(listOf("owner", "friend"), loaded.tracksToModel().first().contributors)
    }

    @Test
    fun `only the owner can load a blend`() {
        blends.insert(blend("0123456789abcdefghijkl"))
        assertNull(blends.findByPlaylistIdAndOwnerId("0123456789abcdefghijkl", "friend"))
    }

    @Test
    fun `saving replaces the songs`() {
        val stored = blends.insert(blend("0123456789abcdefghijkl"))
        blends.save(stored.copy(trackCount = 1, tracks = listOf(track("t9", "friend"))))

        val loaded = blends.findByPlaylistIdAndOwnerId("0123456789abcdefghijkl", "owner")!!
        assertEquals(listOf("t9"), loaded.tracks.map { it.trackId })
        assertEquals(1, loaded.trackCount)
    }

    @Test
    fun `an owner's blends come newest first`() {
        blends.insert(blend("aaaaaaaaaaaaaaaaaaaaaa", createdAt = Instant.parse("2026-11-01T10:00:00Z")))
        blends.insert(blend("bbbbbbbbbbbbbbbbbbbbbb", createdAt = Instant.parse("2026-11-05T10:00:00Z")))
        blends.insert(blend("cccccccccccccccccccccc", owner = "someone-else", members = listOf("someone-else")))

        assertEquals(
            listOf("bbbbbbbbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaaaaaaaa"),
            blends.findByOwnerIdOrderByCreatedAtDesc("owner").map { it.playlistId },
        )
    }

    @Test
    fun `forgetting someone deletes every blend they're in`() {
        blends.insert(blend("aaaaaaaaaaaaaaaaaaaaaa"))
        blends.insert(blend("bbbbbbbbbbbbbbbbbbbbbb", members = listOf("owner")))
        blends.insert(blend("cccccccccccccccccccccc", owner = "friend", members = listOf("friend")))

        blends.deleteInvolving("friend")

        assertEquals(listOf("bbbbbbbbbbbbbbbbbbbbbb"), blends.findAll().map { it.playlistId })
    }

    @Test
    fun `only blends with weekly updates on are picked up, oldest first`() {
        blends.insert(blend("aaaaaaaaaaaaaaaaaaaaaa", createdAt = Instant.parse("2026-11-05T10:00:00Z")))
        blends.insert(blend("bbbbbbbbbbbbbbbbbbbbbb", createdAt = Instant.parse("2026-11-01T10:00:00Z")))
        blends.insert(blend("cccccccccccccccccccccc").copy(autoUpdate = false))

        assertEquals(
            listOf("bbbbbbbbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaaaaaaaa"),
            blends.findByAutoUpdateTrueOrderByCreatedAtAsc().map { it.playlistId },
        )
    }

    @Test
    fun `the last weekly update result is stored`() {
        val stored = blends.insert(blend("0123456789abcdefghijkl"))
        val at = Instant.parse("2026-11-09T06:00:00Z")
        blends.save(
            stored.copy(lastAutoUpdateAt = at, lastAutoUpdateFailure = AutoUpdateFailure.RELOGIN_REQUIRED, lastAutoUpdateDetail = "friend"),
        )

        val loaded = blends.findByIdOrNull("0123456789abcdefghijkl")!!
        assertEquals(at, loaded.lastAutoUpdateAt)
        assertEquals(AutoUpdateFailure.RELOGIN_REQUIRED, loaded.lastAutoUpdateFailure)
        assertEquals("friend", loaded.lastAutoUpdateDetail)
        assertEquals(3, loaded.tracks.size)
    }
}
