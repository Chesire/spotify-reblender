package dev.chesire.reblender.platform.data

import org.springframework.data.jdbc.core.JdbcAggregateOperations

/**
 * Repository fragment for aggregates whose ID is assigned up front (Spotify IDs) rather than generated.
 * Spring Data's `save` treats an entity with an ID as existing and issues an UPDATE, so new ones use [insert].
 */
interface InsertableRepository<T : Any> {
    fun insert(entity: T): T
}

class InsertableRepositoryImpl<T : Any>(
    private val template: JdbcAggregateOperations,
) : InsertableRepository<T> {
    override fun insert(entity: T): T = template.insert(entity)
}
