package com.m3.core.security

import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabasePassphraseTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** XOR "cipher" with an integrity check on the associated data; enough to test the wrapping logic. */
    private class FakeCipher(private val key: Byte) : SecretCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            associatedData.size.toByte().let { byteArrayOf(it) } + plaintext.map { (it.toInt() xor key.toInt()).toByte() }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            if (ciphertext.isEmpty() || ciphertext[0] != associatedData.size.toByte() || key == WRONG) {
                throw GeneralSecurityException("bad")
            }
            return ciphertext.drop(1).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
        }

        companion object {
            const val WRONG: Byte = 0
        }
    }

    private fun file() = File(folder.root, "no_backup/db.key")

    @Test
    fun `creates 32 bytes once and returns the same bytes later`() {
        val first = DatabasePassphrase(file(), FakeCipher(0x5A), SecureRandom()).getOrCreate()
        val second = DatabasePassphrase(file(), FakeCipher(0x5A), SecureRandom()).getOrCreate()
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun `stores only ciphertext`() {
        val passphrase = DatabasePassphrase(file(), FakeCipher(0x5A)).getOrCreate()
        val stored = file().readBytes()
        assertFalse(stored.toList().windowed(passphrase.size).any { it == passphrase.toList() })
        assertFalse(File(file().parentFile, "db.key.tmp").exists())
    }

    @Test
    fun `undecryptable key file throws and is left untouched`() {
        DatabasePassphrase(file(), FakeCipher(0x5A)).getOrCreate()
        val before = file().readBytes()
        assertThrows(GeneralSecurityException::class.java) {
            DatabasePassphrase(file(), FakeCipher(FakeCipher.WRONG)).getOrCreate()
        }
        assertArrayEquals(before, file().readBytes())
    }
}
