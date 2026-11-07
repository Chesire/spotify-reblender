package dev.chesire.reblender.security

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenCipherTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private val aad = TokenCipher.associatedData("spotify", "user-a")
    private val secret = "BQD-access-token-value".toByteArray()

    @Test
    fun `round trips a token`() {
        val cipher = TokenCipher(key)
        assertContentEquals(secret, cipher.decrypt(cipher.encrypt(secret, aad), aad))
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val stored = TokenCipher(key).encrypt(secret, aad)
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("access-token"))
    }

    @Test
    fun `same token encrypts differently each time`() {
        val cipher = TokenCipher(key)
        assertFalse(cipher.encrypt(secret, aad).contentEquals(cipher.encrypt(secret, aad)))
    }

    @Test
    fun `wrong key fails`() {
        val stored = TokenCipher(key).encrypt(secret, aad)
        assertFailsWith<TokenDecryptionException> { TokenCipher(otherKey).decrypt(stored, aad) }
    }

    @Test
    fun `token moved to another user's row fails`() {
        val cipher = TokenCipher(key)
        val stored = cipher.encrypt(secret, aad)
        assertFailsWith<TokenDecryptionException> {
            cipher.decrypt(stored, TokenCipher.associatedData("spotify", "user-b"))
        }
    }

    @Test
    fun `tampered ciphertext fails`() {
        val cipher = TokenCipher(key)
        val stored = cipher.encrypt(secret, aad)
        stored[stored.size - 1] = (stored[stored.size - 1].toInt() xor 1).toByte()
        assertFailsWith<TokenDecryptionException> { cipher.decrypt(stored, aad) }
    }

    @Test
    fun `legacy plaintext is rejected`() {
        assertFailsWith<TokenDecryptionException> { TokenCipher(key).decrypt(secret, aad) }
    }

    @Test
    fun `missing or short keys are rejected`() {
        assertFailsWith<IllegalArgumentException> { TokenCipher("") }
        assertFailsWith<IllegalArgumentException> { TokenCipher(Base64.getEncoder().encodeToString(ByteArray(16))) }
        assertFailsWith<IllegalArgumentException> { TokenCipher("not base64 !!") }
    }

    @Test
    fun `stored format starts with a version byte`() {
        assertTrue(TokenCipher(key).encrypt(secret, aad)[0] == 1.toByte())
    }
}
