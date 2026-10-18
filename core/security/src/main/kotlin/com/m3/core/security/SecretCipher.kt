package com.m3.core.security

/** Authenticated encryption for small secrets. The production implementation is [TinkSecretCipher]. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** Throws [java.security.GeneralSecurityException] if the ciphertext was changed or the key is wrong. */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}
