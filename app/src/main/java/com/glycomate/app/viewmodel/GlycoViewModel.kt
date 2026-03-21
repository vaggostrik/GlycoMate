package com.glycomate.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glycomate.app.data.model.*
import com.glycomate.app.data.repository.GlycoRepository
import com.glycomate.app.gamification.*
import com.glycomate.app.worker.CgmSyncWorker
import java.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val latestReading:     GlucoseReading? = null,
    val todayReadings:     List<GlucoseReading> = emptyList(),
    val todayTir:          Float = 0f,
    val todayInsulinTotal: Float = 0f,
    val iob:               Float = 0f,
    val basalToday:        Float = 0f,
    val profile:           UserProfile = UserProfile(),
    val isSyncing:         Boolean = false,
    val lastSyncError:     String? = null
)

sealed class AppEvent {
    data class XpGained(val amount: Int, val reason: String)  : AppEvent()
    data class BadgeUnlocked(val badge: Badge)                : AppEvent()
    data class LevelUp(val newLevel: Int, val titleRes: Int)  : AppEvent()
    data class StreakUpdated(val days: Int)                    : AppEvent()
    data class ShowSnackbar(val message: String)              : AppEvent()
}

class GlycoViewModel(app: Application) : AndroidViewModel(app) {

    val repo  = GlycoRepository(app)
    val gamif = GamificationManager(app)

    val allReadings = repo.allReadings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allInsulin  = repo.allInsulin.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allMeals    = repo.allMeals.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val userProfile = repo.prefs.userProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())
    val onboardingDone = repo.prefs.onboardingDone.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cgmSource = repo.prefs.cgmSource.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "NONE")
    val gamificationState = gamif.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), GamificationState())

    private val _dashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>()
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(repo.allReadings, repo.prefs.userProfile, repo.allInsulin) { readings, profile, insulinList ->
                val today = repo.todayReadings()
                val nowMs = System.currentTimeMillis()
                val diaMs = (profile.dia * 3_600_000L).toLong()
                val iob   = insulinList
                    .filter { it.type == InsulinType.RAPID && it.timestampMs >= nowMs - diaMs }
                    .sumOf { entry ->
                        val hoursAgo  = (nowMs - entry.timestampMs) / 3_600_000f
                        val remaining = (1f - hoursAgo / profile.dia).coerceAtLeast(0f)
                        (entry.units * remaining).toDouble()
                    }.toFloat()
                val todayStartMs = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val basalToday = insulinList
                    .filter { it.type == InsulinType.LONG && it.timestampMs >= todayStartMs }
                    .sumOf { it.units.toDouble() }.toFloat()
                DashboardState(
                    latestReading     = readings.firstOrNull(),
                    todayReadings     = today,
                    todayTir          = repo.timeInRange(today, profile.targetLow, profile.targetHigh),
                    todayInsulinTotal = repo.todayInsulinTotal(),
                    iob               = iob,
                    basalToday        = basalToday,
                    profile           = profile,
                    isSyncing         = _dashboard.value.isSyncing,
                    lastSyncError     = _dashboard.value.lastSyncError
                )
            }.collect { _dashboard.value = it }
        }
    }

    /**
     * Estimates trend from rate-of-change vs the previous reading.
     * Only used for manual entries — CGM readings already carry a trend.
     * Thresholds in mg/dL/min (standard Dexcom/Libre convention):
     *   > +3 ↑↑ | +2..3 ↑ | +1..2 ↗ | ±1 → | -1..-2 ↘ | -2..-3 ↓ | < -3 ↓↓
     */
    private fun inferTrend(newValue: Float, prev: GlucoseReading?): GlucoseTrend {
        if (prev == null) return GlucoseTrend.STABLE
        val timeDiffMin = (System.currentTimeMillis() - prev.timestampMs) / 60_000f
        if (timeDiffMin < 1f || timeDiffMin > 30f) return GlucoseTrend.STABLE
        val roc = (newValue - prev.valueMgDl) / timeDiffMin
        return when {
            roc >  3f -> GlucoseTrend.RISING_FAST
            roc >  2f -> GlucoseTrend.RISING
            roc >  1f -> GlucoseTrend.RISING_SLOW
            roc > -1f -> GlucoseTrend.STABLE
            roc > -2f -> GlucoseTrend.FALLING_SLOW
            roc > -3f -> GlucoseTrend.FALLING
            else      -> GlucoseTrend.FALLING_FAST
        }
    }

    fun logGlucose(valueMgDl: Float, timestampMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                val trend   = if (System.currentTimeMillis() - timestampMs < 5 * 60_000L)
                    inferTrend(valueMgDl, allReadings.value.firstOrNull())
                else GlucoseTrend.STABLE
                val reading   = repo.addGlucoseReading(valueMgDl, trend, timestampMs = timestampMs)
                val profile   = userProfile.value
                val today     = repo.todayReadings()
                val gamEvents = gamif.onGlucoseLogged(reading, today,
                    profile.targetLow, profile.targetHigh)
                emitGamEvents(gamEvents)
            } catch (e: Exception) {
                _events.emit(AppEvent.ShowSnackbar("Σφάλμα: ${e.localizedMessage}"))
            }
        }
    }

    fun deleteGlucose(r: GlucoseReading) =
        viewModelScope.launch { repo.deleteGlucoseReading(r) }

    val allMoods = repo.allMoods.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logMood(mood: MoodLevel, energy: EnergyLevel, notes: String = "") {
        viewModelScope.launch {
            try {
                val currentGlucose = dashboard.value.latestReading?.valueMgDl
                repo.addMoodEntry(mood, energy, notes, currentGlucose)
                _events.emit(AppEvent.ShowSnackbar(
                    "Mood καταγράφηκε ${mood.emoji}  +${XpAward.GLUCOSE_LOG / 2} XP"))
            } catch (e: Exception) {
                _events.emit(AppEvent.ShowSnackbar("Σφάλμα: ${e.localizedMessage}"))
            }
        }
    }

    fun deleteMood(e: MoodEntry) = viewModelScope.launch { repo.deleteMoodEntry(e) }

    suspend fun getMoodCorrelation(days: Int = 30) = repo.moodGlucoseCorrelation(days)

    fun logInsulin(units: Float, type: InsulinType, note: String = "", timestampMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                repo.addInsulin(units, type, note, timestampMs)
                val total     = allInsulin.value.size + 1
                val gamEvents = gamif.onInsulinLogged(total)
                emitGamEvents(gamEvents)
            } catch (e: Exception) {
                _events.emit(AppEvent.ShowSnackbar("Σφάλμα: ${e.localizedMessage}"))
            }
        }
    }

    fun deleteInsulin(e: InsulinEntry) =
        viewModelScope.launch { repo.deleteInsulin(e) }

    fun logMeal(description: String, carbsGrams: Float, timestampMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                val profile          = userProfile.value
                val currentGlucose   = dashboard.value.latestReading?.valueMgDl ?: profile.targetHigh
                val suggestedInsulin = profile.calculateBolus(carbsGrams, currentGlucose)
                repo.addMeal(description, carbsGrams, suggestedInsulin, timestampMs)
                val total     = allMeals.value.size + 1
                val gamEvents = gamif.onMealLogged(total)
                emitGamEvents(gamEvents)
            } catch (e: Exception) {
                _events.emit(AppEvent.ShowSnackbar("Σφάλμα: ${e.localizedMessage}"))
            }
        }
    }

    fun deleteMeal(e: MealEntry) =
        viewModelScope.launch { repo.deleteMeal(e) }

    fun syncNow() {
        _dashboard.update { it.copy(isSyncing = true, lastSyncError = null) }
        CgmSyncWorker.runNow(getApplication())
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _dashboard.update { it.copy(isSyncing = false) }
        }
    }

    fun saveProfile(profile: UserProfile) =
        viewModelScope.launch { repo.prefs.saveUserProfile(profile) }

    fun completeOnboarding() = viewModelScope.launch {
        repo.prefs.setOnboardingDone()
        // Always schedule polling — it will stop itself if source is NONE
        CgmSyncWorker.schedule(getApplication())
        if (cgmSource.value != "NONE") {
            val gamEvents = gamif.onCgmConnected()
            emitGamEvents(gamEvents)
        }
    }

    fun calculateBolus(carbsGrams: Float): Float {
        val profile        = userProfile.value
        val currentGlucose = dashboard.value.latestReading?.valueMgDl ?: profile.targetHigh
        return profile.calculateBolus(carbsGrams, currentGlucose)
    }

    private suspend fun emitGamEvents(events: List<GamificationEvent>) {
        events.forEach { e ->
            when (e) {
                is GamificationEvent.XpGained     -> _events.emit(AppEvent.XpGained(e.amount, e.reason))
                is GamificationEvent.BadgeUnlocked -> _events.emit(AppEvent.BadgeUnlocked(e.badge))
                is GamificationEvent.LevelUp       -> _events.emit(AppEvent.LevelUp(e.newLevel, e.titleRes))
                is GamificationEvent.StreakUpdated -> _events.emit(AppEvent.StreakUpdated(e.days))
            }
        }
    }
}
