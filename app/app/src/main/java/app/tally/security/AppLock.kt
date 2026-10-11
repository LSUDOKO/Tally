package app.tally.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The phone's own lock (fingerprint, face or PIN) in front of the app and in front of every payment
 * the owner taps. Framework BiometricPrompt, so there is nothing to add and nothing to configure:
 * whatever the phone already trusts, Tally trusts.
 */
object AppLock {
    /** Whether the phone has any lock Tally could ask for. */
    fun available(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isDeviceSecure == true) return true
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    /** Shows the prompt and resolves true once the owner passes it; false if they cancel or fail. */
    suspend fun confirm(context: Context, title: String, subtitle: String? = null): Boolean = suspendCancellableCoroutine { cont ->
        val builder = BiometricPrompt.Builder(context).setTitle(title).setConfirmationRequired(false)
        if (subtitle != null) builder.setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        builder.build().authenticate(cancel, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { if (cont.isActive) cont.resume(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { if (cont.isActive) cont.resume(false) }
        })
    }
}
