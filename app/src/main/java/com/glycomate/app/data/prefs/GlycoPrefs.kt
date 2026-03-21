package com.glycomate.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.glycomate.app.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore("glycomate_prefs")

object PrefKeys {
    val ONBOARDING_DONE  = booleanPreferencesKey("onboarding_done")
    val USER_NAME        = stringPreferencesKey("user_name")
    val DIABETES_TYPE    = stringPreferencesKey("diabetes_type")
    val TARGET_LOW       = floatPreferencesKey("target_low")
    val TARGET_HIGH      = floatPreferencesKey("target_high")
    val ICR              = floatPreferencesKey("icr")
    val ISF              = floatPreferencesKey("isf")
    val BASAL_UNITS      = floatPreferencesKey("basal_units")
    val RAPID_BRAND      = stringPreferencesKey("rapid_insulin_brand")
    val LONG_BRAND       = stringPreferencesKey("long_insulin_brand")
    val CGM_SOURCE       = stringPreferencesKey("cgm_source")
    val LLU_EMAIL        = stringPreferencesKey("llu_email")
    val LLU_REGION       = stringPreferencesKey("llu_region")
    val NS_URL           = stringPreferencesKey("ns_url")
    val DEXCOM_USERNAME  = stringPreferencesKey("dexcom_username")
    val DEXCOM_REGION    = stringPreferencesKey("dexcom_region")
    val DIA              = floatPreferencesKey("dia")
    val ALERT_HYPO         = floatPreferencesKey("alert_hypo")
    val ALERT_HYPER        = floatPreferencesKey("alert_hyper")
    val ALERT_HYPO_ENABLED = booleanPreferencesKey("alert_hypo_enabled")
    val ALERT_HYPER_ENABLED= booleanPreferencesKey("alert_hyper_enabled")
    val WATCH_ENABLED      = booleanPreferencesKey("watch_enabled")
    val SOS_CONTACTS     = stringPreferencesKey("sos_contacts")
    val SOS_AUTO_TRIGGER = booleanPreferencesKey("sos_auto_trigger")
    val SOS_THRESHOLD    = floatPreferencesKey("sos_threshold")
    val OPENAI_KEY_UPDATED = longPreferencesKey("openai_key_updated")

    // Theme preference: "System" | "Light" | "Dark"
    val APP_THEME        = stringPreferencesKey("app_theme")
}

class GlycoPrefs(val context: Context) {
    val secure = SecureStorage(context)

    val onboardingDone: Flow<Boolean> = context.dataStore.data
        .map { it[PrefKeys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() = context.dataStore.edit {
        it[PrefKeys.ONBOARDING_DONE] = true
    }

    val appTheme: Flow<String> = context.dataStore.data
        .map { it[PrefKeys.APP_THEME] ?: "System" }

    suspend fun saveAppTheme(theme: String) = context.dataStore.edit {
        it[PrefKeys.APP_THEME] = theme
    }

    val userProfile: Flow<UserProfile> = context.dataStore.data.map { p ->
        UserProfile(
            name              = p[PrefKeys.USER_NAME]     ?: "",
            diabetesType      = p[PrefKeys.DIABETES_TYPE] ?: "Τύπος 1",
            targetLow         = p[PrefKeys.TARGET_LOW]    ?: 70f,
            targetHigh        = p[PrefKeys.TARGET_HIGH]   ?: 180f,
            icr               = p[PrefKeys.ICR]           ?: 10f,
            isf               = p[PrefKeys.ISF]           ?: 40f,
            basalUnits        = p[PrefKeys.BASAL_UNITS]   ?: 0f,
            rapidInsulinBrand = p[PrefKeys.RAPID_BRAND]   ?: "NovoRapid",
            longInsulinBrand  = p[PrefKeys.LONG_BRAND]    ?: "Lantus",
            dia               = p[PrefKeys.DIA]           ?: 4f
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) = context.dataStore.edit {
        it[PrefKeys.USER_NAME]     = profile.name
        it[PrefKeys.DIABETES_TYPE] = profile.diabetesType
        it[PrefKeys.TARGET_LOW]    = profile.targetLow
        it[PrefKeys.TARGET_HIGH]   = profile.targetHigh
        it[PrefKeys.ICR]           = profile.icr
        it[PrefKeys.ISF]           = profile.isf
        it[PrefKeys.BASAL_UNITS]   = profile.basalUnits
        it[PrefKeys.RAPID_BRAND]   = profile.rapidInsulinBrand
        it[PrefKeys.LONG_BRAND]    = profile.longInsulinBrand
        it[PrefKeys.DIA]           = profile.dia
    }

    val cgmSource: Flow<String> = context.dataStore.data
        .map { it[PrefKeys.CGM_SOURCE] ?: "NONE" }

    // ── LibreLinkUp ──────────────────────────────────────────────────────────
    val lluEmail: Flow<String> = context.dataStore.data.map { it[PrefKeys.LLU_EMAIL] ?: "" }
    val lluRegion: Flow<String> = context.dataStore.data.map { it[PrefKeys.LLU_REGION] ?: "EU" }
    val lluPassword: Flow<String> = flow { emit(withContext(Dispatchers.IO) { secure.lluPassword }) }
    val lluToken: Flow<String> = flow { emit(withContext(Dispatchers.IO) { secure.lluToken }) }
    val lluTokenExpiry: Flow<Long> = flow { emit(withContext(Dispatchers.IO) { secure.lluTokenExpiry }) }

    suspend fun saveLluCredentials(email: String, password: String, region: String) {
        context.dataStore.edit {
            it[PrefKeys.LLU_EMAIL]  = email
            it[PrefKeys.LLU_REGION] = region
            it[PrefKeys.CGM_SOURCE] = "LLU"
        }
        withContext(Dispatchers.IO) { secure.lluPassword = password }
    }

    suspend fun saveLluSession(token: String, expiry: Long, patientId: String) {
        withContext(Dispatchers.IO) {
            secure.lluToken       = token
            secure.lluTokenExpiry = expiry
            secure.lluPatientId   = patientId
        }
    }

    // ── Dexcom ───────────────────────────────────────────────────────────────
    val dexcomUsername: Flow<String> = context.dataStore.data.map { it[PrefKeys.DEXCOM_USERNAME] ?: "" }
    val dexcomRegion: Flow<String> = context.dataStore.data.map { it[PrefKeys.DEXCOM_REGION] ?: "OUS" }

    suspend fun getDexcomPassword(): String = withContext(Dispatchers.IO) { secure.dexcomPassword }

    suspend fun saveDexcomCredentials(username: String, pass: String, region: String) {
        context.dataStore.edit {
            it[PrefKeys.DEXCOM_USERNAME] = username
            it[PrefKeys.DEXCOM_REGION]   = region
            it[PrefKeys.CGM_SOURCE]      = "DEXCOM"
        }
        withContext(Dispatchers.IO) { secure.dexcomPassword = pass }
    }

    // ── Nightscout ────────────────────────────────────────────────────────────
    val nsUrl: Flow<String> = context.dataStore.data.map { it[PrefKeys.NS_URL] ?: "" }
    val nsSecret: Flow<String> = flow { emit(withContext(Dispatchers.IO) { secure.nsSecret }) }
    val nsToken: Flow<String> = flow { emit(withContext(Dispatchers.IO) { secure.nsToken }) }

    suspend fun saveNightscoutConfig(url: String, secret: String, token: String) {
        context.dataStore.edit {
            it[PrefKeys.NS_URL]     = url
            it[PrefKeys.CGM_SOURCE] = "NIGHTSCOUT"
        }
        withContext(Dispatchers.IO) {
            secure.nsSecret = secret
            secure.nsToken  = token
        }
    }

    val openAiKey: Flow<String> = context.dataStore.data.map { secure.openAiKey }

    suspend fun saveOpenAiKey(key: String) {
        withContext(Dispatchers.IO) { secure.openAiKey = key }
        context.dataStore.edit { it[PrefKeys.OPENAI_KEY_UPDATED] = System.currentTimeMillis() }
    }

    val alertHypo:         Flow<Float>   = context.dataStore.data.map { it[PrefKeys.ALERT_HYPO]          ?: 70f }
    val alertHyper:        Flow<Float>   = context.dataStore.data.map { it[PrefKeys.ALERT_HYPER]         ?: 250f }
    val alertHypoEnabled:  Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.ALERT_HYPO_ENABLED]  ?: true }
    val alertHyperEnabled: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.ALERT_HYPER_ENABLED] ?: true }
    val watchEnabled:      Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.WATCH_ENABLED]       ?: true }

    val sosContacts:    Flow<String>  = context.dataStore.data.map { it[PrefKeys.SOS_CONTACTS]     ?: "[]" }
    val sosAutoTrigger: Flow<Boolean> = context.dataStore.data.map { it[PrefKeys.SOS_AUTO_TRIGGER] ?: false }
    val sosThreshold:   Flow<Float>   = context.dataStore.data.map { it[PrefKeys.SOS_THRESHOLD]    ?: 55f }

    suspend fun saveSosContacts(json: String) = context.dataStore.edit {
        it[PrefKeys.SOS_CONTACTS] = json
    }

    suspend fun saveSosSettings(autoTrigger: Boolean, threshold: Float) = context.dataStore.edit {
        it[PrefKeys.SOS_AUTO_TRIGGER] = autoTrigger
        it[PrefKeys.SOS_THRESHOLD]    = threshold
    }

    suspend fun clearCgm() {
        context.dataStore.edit { it[PrefKeys.CGM_SOURCE] = "NONE" }
        withContext(Dispatchers.IO) { secure.clearAll() }
    }
}
