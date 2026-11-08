package dev.chesire.reblender.platform.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptionKeySourceTest {
    @Test
    fun `uses the inline key when no file is set`() {
        assertEquals("inline", EncryptionKeySource.resolve("inline", ""))
    }

    @Test
    fun `reads and trims the key file`() {
        val file = Files.createTempFile("key", ".txt")
        Files.writeString(file, "  file-key\n")
        assertEquals("file-key", EncryptionKeySource.resolve("", file.toString()))
    }

    @Test
    fun `rejects both being set`() {
        assertFailsWith<IllegalArgumentException> { EncryptionKeySource.resolve("inline", "/tmp/whatever") }
    }

    @Test
    fun `rejects a missing file`() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionKeySource.resolve("", "/definitely/not/here/key")
        }
    }

    @Test
    fun `returns blank when neither is set so TokenCipher reports it`() {
        assertEquals("", EncryptionKeySource.resolve("", ""))
    }
}
