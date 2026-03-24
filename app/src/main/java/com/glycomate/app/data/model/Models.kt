package com.glycomate.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.glycomate.app.R

// ── Glucose Reading ──────────────────────────────────────────────────────────
@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val valueMgDl: Float,
    val timestampMs: Long = System.currentTimeMillis(),
    val trend: GlucoseTrend = GlucoseTrend.STABLE,
    val source: DataSource = DataSource.MANUAL
)

enum class GlucoseTrend(val arrow: String, val labelRes: Int) {
    RISING_FAST ("↑↑", R.string.trend_rising_fast),
    RISING      ("↑",  R.string.trend_rising),
    RISING_SLOW ("↗",  R.string.trend_rising_slow),
    STABLE      ("→",  R.string.trend_stable),
    FALLING_SLOW("↘",  R.string.trend_falling_slow),
    FALLING     ("↓",  R.string.trend_falling),
    FALLING_FAST("↓↓", R.string.trend_falling_fast)
}

enum class DataSource { MANUAL, LIBRE_LINK_UP, NIGHTSCOUT, DEXCOM }

fun GlucoseReading.glucoseStatus(low: Float = 70f, high: Float = 180f) = when {
    valueMgDl < low  -> GlucoseStatus.LOW
    valueMgDl > high -> GlucoseStatus.HIGH
    else             -> GlucoseStatus.IN_RANGE
}

enum class GlucoseStatus { LOW, IN_RANGE, HIGH }

// ── Insulin Entry ────────────────────────────────────────────────────────────
@Entity(tableName = "insulin_entries")
data class InsulinEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val units: Float,
    val type: InsulinType,
    val timestampMs: Long = System.currentTimeMillis(),
    val note: String = "",
    val brand: String = ""
)

enum class InsulinType(val labelRes: Int) {
    RAPID(R.string.insulin_rapid),
    LONG (R.string.insulin_long),
    MIXED(R.string.insulin_mixed)
}

// ── Meal Entry ───────────────────────────────────────────────────────────────
@Entity(tableName = "meal_entries")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val carbsGrams: Float,
    val timestampMs: Long = System.currentTimeMillis(),
    val suggestedInsulinUnits: Float = 0f
)

// ── Mood Entry ────────────────────────────────────────────────────────────────
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mood:        MoodLevel,
    val energy:      EnergyLevel,
    val notes:       String = "",
    val timestampMs: Long   = System.currentTimeMillis(),
    // Snapshot of glucose at time of mood log (for correlation)
    val glucoseAtTime: Float? = null
)

enum class MoodLevel(val emoji: String, val labelRes: Int, val score: Int) {
    VERY_BAD  ("😞", R.string.mood_very_bad,  1),
    BAD       ("😔", R.string.mood_bad,       2),
    NEUTRAL   ("😐", R.string.mood_neutral_level,   3),
    GOOD      ("😊", R.string.mood_good,      4),
    VERY_GOOD ("😄", R.string.mood_very_good,  5)
}

enum class EnergyLevel(val emoji: String, val labelRes: Int) {
    EXHAUSTED ("🪫", R.string.energy_exhausted),
    TIRED     ("😴", R.string.energy_tired),
    NORMAL    ("⚡", R.string.energy_normal),
    ENERGIZED ("🔋", R.string.energy_energized),
    HYPER     ("🚀", R.string.energy_hyper)
}

data class UserProfile(
    val name: String               = "",
    val diabetesType: String       = "T1D",
    val targetLow: Float           = 70f,
    val targetHigh: Float          = 180f,
    val icr: Float                 = 10f,
    val isf: Float                 = 40f,
    val basalUnits: Float          = 0f,
    val rapidInsulinBrand: String  = "NovoRapid",
    val longInsulinBrand: String   = "Lantus",
    val dia: Float                 = 4f    // Duration of Insulin Action (hours) for IOB calculation
) {
    fun calculateBolus(carbsGrams: Float, currentGlucose: Float): Float {
        val carbDose       = carbsGrams / icr
        val correctionDose = if (currentGlucose > targetHigh) {
            (currentGlucose - targetHigh) / isf
        } else 0f
        return (carbDose + correctionDose).coerceAtLeast(0f)
    }

    companion object {
        val RAPID_BRANDS = listOf("NovoRapid", "Apidra", "Actrapid", "Humulin Regular", "Fiasp")
        val LONG_BRANDS  = listOf("Tresiba", "Toujeo", "Lantus", "Levemir")
    }
}

// ── Mood-Glucose correlation point (for charts) ───────────────────────────────
data class MoodGlucosePoint(
    val timestampMs:   Long,
    val moodScore:     Float,          // 1-5
    val glucoseAtTime: Float?,         // mg/dL at time of log
    val moodLabel:     String,
    val moodEmoji:     String
)
