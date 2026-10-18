package com.m3.core.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

sealed interface UnlockResult {
    data object Success : UnlockResult

    /** The owner closed the prompt or it failed; they can try again. */
    data class Failed(val message: String) : UnlockResult

    /** The phone has no screen lock (PIN, pattern, password), so the app can't be locked. */
    data object NoScreenLock : UnlockResult
}

/**
 * Shows the system unlock prompt: fingerprint/face (strong biometrics) or the phone's screen-lock PIN.
 * Uses the platform [BiometricPrompt] (API 30+), so no extra library is needed.
 */
class UnlockPrompt(private val activity: Activity) {
    private var cancellation: CancellationSignal? = null

    val isShowing: Boolean get() = cancellation != null

    fun canUnlock(): Boolean =
        activity.getSystemService(BiometricManager::class.java)
            .canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun show(title: String, subtitle: String, onResult: (UnlockResult) -> Unit) {
        if (isShowing) return
        if (!canUnlock()) {
            onResult(UnlockResult.NoScreenLock)
            return
        }
        val signal = CancellationSignal()
        cancellation = signal
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(
            signal,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    cancellation = null
                    onResult(UnlockResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    cancellation = null
                    onResult(UnlockResult.Failed(errString.toString()))
                }
                // onAuthenticationFailed (one wrong finger) keeps the prompt open; nothing to do.
            },
        )
    }

    fun cancel() {
        cancellation?.cancel()
        cancellation = null
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
