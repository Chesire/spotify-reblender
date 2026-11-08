package dev.chesire.reblender.playlists

import dev.chesire.reblender.blend.models.SharedMode
import dev.chesire.reblender.blend.models.TimeRange
import dev.chesire.reblender.integrations.spotify.ReloginRequiredException
import dev.chesire.reblender.playlists.data.JobRunEntity
import dev.chesire.reblender.playlists.data.JobRunRepository
import dev.chesire.reblender.playlists.models.AutoUpdateFailure
import dev.chesire.reblender.playlists.models.Blend
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.web.client.HttpClientErrorException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklyUpdatesTest {
    private val playlists = mock(PlaylistService::class.java)
    private val runs = mock(JobRunRepository::class.java)
    private val job = WeeklyUpdates(playlists, runs, mock(TaskScheduler::class.java))

    private fun blend(id: String) = Blend(
        playlistId = id,
        playlistName = id,
        playlistUrl = "https://open.spotify.com/playlist/$id",
        ownerId = "owner",
        memberIds = listOf("owner", "friend"),
        timeRange = TimeRange.RECENT,
        size = 10,
        sharedMode = SharedMode.CAPPED,
        trackCount = 10,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun spotifyError(status: HttpStatus) =
        HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, ByteArray(0), Charsets.UTF_8)

    /** playlistId -> (failure, detail) for every recordAutoUpdate call. */
    private fun recorded(): Map<String, Pair<Any?, Any?>> = mockingDetails(playlists).invocations
        .filter { it.method.name == "recordAutoUpdate" }
        .associate { (it.arguments[0] as String) to (it.arguments[2] to it.arguments[3]) }

    private fun updateCalls() = mockingDetails(playlists).invocations.filter { it.method.name == "update" }.map { it.arguments[1] }

    @Test
    fun `one failing blend doesn't stop the others, and every outcome is recorded`() {
        `when`(playlists.autoUpdating()).thenReturn(listOf(blend("a"), blend("b"), blend("c")))
        doThrow(ReloginRequiredException("friend")).`when`(playlists).update("owner", "a")
        doThrow(MemberOptedOutException("friend", "opted out")).`when`(playlists).update("owner", "c")

        val summary = job.runAll()

        assertEquals(WeeklyUpdates.Summary(updated = 1, failed = 2, skipped = 0), summary)
        assertEquals(
            mapOf(
                "a" to (AutoUpdateFailure.RELOGIN_REQUIRED to "friend"),
                "b" to (null to null),
                "c" to (AutoUpdateFailure.MEMBER_OPTED_OUT to "friend"),
            ),
            recorded(),
        )
    }

    @Test
    fun `hitting Spotify's limit ends the run`() {
        `when`(playlists.autoUpdating()).thenReturn(listOf(blend("a"), blend("b"), blend("c")))
        doThrow(spotifyError(HttpStatus.TOO_MANY_REQUESTS)).`when`(playlists).update("owner", "b")

        val summary = job.runAll()

        assertEquals(WeeklyUpdates.Summary(updated = 1, failed = 1, skipped = 1), summary)
        assertEquals(listOf("a", "b"), updateCalls())
        assertEquals(AutoUpdateFailure.SPOTIFY_LIMIT, recorded().getValue("b").first)
    }

    @Test
    fun `a playlist Spotify no longer has is reported as gone`() {
        `when`(playlists.autoUpdating()).thenReturn(listOf(blend("a")))
        doThrow(spotifyError(HttpStatus.NOT_FOUND)).`when`(playlists).update("owner", "a")

        job.runAll()

        assertEquals(AutoUpdateFailure.PLAYLIST_GONE, recorded().getValue("a").first)
    }

    @Test
    fun `a missed run is caught up, but the very first start doesn't run anything`() {
        val now = Instant.parse("2026-11-12T12:00:00Z")
        assertFalse(job.isCatchUpDue(now), "first start just records the time")

        `when`(runs.findById(WeeklyUpdates.JOB)).thenReturn(Optional.of(JobRunEntity(WeeklyUpdates.JOB, now.minus(Duration.ofDays(2)))))
        assertFalse(job.isCatchUpDue(now))

        `when`(runs.findById(WeeklyUpdates.JOB)).thenReturn(Optional.of(JobRunEntity(WeeklyUpdates.JOB, now.minus(Duration.ofDays(8)))))
        assertTrue(job.isCatchUpDue(now))
    }

    @Test
    fun `the default schedule is Monday 06:00 UK time`() {
        val next = CronExpression.parse("0 0 6 * * MON").next(LocalDateTime.of(2026, 11, 8, 12, 0))!!
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 0), next)
    }
}
