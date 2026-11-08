package dev.chesire.reblender.playlists.data

import dev.chesire.reblender.platform.data.InsertableRepository
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.ListCrudRepository
import java.time.Instant

@Table("job_run")
data class JobRunEntity(
    @Id val name: String,
    val lastRunAt: Instant,
)

interface JobRunRepository :
    ListCrudRepository<JobRunEntity, String>,
    InsertableRepository<JobRunEntity>
