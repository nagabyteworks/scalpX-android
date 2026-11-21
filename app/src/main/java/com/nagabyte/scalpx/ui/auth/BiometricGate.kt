package com.nagabyte.scalpx.ui.auth

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Biometric gate protecting the application from unintended taps when returning from background.
 * Locks the application if it has been backgrounded for more than 60 seconds.
 */
class BiometricGate(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var lastBackgroundTimestamp = 0L

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = clock()
    }

    fun onAppForegrounded(activity: FragmentActivity, onUnlocked: () -> Unit = {}) {
        val now = clock()
        val elapsed = now - lastBackgroundTimestamp

        if (lastBackgroundTimestamp > 0L && elapsed > timeoutMillis) {
            _isLocked.value = true
            promptUnlock(activity) { success ->
                if (success) {
                    onUnlocked()
                }
            }
        }
    }

    fun promptUnlock(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuthenticate = biometricManager.canAuthenticate(authenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // If device has no security credentials set, unlock directly to avoid trapping user
            _isLocked.value = false
            lastBackgroundTimestamp = 0L
            onResult(true)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                _isLocked.value = false
                lastBackgroundTimestamp = 0L
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    _isLocked.value = true
                }
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometric not recognized, prompt remains open
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("scalpX Security Lock")
            .setSubtitle("Authenticate to access 1-click execution cockpit")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    fun forceLock() {
        _isLocked.value = true
    }

    fun resetLock() {
        _isLocked.value = false
        lastBackgroundTimestamp = 0L
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L // 60 seconds
    }
}
