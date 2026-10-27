package com.uwapo.app.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionStore(context: Context) {
    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "uwapo_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) { null }

    val secureStorageAvailable: Boolean get() = prefs != null
    fun accessToken(): String = prefs?.getString("access_token", "").orEmpty()
    fun refreshToken(): String = prefs?.getString("refresh_token", "").orEmpty()
    fun userEmail(): String = prefs?.getString("email", "").orEmpty()
    fun clubId(): String = prefs?.getString("club_id", "").orEmpty()
    fun accountId(): String = prefs?.getString("account_id", "").orEmpty()
    fun saveSession(access: String, refresh: String, email: String) {
        prefs?.edit()?.putString("access_token", access)?.putString("refresh_token", refresh)
            ?.putString("email", email)?.apply()
    }
    fun saveClubConfig(clubId: String, accountId: String) {
        prefs?.edit()?.putString("club_id", clubId.trim())?.putString("account_id", accountId.trim())?.apply()
    }
    fun clearSession() {
        prefs?.edit()?.remove("access_token")?.remove("refresh_token")?.remove("email")?.apply()
    }
}
