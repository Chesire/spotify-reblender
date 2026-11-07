package dev.chesire.reblender.security

import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the token encryption key comes from:
 * - `TOKEN_ENCRYPTION_KEY`: the base64 key itself (simplest; lives in `.env`).
 * - `TOKEN_ENCRYPTION_KEY_FILE`: a path to a file containing it. Use this with Docker secrets,
 *   or with a TPM-sealed systemd credential so the key only decrypts on this machine.
 */
object EncryptionKeySource {

    fun resolve(inlineKey: String, keyFile: String): String {
        val hasInline = inlineKey.isNotBlank()
        val hasFile = keyFile.isNotBlank()
        require(!(hasInline && hasFile)) {
            "Set either TOKEN_ENCRYPTION_KEY or TOKEN_ENCRYPTION_KEY_FILE, not both"
        }
        if (!hasFile) return inlineKey

        val path = Path.of(keyFile.trim())
        require(Files.isRegularFile(path) && Files.isReadable(path)) {
            "TOKEN_ENCRYPTION_KEY_FILE ($keyFile) doesn't exist or isn't readable by the app user"
        }
        return Files.readString(path).trim()
    }
}
