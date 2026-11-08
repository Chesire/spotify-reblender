package dev.chesire.reblender.accounts.data

import dev.chesire.reblender.platform.data.InsertableRepository
import org.springframework.data.repository.ListCrudRepository

interface UserRepository :
    ListCrudRepository<UserEntity, String>,
    InsertableRepository<UserEntity> {
    fun findAllByOrderByDisplayNameAsc(): List<UserEntity>
}
