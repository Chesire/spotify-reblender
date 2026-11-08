package dev.chesire.reblender.playlists

import dev.chesire.reblender.integrations.spotify.ReloginRequiredException
import dev.chesire.reblender.playlists.data.JobRunEntity
import dev.chesire.reblender.playlists.data.JobRunRepository
import dev.chesire.reblender.playlists.models.AutoUpdateFailure
import dev.chesire.reblender.playlists.models.Blend
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rebuilds every blend that has weekly updates on, on the schedule in `reblender.auto-update.cron`
 * (Mondays 06:00 UK time by default). Blends are done one at a time so token refreshes never race, and one
 * failing blend never stops the rest, except Spotify's rate limit, which ends the run until next week.
 * If the app was down when a run was due, it catches up a minute after starting.
 */
@Component
class WeeklyUpdates(
    private val playlists: PlaylistService,
    private val runs: JobRunRepository,
    private val scheduler: TaskScheduler,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    data class Summary(
        val updated: Int,
        val failed: Int,
        val skipped: Int,
    )

    @Scheduled(cron = "\${reblender.auto-update.cron}", zone = "\${reblender.auto-update.zone}")
    fun scheduledRun() {
        runAll()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun catchUpIfMissed() {
        if (isCatchUpDue(Instant.now())) {
            log.info("Weekly update: the last run was over a week ago, catching up in a minute")
            scheduler.schedule({ runAll() }, Instant.now().plus(CATCH_UP_DELAY))
        }
    }

    /** True if the last run was over a week before [now]. The first start ever just records [now], so nothing runs early. */
    fun isCatchUpDue(now: Instant): Boolean {
        val last = runs.findByIdOrNull(JOB)?.lastRunAt
        if (last == null) {
            runs.insert(JobRunEntity(JOB, now))
            return false
        }
        return Duration.between(last, now) > Duration.ofDays(7).plus(GRACE)
    }

    fun runAll(): Summary {
        if (!running.compareAndSet(false, true)) {
            log.info("Weekly update: already running, skipping")
            return Summary(0, 0, 0)
        }
        try {
            val started = Instant.now()
            val due = playlists.autoUpdating()
            var updated = 0
            var failed = 0
            for ((index, blend) in due.withIndex()) {
                val failure = updateOne(blend)
                if (failure == null) updated++ else failed++
                if (failure?.first == AutoUpdateFailure.SPOTIFY_LIMIT) {
                    val skipped = due.size - index - 1
                    log.warn("Weekly update: Spotify's limit was hit, skipping the remaining {} blend(s) until next week", skipped)
                    return finish(started, Summary(updated, failed, skipped))
                }
            }
            return finish(started, Summary(updated, failed, 0))
        } finally {
            running.set(false)
        }
    }

    private fun finish(
        started: Instant,
        summary: Summary,
    ): Summary {
        val entity = JobRunEntity(JOB, started)
        if (runs.existsById(JOB)) runs.save(entity) else runs.insert(entity)
        log.info("Weekly update: {} updated, {} failed, {} skipped", summary.updated, summary.failed, summary.skipped)
        return summary
    }

    /** Updates one blend and records the outcome. Returns why it failed, or null if it worked. */
    private fun updateOne(blend: Blend): Pair<AutoUpdateFailure, String?>? {
        val failure = try {
            playlists.update(blend.ownerId, blend.playlistId)
            null
        } catch (e: ReloginRequiredException) {
            AutoUpdateFailure.RELOGIN_REQUIRED to e.spotifyId
        } catch (e: MemberOptedOutException) {
            AutoUpdateFailure.MEMBER_OPTED_OUT to e.spotifyId
        } catch (e: RestClientResponseException) {
            when (e.statusCode.value()) {
                404 -> AutoUpdateFailure.PLAYLIST_GONE to null
                429 -> AutoUpdateFailure.SPOTIFY_LIMIT to null
                else -> AutoUpdateFailure.SPOTIFY_ERROR to e.statusCode.value().toString()
            }
        } catch (e: IllegalArgumentException) {
            AutoUpdateFailure.OTHER to e.message
        } catch (e: RuntimeException) {
            log.warn("Weekly update of {} failed unexpectedly", blend.playlistId, e)
            AutoUpdateFailure.OTHER to e.javaClass.simpleName
        }
        if (failure != null) log.info("Weekly update of {} failed: {}", blend.playlistId, failure.first)
        playlists.recordAutoUpdate(blend.playlistId, Instant.now(), failure?.first, failure?.second)
        return failure
    }

    companion object {
        const val JOB = "weekly-update"
        private val CATCH_UP_DELAY: Duration = Duration.ofMinutes(1)

        /** Slack so a run that finished a little late last week doesn't trigger a catch-up just before this week's. */
        private val GRACE: Duration = Duration.ofHours(6)
    }
}
