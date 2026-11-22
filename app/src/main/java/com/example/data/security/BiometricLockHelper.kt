package com.example.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local device lock helper backed by Android BiometricPrompt (fingerprint / FaceID / device PIN).
 * Respects the user-configurable toggle in Administración and gracefully bypasses lock on devices
 * with no enrolled screen lock credentials.
 */
object BiometricLockHelper {
  private const val PREFS_NAME = "mis_finanzas_security_prefs"
  private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"

  private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.DEVICE_CREDENTIAL

  private val _isEnabledFlow = MutableStateFlow(false)

  fun isBiometricLockEnabled(context: Context): Boolean {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
    _isEnabledFlow.value = enabled
    return enabled
  }

  fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled).apply()
    _isEnabledFlow.value = enabled
  }

  fun observeBiometricLockEnabled(context: Context): StateFlow<Boolean> {
    isBiometricLockEnabled(context)
    return _isEnabledFlow.asStateFlow()
  }

  fun canAuthenticate(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
  }

  fun shouldRequireLockOnLaunch(activity: FragmentActivity): Boolean {
    return isBiometricLockEnabled(activity) && canAuthenticate(activity)
  }

  fun authenticate(
    activity: FragmentActivity,
    title: String = "Desbloquear Mis Finanzas",
    subtitle: String = "Confirma tu identidad para acceder a tus finanzas",
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
  ) {
    if (!canAuthenticate(activity)) {
      onSuccess()
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
      BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onFailure(errString.toString())
          }
        },
      )

    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()

    prompt.authenticate(info)
  }
}
