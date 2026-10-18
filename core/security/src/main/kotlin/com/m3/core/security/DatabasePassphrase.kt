package com.m3.core.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom

/**
 * The SQLCipher passphrase: 32 random bytes made once, stored only in encrypted form in [file]
 * (normally `noBackupFilesDir/db.key`).
 */
class DatabasePassphrase(
    private val file: File,
    private val cipher: SecretCipher,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Returns the plaintext passphrase, creating it on first use. The caller owns the returned array.
     *
     * Never replaces an existing file: if it can't be decrypted this throws, because a new key would make
     * the existing database unreadable.
     */
    @Synchronized
    fun getOrCreate(): ByteArray {
        if (file.exists()) {
            return cipher.decrypt(file.readBytes(), ASSOCIATED_DATA)
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(random::nextBytes)
        writeAtomically(cipher.encrypt(passphrase, ASSOCIATED_DATA))
        return passphrase
    }

    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not store the database key")
        }
    }

    companion object {
        const val PASSPHRASE_BYTES = 32

        /** Binds the ciphertext to its purpose, so it can't be swapped for another secret. */
        private val ASSOCIATED_DATA = "m3-db-passphrase-v1".toByteArray()
    }
}
