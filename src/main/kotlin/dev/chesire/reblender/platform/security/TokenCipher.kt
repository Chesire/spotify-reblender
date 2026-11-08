package dev.chesire.reblender.platform.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when stored ciphertext can't be decrypted (wrong key or tampering). */
class TokenDecryptionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * AES-256-GCM encryption for OAuth tokens at rest.
 *
 * Format: `[version=1][12-byte random IV][ciphertext + 16-byte tag]`.
 * Associated data binds each ciphertext to its row (registration id + Spotify user id),
 * so a token copied into another user's row fails to decrypt.
 */
class TokenCipher(
    base64Key: String,
) {
    private val key: SecretKeySpec
    private val random = SecureRandom()

    init {
        require(base64Key.isNotBlank()) {
            "TOKEN_ENCRYPTION_KEY (or TOKEN_ENCRYPTION_KEY_FILE) is not set. Generate a key with: openssl rand -base64 32"
        }
        val bytes = try {
            Base64.getDecoder().decode(base64Key.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("TOKEN_ENCRYPTION_KEY must be base64 (openssl rand -base64 32)", e)
        }
        require(bytes.size == KEY_BYTES) {
            "TOKEN_ENCRYPTION_KEY must decode to $KEY_BYTES bytes (got ${bytes.size}). Use: openssl rand -base64 32"
        }
        key = SecretKeySpec(bytes, "AES")
    }

    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(associatedData)
        }
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(
        stored: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        if (stored.size < 1 + IV_BYTES + TAG_BITS / 8 || stored[0] != VERSION) {
            throw TokenDecryptionException("Stored token is not in the encrypted format")
        }
        val iv = stored.copyOfRange(1, 1 + IV_BYTES)
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(associatedData)
                doFinal(stored, 1 + IV_BYTES, stored.size - 1 - IV_BYTES)
            }
        } catch (e: GeneralSecurityException) {
            throw TokenDecryptionException("Stored token could not be decrypted (wrong key or tampered data)", e)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        fun associatedData(
            registrationId: String,
            principalName: String,
        ): ByteArray = "$registrationId\u0000$principalName".toByteArray(Charsets.UTF_8)
    }
}
