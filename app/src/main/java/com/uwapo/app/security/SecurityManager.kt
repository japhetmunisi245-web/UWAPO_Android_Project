package com.uwapo.app.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class SecurityManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "uwapo_sec_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("uwapo_sec_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    init {
        if (isAppLockEnabled() && !getPinHash().isNullOrBlank()) {
            _isLocked.value = true
        }
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (enabled && getPinHash().isNullOrBlank()) {
            // Needs PIN set
        } else if (!enabled) {
            _isLocked.value = false
        }
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isFlagSecureEnabled(): Boolean = prefs.getBoolean(KEY_FLAG_SECURE_ENABLED, true)

    fun setFlagSecureEnabled(enabled: Boolean, activity: Activity? = null) {
        prefs.edit().putBoolean(KEY_FLAG_SECURE_ENABLED, enabled).apply()
        activity?.let { applyFlagSecure(it) }
    }

    fun getPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    fun setPin(pin: String) {
        val salt = getOrCreateSalt()
        val hash = hashPin(pin, salt)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
        setAppLockEnabled(true)
        _isLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = getPinHash() ?: return false
        val salt = getOrCreateSalt()
        val computedHash = hashPin(pin, salt)
        return computedHash == savedHash
    }

    fun unlockWithPin(pin: String): Boolean {
        return if (verifyPin(pin)) {
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun unlockWithBiometrics() {
        _isLocked.value = false
    }

    fun lockApp() {
        if (isAppLockEnabled() && !getPinHash().isNullOrBlank()) {
            _isLocked.value = true
        }
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).putBoolean(KEY_APP_LOCK_ENABLED, false).apply()
        _isLocked.value = false
    }

    fun isSqlCipherEnabled(): Boolean = prefs.getBoolean(KEY_SQLCIPHER_ENABLED, false)

    fun getDatabasePassphrase(): String? = prefs.getString(KEY_DATABASE_PASSPHRASE, null)

    fun setDatabasePassphrase(passphrase: String?) {
        if (passphrase.isNullOrBlank()) {
            prefs.edit().remove(KEY_DATABASE_PASSPHRASE).putBoolean(KEY_SQLCIPHER_ENABLED, false).apply()
        } else {
            prefs.edit().putString(KEY_DATABASE_PASSPHRASE, passphrase).putBoolean(KEY_SQLCIPHER_ENABLED, true).apply()
        }
    }

    fun applyFlagSecure(activity: Activity) {
        if (isFlagSecureEnabled() || _isLocked.value) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun canAuthenticateWithBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Unlock UWAPO",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlockWithBiometrics()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    private fun getOrCreateSalt(): String {
        var salt = prefs.getString(KEY_PIN_SALT, null)
        if (salt == null) {
            val random = SecureRandom()
            val saltBytes = ByteArray(16)
            random.nextBytes(saltBytes)
            salt = Base64.getEncoder().encodeToString(saltBytes)
            prefs.edit().putString(KEY_PIN_SALT, salt).apply()
        }
        return salt
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FLAG_SECURE_ENABLED = "flag_secure_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_SQLCIPHER_ENABLED = "sqlcipher_enabled"
        private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
    }
}
