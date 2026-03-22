package com.glycomate.app.data.repository

import android.content.Context
import android.util.Log
import com.glycomate.app.data.cgm.libre.LibreLinkUpClient
import com.glycomate.app.data.cgm.libre.LluRegion
import com.glycomate.app.data.cgm.libre.LluResult
import com.glycomate.app.data.cgm.dexcom.DexcomShareClient
import com.glycomate.app.data.cgm.dexcom.DexcomRegion
import com.glycomate.app.data.cgm.dexcom.DexcomResult
import com.glycomate.app.data.cgm.nightscout.NightscoutClient
import com.glycomate.app.data.cgm.nightscout.NsResult
import com.glycomate.app.data.db.GlycoDatabase
import com.glycomate.app.data.model.*
import com.glycomate.app.data.prefs.GlycoPrefs
import kotlinx.coroutines.flow.*
import java.util.Calendar

private const val TAG = "GlycoRepository"

sealed class CgmSyncResult {
    data class Success(val reading: GlucoseReading) : CgmSyncResult()
    data class Error(val message: String)            : CgmSyncResult()
    object NoSourceConfigured                         : CgmSyncResult()
}

class GlycoRepository(private val context: Context) {

    private val db  = GlycoDatabase.getInstance(context)
    val prefs       = GlycoPrefs(context)

    // Keep one LLU client instance per region to reuse the session
    private var lluClient: LibreLinkUpClient? = null
    private var lluClientRegion: LluRegion?   = null

    private fun getLluClient(region: LluRegion): LibreLinkUpClient {
        if (lluClient == null || lluClientRegion != region) {
            lluClient       = LibreLinkUpClient(region)
            lluClientRegion = region
        }
        return lluClient!!
    }

    // ── Glucose ───────────────────────────────────────────────────────────────
    val allReadings: Flow<List<GlucoseReading>> = db.glucoseDao().observeAll()
    val latestReading: Flow<GlucoseReading?>    = db.glucoseDao().observeLatest()

    suspend fun addGlucoseReading(
        valueMgDl:   Float,
        trend:       GlucoseTrend = GlucoseTrend.STABLE,
        source:      DataSource   = DataSource.MANUAL,
        timestampMs: Long         = System.currentTimeMillis()
    ): GlucoseReading {
        val r  = GlucoseReading(valueMgDl = valueMgDl, trend = trend, source = source, timestampMs = timestampMs)
        val id = db.glucoseDao().insert(r)
        return r.copy(id = id)
    }

    suspend fun updateGlucoseReading(r: GlucoseReading) = db.glucoseDao().update(r)

    suspend fun deleteGlucoseReading(r: GlucoseReading) = db.glucoseDao().delete(r)

    suspend fun todayReadings(): List<GlucoseReading> {
        return db.glucoseDao().getFrom(todayStartMs())
    }

    fun timeInRange(readings: List<GlucoseReading>, low: Float, high: Float): Float {
        if (readings.isEmpty()) return 0f
        return readings.count { it.valueMgDl in low..high }.toFloat() / readings.size * 100f
    }

    // ── Insulin ───────────────────────────────────────────────────────────────
    val allInsulin: Flow<List<InsulinEntry>> = db.insulinDao().observeAll()

    suspend fun addInsulin(units: Float, type: InsulinType, note: String = "", timestampMs: Long = System.currentTimeMillis()): InsulinEntry {
        val e  = InsulinEntry(units = units, type = type, note = note, timestampMs = timestampMs)
        val id = db.insulinDao().insert(e)
        return e.copy(id = id)
    }

    suspend fun updateInsulin(e: InsulinEntry) = db.insulinDao().update(e)

    suspend fun deleteInsulin(e: InsulinEntry) = db.insulinDao().delete(e)

    suspend fun todayInsulinTotal(): Float {
        return db.insulinDao().getFrom(todayStartMs()).sumOf { it.units.toDouble() }.toFloat()
    }

    // ── Meals ─────────────────────────────────────────────────────────────────
    val allMeals: Flow<List<MealEntry>> = db.mealDao().observeAll()

    suspend fun addMeal(description: String, carbsGrams: Float, suggestedInsulin: Float = 0f, timestampMs: Long = System.currentTimeMillis()): MealEntry {
        val e  = MealEntry(description = description, carbsGrams = carbsGrams,
            suggestedInsulinUnits = suggestedInsulin, timestampMs = timestampMs)
        val id = db.mealDao().insert(e)
        return e.copy(id = id)
    }

    suspend fun updateMeal(e: MealEntry) = db.mealDao().update(e)

    suspend fun deleteMeal(e: MealEntry) = db.mealDao().delete(e)

    // ── CGM Sync ──────────────────────────────────────────────────────────────
    suspend fun syncCgm(): CgmSyncResult {
        return when (prefs.cgmSource.first()) {
            "LLU"        -> syncFromLlu()
            "NIGHTSCOUT" -> syncFromNightscout()
            "DEXCOM"     -> syncFromDexcom()
            else         -> CgmSyncResult.NoSourceConfigured
        }
    }

    private suspend fun syncFromLlu(): CgmSyncResult {
        val email    = prefs.lluEmail.first()
        val password = prefs.lluPassword.first()
        val region   = LluRegion.fromCode(prefs.lluRegion.first())

        if (email.isBlank() || password.isBlank())
            return CgmSyncResult.Error("Δεν έχεις ορίσει στοιχεία LibreLinkUp")

        val client = getLluClient(region)
        return when (val r = client.getReadings(email, password)) {
            is LluResult.Success -> {
                val (readings, patientId) = r.data
                if (patientId.isNotBlank()) {
                    prefs.saveLluSession(
                        prefs.lluToken.first(),
                        prefs.lluTokenExpiry.first(),
                        patientId
                    )
                }
                // Save all readings newer than the last stored one
                val latestStored = db.glucoseDao().observeLatest().first()
                val sinceMs      = latestStored?.timestampMs ?: 0L
                val apiLatest    = readings.maxByOrNull { it.timestampMs }!!

                // If the DB's latest timestamp is more than 10 minutes ahead of the
                // API's latest, it was created by the System.currentTimeMillis() fallback
                // (old bug). Delete those bogus future entries, then re-read the DB so
                // the filter below uses the real last-stored timestamp.
                var effectiveSinceMs = sinceMs
                if (latestStored != null &&
                    latestStored.timestampMs > apiLatest.timestampMs + 10 * 60_000L) {
                    Log.w(TAG, "Bogus future timestamp in DB (${latestStored.timestampMs} vs ${apiLatest.timestampMs}) — purging")
                    db.glucoseDao().deleteNewerThan(apiLatest.timestampMs + 60_000L)
                    effectiveSinceMs = db.glucoseDao().observeLatest().first()?.timestampMs ?: 0L
                }

                val newReadings = readings
                    .filter { it.timestampMs > effectiveSinceMs + 60_000L }
                    .sortedBy { it.timestampMs }
                if (newReadings.isNotEmpty()) {
                    db.glucoseDao().insertAll(newReadings)
                    Log.d(TAG, "Saved ${newReadings.size} new LLU readings")
                }
                CgmSyncResult.Success(apiLatest)
            }
            is LluResult.Error -> CgmSyncResult.Error(r.msg)
        }
    }

    private suspend fun syncFromNightscout(): CgmSyncResult {
        val url    = prefs.nsUrl.first()
        val secret = prefs.nsSecret.first()
        val token  = prefs.nsToken.first()

        if (url.isBlank())
            return CgmSyncResult.Error("Δεν έχεις ορίσει Nightscout URL")

        val client = NightscoutClient(url, secret, token)
        return when (val r = client.getLatestReading()) {
            is NsResult.Success -> {
                saveIfNewer(r.data)
                CgmSyncResult.Success(r.data)
            }
            is NsResult.Error -> CgmSyncResult.Error(r.msg)
        }
    }

    // ── Dexcom Share sync ─────────────────────────────────────────────────────
    private var dexcomClient: DexcomShareClient? = null

    private suspend fun syncFromDexcom(): CgmSyncResult {
        val username = prefs.dexcomUsername.first()
        val password = prefs.getDexcomPassword()
        val region   = DexcomRegion.fromCode(prefs.dexcomRegion.first())

        if (username.isBlank() || password.isBlank())
            return CgmSyncResult.Error("Δεν έχεις ορίσει στοιχεία Dexcom")

        if (dexcomClient == null) dexcomClient = DexcomShareClient(region)

        return when (val r = dexcomClient!!.getLatestReading(username, password)) {
            is DexcomResult.Success -> {
                saveIfNewer(r.data)
                CgmSyncResult.Success(r.data)
            }
            is DexcomResult.Error -> CgmSyncResult.Error(r.msg)
        }
    }

    // ── Dexcom login (called from settings) ───────────────────────────────────
    suspend fun dexcomLogin(
        username: String,
        password: String,
        region:   DexcomRegion
    ): DexcomResult<String> {
        val client = DexcomShareClient(region)
        return when (val r = client.login(username, password)) {
            is DexcomResult.Success -> {
                prefs.saveDexcomCredentials(username, password, region.name)
                dexcomClient = client  // reuse session
                // Immediately fetch first reading
                when (val reading = client.getLatestReading(username, password)) {
                    is DexcomResult.Success -> db.glucoseDao().insert(reading.data)
                    else -> { /* not critical */ }
                }
                DexcomResult.Success("OK")
            }
            is DexcomResult.Error -> r
        }
    }

    private suspend fun saveIfNewer(reading: GlucoseReading) {
        val latest = db.glucoseDao().observeLatest().first()
        // Save only if it's newer by at least 60 seconds (avoid duplicates from frequent polling)
        if (latest == null || reading.timestampMs > latest.timestampMs + 60_000L) {
            db.glucoseDao().insert(reading)
            Log.d(TAG, "Saved reading: ${reading.valueMgDl} mg/dL")
        } else {
            Log.d(TAG, "Skipped duplicate reading")
        }
    }

    // ── LLU Login (called from onboarding/settings) ───────────────────────────
    suspend fun lluLogin(email: String, password: String, region: LluRegion): LluResult<String> {
        val client = getLluClient(region)
        return when (val r = client.login(email, password)) {
            is LluResult.Success -> {
                val session = r.data
                prefs.saveLluCredentials(email, password, region.name)
                prefs.saveLluSession(session.token, session.expiresMs, session.patientId)
                // Immediately fetch first reading
                when (val readResult = client.getLatestReading(email, password)) {
                    is LluResult.Success -> {
                        val (reading, patientId) = readResult.data
                        prefs.saveLluSession(session.token, session.expiresMs, patientId)
                        db.glucoseDao().insert(reading)
                    }
                    else -> { /* not critical */ }
                }
                LluResult.Success(session.token)
            }
            is LluResult.Error -> r
        }
    }

    // ── Nightscout test + save ────────────────────────────────────────────────
    suspend fun nightscoutTest(url: String, secret: String, token: String): NsResult<String> {
        val client = NightscoutClient(url, secret, token)
        return when (val r = client.testConnection()) {
            is NsResult.Success -> {
                prefs.saveNightscoutConfig(url, secret, token)
                NsResult.Success("${r.data.name} v${r.data.version}")
            }
            is NsResult.Error -> r
        }
    }

    // ── Mood ──────────────────────────────────────────────────────────────────
    val allMoods: Flow<List<MoodEntry>> = db.moodDao().observeAll()

    suspend fun addMoodEntry(
        mood:    MoodLevel,
        energy:  EnergyLevel,
        notes:   String = "",
        glucoseAtTime: Float? = null
    ): MoodEntry {
        val e  = MoodEntry(mood = mood, energy = energy, notes = notes,
            glucoseAtTime = glucoseAtTime)
        val id = db.moodDao().insert(e)
        return e.copy(id = id)
    }

    suspend fun updateMoodEntry(e: MoodEntry) = db.moodDao().update(e)

    suspend fun deleteMoodEntry(e: MoodEntry) = db.moodDao().delete(e)

    suspend fun moodGlucoseCorrelation(days: Int = 30): List<MoodGlucosePoint> {
        val fromMs = System.currentTimeMillis() - days * 86_400_000L
        val moods  = db.moodDao().getFrom(fromMs)
        return moods.map { m ->
            MoodGlucosePoint(
                timestampMs   = m.timestampMs,
                moodScore     = m.mood.score.toFloat(),
                glucoseAtTime = m.glucoseAtTime,
                moodLabel     = m.mood.label,
                moodEmoji     = m.mood.emoji
            )
        }
    }
    fun calculateBolus(carbsGrams: Float): Float {
        // Called from ViewModel — profile access is there
        return 0f   // placeholder; real calc is in ViewModel
    }

    private fun todayStartMs() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
