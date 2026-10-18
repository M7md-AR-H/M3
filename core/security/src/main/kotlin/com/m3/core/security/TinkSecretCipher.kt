package com.m3.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * AES256-GCM keyset whose master key lives in the Android Keystore (ADR 0004).
 * The wrapped keyset is kept in an app-private prefs file; backups are disabled for the whole app.
 *
 * Construction touches the Keystore, so build this off the main thread.
 */
class TinkSecretCipher(context: Context) : SecretCipher {
    private val aead: Aead

    init {
        AeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREFS_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        aead = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)

    private companion object {
        const val MASTER_KEY_URI = "android-keystore://m3_master_key"
        const val KEYSET_NAME = "m3_keyset"
        const val PREFS_FILE = "m3_keyset_prefs"
    }
}
