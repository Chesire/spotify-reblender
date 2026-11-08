package dev.chesire.reblender.accounts.data

import dev.chesire.reblender.platform.data.JdbcMappingConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Runs the real repository against H2 with schema.sql. */
@DataJdbcTest
@Import(JdbcMappingConfig::class)
class UserRepositoryTest {
    @Autowired
    lateinit var users: UserRepository

    private val now = Instant.parse("2026-11-08T10:00:00Z")

    @Test
    fun `users are listed by name and can be updated`() {
        users.insert(UserEntity("b", "Sam", null, allowBlending = true, lastLoginAt = now))
        users.insert(UserEntity("a", "Alex", "https://i.scdn.co/image/a", allowBlending = true, lastLoginAt = now))

        assertEquals(listOf("Alex", "Sam"), users.findAllByOrderByDisplayNameAsc().map { it.displayName })

        users.save(users.findByIdOrNull("b")!!.copy(allowBlending = false))
        assertFalse(users.findByIdOrNull("b")!!.allowBlending)
    }
}
