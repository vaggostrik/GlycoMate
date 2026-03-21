package com.glycomate.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG  = "SecureStorage"
private const val FILE = "glycomate_secure"

/**
 * Stores sensitive credentials (passwords, tokens) in EncryptedSharedPreferences
 * backed by Android Keystore. Values are AES-256-GCM encrypted at rest.
 *
 * Non-sensitive settings (email, region, URLs, profile) remain in plain DataStore.
 */
class SecureStorage(context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPrefs if encryption init fails
        // (e.g. on emulators without proper Keystore support)
        Log.w(TAG, "EncryptedSharedPreferences init failed, using plain prefs", e)
        context.getSharedPreferences("${FILE}_fallback", Context.MODE_PRIVATE)
    }

    // ── LibreLinkUp ───────────────────────────────────────────────────────────
    var lluPassword: String
        get()     = prefs.getString(KEY_LLU_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LLU_PASSWORD, value).apply()

    var lluToken: String
        get()     = prefs.getString(KEY_LLU_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LLU_TOKEN, value).apply()

    var lluTokenExpiry: Long
        get()     = prefs.getLong(KEY_LLU_TOKEN_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_LLU_TOKEN_EXPIRY, value).apply()

    var lluPatientId: String
        get()     = prefs.getString(KEY_LLU_PATIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LLU_PATIENT_ID, value).apply()

    // ── Nightscout ────────────────────────────────────────────────────────────
    var nsSecret: String
        get()     = prefs.getString(KEY_NS_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NS_SECRET, value).apply()

    var nsToken: String
        get()     = prefs.getString(KEY_NS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NS_TOKEN, value).apply()

    var dexcomPassword: String
        get()     = prefs.getString(KEY_DEXCOM_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEXCOM_PASSWORD, value).apply()

    // ── OpenAI ────────────────────────────────────────────────────────────────
    var openAiKey: String
        get()     = prefs.getString(KEY_OPENAI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENAI_KEY, value).apply()

    // ── Clear all (logout) ────────────────────────────────────────────────────
    fun clearLlu() {
        prefs.edit()
            .remove(KEY_LLU_PASSWORD)
            .remove(KEY_LLU_TOKEN)
            .remove(KEY_LLU_TOKEN_EXPIRY)
            .remove(KEY_LLU_PATIENT_ID)
            .apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_LLU_PASSWORD    = "llu_password"
        private const val KEY_LLU_TOKEN       = "llu_token"
        private const val KEY_LLU_TOKEN_EXPIRY = "llu_token_expiry"
        private const val KEY_LLU_PATIENT_ID  = "llu_patient_id"
        private const val KEY_NS_SECRET       = "ns_secret"
        private const val KEY_NS_TOKEN        = "ns_token"
        private const val KEY_DEXCOM_PASSWORD = "dexcom_password"
        private const val KEY_OPENAI_KEY      = "openai_api_key"
    }
}
