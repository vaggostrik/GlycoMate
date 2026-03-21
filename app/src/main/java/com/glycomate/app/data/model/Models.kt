package com.glycomate.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Glucose Reading ──────────────────────────────────────────────────────────
@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val valueMgDl: Float,
    val timestampMs: Long = System.currentTimeMillis(),
    val trend: GlucoseTrend = GlucoseTrend.STABLE,
    val source: DataSource = DataSource.MANUAL
)

enum class GlucoseTrend(val arrow: String, val label: String) {
    RISING_FAST ("↑↑", "Ανεβαίνει πολύ γρήγορα"),
    RISING      ("↑",  "Ανεβαίνει γρήγορα"),
    RISING_SLOW ("↗",  "Ανεβαίνει αργά"),
    STABLE      ("→",  "Σταθερή"),
    FALLING_SLOW("↘",  "Κατεβαίνει αργά"),
    FALLING     ("↓",  "Κατεβαίνει γρήγορα"),
    FALLING_FAST("↓↓", "Κατεβαίνει πολύ γρήγορα")
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

enum class InsulinType(val label: String) {
    RAPID("Ταχείας"),
    LONG ("Βραδείας"),
    MIXED("Μικτή")
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

enum class MoodLevel(val emoji: String, val label: String, val score: Int) {
    VERY_BAD  ("😞", "Πολύ κακή",  1),
    BAD       ("😔", "Κακή",       2),
    NEUTRAL   ("😐", "Ουδέτερη",   3),
    GOOD      ("😊", "Καλή",       4),
    VERY_GOOD ("😄", "Πολύ καλή",  5)
}

enum class EnergyLevel(val emoji: String, val label: String) {
    EXHAUSTED ("🪫", "Εξάντληση"),
    TIRED     ("😴", "Κούραση"),
    NORMAL    ("⚡", "Κανονικό"),
    ENERGIZED ("🔋", "Ενεργητικός"),
    HYPER     ("🚀", "Υπερδραστήριος")
}

data class UserProfile(
    val name: String               = "",
    val diabetesType: String       = "Τύπος 1",
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
