package com.glycomate.app.gamification

import android.content.Context
import androidx.datastore.preferences.core.*
import com.glycomate.app.data.prefs.dataStore
import com.glycomate.app.data.model.GlucoseReading
import kotlinx.coroutines.flow.*
import java.util.Calendar

// ── DataStore keys ────────────────────────────────────────────────────────────
private object GamifKeys {
    val XP              = intPreferencesKey("gam_xp")
    val STREAK          = intPreferencesKey("gam_streak")
    val LAST_LOG_DATE   = longPreferencesKey("gam_last_log_date")
    val EARNED_BADGES   = stringPreferencesKey("gam_badges")   // CSV of BadgeId names
    val BUDDY_MOOD      = stringPreferencesKey("gam_buddy_mood")
    val ACCESSORIES     = stringPreferencesKey("gam_accessories") // CSV
}

// ── Events emitted when something happens ────────────────────────────────────
sealed class GamificationEvent {
    data class XpGained(val amount: Int, val reason: String) : GamificationEvent()
    data class BadgeUnlocked(val badge: Badge)               : GamificationEvent()
    data class LevelUp(val newLevel: Int, val title: String) : GamificationEvent()
    data class StreakUpdated(val days: Int)                   : GamificationEvent()
}

class GamificationManager(private val context: Context) {

    // ── Live state from DataStore ─────────────────────────────────────────────
    val state: Flow<GamificationState> = context.dataStore.data.map { p ->
        GamificationState(
            xp             = p[GamifKeys.XP]           ?: 0,
            streakDays     = p[GamifKeys.STREAK]        ?: 0,
            lastLogDateMs  = p[GamifKeys.LAST_LOG_DATE] ?: 0L,
            earnedBadgeIds = parseBadgeIds(p[GamifKeys.EARNED_BADGES] ?: ""),
            buddyMood      = parseMood(p[GamifKeys.BUDDY_MOOD]),
            buddyAccessories = parseAccessories(p[GamifKeys.ACCESSORIES] ?: "")
        )
    }

    // ── Called when user logs a glucose reading ───────────────────────────────
    suspend fun onGlucoseLogged(
        reading: GlucoseReading,
        todayReadings: List<GlucoseReading>,
        targetLow: Float,
        targetHigh: Float
    ): List<GamificationEvent> {
        val events    = mutableListOf<GamificationEvent>()
        val current   = state.first()
        var xpGain    = 0
        val newBadges = mutableSetOf<BadgeId>()

        // Base XP
        xpGain += XpAward.GLUCOSE_LOG
        events.add(GamificationEvent.XpGained(XpAward.GLUCOSE_LOG, "Καταγραφή γλυκόζης"))

        // First log badge
        if (!current.earnedBadgeIds.contains(BadgeId.FIRST_LOG)) {
            newBadges.add(BadgeId.FIRST_LOG)
        }

        // Night owl (after midnight)
        val hour = Calendar.getInstance().apply { timeInMillis = reading.timestampMs }
            .get(Calendar.HOUR_OF_DAY)
        if (hour == 0 || hour == 1 || hour == 23) {
            if (!current.earnedBadgeIds.contains(BadgeId.NIGHT_OWL))
                newBadges.add(BadgeId.NIGHT_OWL)
        }
        // Early bird (before 7am)
        if (hour < 7) {
            if (!current.earnedBadgeIds.contains(BadgeId.EARLY_BIRD))
                newBadges.add(BadgeId.EARLY_BIRD)
        }

        // Check TIR for today
        if (todayReadings.size >= 4) {
            val tir = todayReadings.count { it.valueMgDl in targetLow..targetHigh }
                .toFloat() / todayReadings.size
            when {
                tir >= 1.0f -> {
                    xpGain += XpAward.PERFECT_DAY
                    events.add(GamificationEvent.XpGained(XpAward.PERFECT_DAY, "Τέλεια μέρα!"))
                    if (!current.earnedBadgeIds.contains(BadgeId.PERFECT_DAY))
                        newBadges.add(BadgeId.PERFECT_DAY)
                }
                tir >= 0.7f -> {
                    xpGain += XpAward.IN_RANGE_DAY
                    events.add(GamificationEvent.XpGained(XpAward.IN_RANGE_DAY, "Εντός στόχου"))
                }
            }
        }

        // Update streak
        val todayStart = todayStartMs()
        val wasLoggedToday = current.lastLogDateMs >= todayStart
        val wasLoggedYesterday = current.lastLogDateMs >= todayStart - 86_400_000L &&
                current.lastLogDateMs < todayStart
        val newStreak = when {
            wasLoggedToday     -> current.streakDays   // already logged today
            wasLoggedYesterday -> current.streakDays + 1
            else               -> 1                    // streak broken, reset
        }
        if (newStreak != current.streakDays) {
            val bonus = newStreak * XpAward.STREAK_BONUS
            xpGain += bonus
            events.add(GamificationEvent.XpGained(bonus, "Streak bonus ($newStreak μέρες)"))
            events.add(GamificationEvent.StreakUpdated(newStreak))
        }

        // Streak badges
        if (newStreak >= 7  && !current.earnedBadgeIds.contains(BadgeId.WEEK_STREAK))
            newBadges.add(BadgeId.WEEK_STREAK)
        if (newStreak >= 14 && !current.earnedBadgeIds.contains(BadgeId.TWO_WEEK_STREAK))
            newBadges.add(BadgeId.TWO_WEEK_STREAK)
        if (newStreak >= 30 && !current.earnedBadgeIds.contains(BadgeId.MONTH_STREAK))
            newBadges.add(BadgeId.MONTH_STREAK)

        // Stable glucose (check last 6h readings all in range)
        if (todayReadings.size >= 6) {
            val last6h = todayReadings.takeLast(6)
            if (last6h.all { it.valueMgDl in targetLow..targetHigh }) {
                if (!current.earnedBadgeIds.contains(BadgeId.STABLE_GLUCOSE))
                    newBadges.add(BadgeId.STABLE_GLUCOSE)
            }
        }

        // Level up check
        val oldLevel = current.level
        val newXp    = current.xp + xpGain
        val newLevel = GamificationState(xp = newXp).level
        if (newLevel > oldLevel) {
            events.add(GamificationEvent.LevelUp(newLevel,
                GamificationState(xp = newXp).levelTitle))
        }

        // Badge events
        newBadges.forEach { badgeId ->
            val badge    = ALL_BADGES.first { it.id == badgeId }
            xpGain      += badge.xpReward
            events.add(GamificationEvent.BadgeUnlocked(badge))
            events.add(GamificationEvent.XpGained(badge.xpReward, "Badge: ${badge.name}"))
        }

        // Buddy mood
        val buddyMood = when {
            reading.valueMgDl < targetLow || reading.valueMgDl > targetHigh -> BuddyMood.WORRIED
            newStreak > 0 && newStreak == current.streakDays + 1 -> BuddyMood.VERY_HAPPY
            reading.valueMgDl in targetLow..targetHigh           -> BuddyMood.HAPPY
            else                                                 -> BuddyMood.NEUTRAL
        }

        // Unlock accessories based on level
        val accessories = current.buddyAccessories.toMutableSet()
        if (newLevel >= 3) accessories.add("hat")
        if (newLevel >= 5) accessories.add("cape")
        if (newLevel >= 8) accessories.add("crown")

        // Persist
        persist(
            xp           = newXp,
            streak       = newStreak,
            lastLogDate  = System.currentTimeMillis(),
            badgeIds     = current.earnedBadgeIds + newBadges,
            mood         = buddyMood,
            accessories  = accessories
        )

        return events
    }

    // ── Called when user logs a meal ──────────────────────────────────────────
    suspend fun onMealLogged(totalMeals: Int): List<GamificationEvent> {
        val events  = mutableListOf<GamificationEvent>()
        val current = state.first()
        var xpGain  = XpAward.MEAL_LOG
        val newBadges = mutableSetOf<BadgeId>()

        events.add(GamificationEvent.XpGained(xpGain, "Καταγραφή γεύματος"))

        if (!current.earnedBadgeIds.contains(BadgeId.FIRST_MEAL_LOG))
            newBadges.add(BadgeId.FIRST_MEAL_LOG)
        if (totalMeals >= 50 && !current.earnedBadgeIds.contains(BadgeId.MEAL_MASTER))
            newBadges.add(BadgeId.MEAL_MASTER)

        newBadges.forEach { badgeId ->
            val badge = ALL_BADGES.first { it.id == badgeId }
            xpGain   += badge.xpReward
            events.add(GamificationEvent.BadgeUnlocked(badge))
        }

        val newXp    = current.xp + xpGain
        val newLevel = GamificationState(xp = newXp).level
        if (newLevel > current.level)
            events.add(GamificationEvent.LevelUp(newLevel,
                GamificationState(xp = newXp).levelTitle))

        persist(xp = newXp, badgeIds = current.earnedBadgeIds + newBadges)
        return events
    }

    // ── Called when user logs insulin ─────────────────────────────────────────
    suspend fun onInsulinLogged(totalInsulinEntries: Int): List<GamificationEvent> {
        val events  = mutableListOf<GamificationEvent>()
        val current = state.first()
        var xpGain  = XpAward.INSULIN_LOG
        val newBadges = mutableSetOf<BadgeId>()

        events.add(GamificationEvent.XpGained(xpGain, "Καταγραφή ινσουλίνης"))

        if (totalInsulinEntries >= 100 && !current.earnedBadgeIds.contains(BadgeId.INSULIN_PRO))
            newBadges.add(BadgeId.INSULIN_PRO)

        newBadges.forEach { badgeId ->
            val badge = ALL_BADGES.first { it.id == badgeId }
            xpGain   += badge.xpReward
            events.add(GamificationEvent.BadgeUnlocked(badge))
        }

        val newXp = current.xp + xpGain
        persist(xp = newXp, badgeIds = current.earnedBadgeIds + newBadges)
        return events
    }

    // ── Called when CGM connects ──────────────────────────────────────────────
    suspend fun onCgmConnected(): List<GamificationEvent> {
        val events  = mutableListOf<GamificationEvent>()
        val current = state.first()
        if (current.earnedBadgeIds.contains(BadgeId.CGM_CONNECTED)) return events

        val badge  = ALL_BADGES.first { it.id == BadgeId.CGM_CONNECTED }
        val newXp  = current.xp + badge.xpReward
        events.add(GamificationEvent.BadgeUnlocked(badge))
        events.add(GamificationEvent.XpGained(badge.xpReward, "CGM συνδέθηκε"))
        persist(xp = newXp, badgeIds = current.earnedBadgeIds + BadgeId.CGM_CONNECTED)
        return events
    }

    // ── Buddy message based on current state ──────────────────────────────────
    suspend fun getBuddyMessage(): BuddyMessage {
        val current      = state.first()
        val hoursSinceLog = ((System.currentTimeMillis() - current.lastLogDateMs) / 3_600_000L).toInt()
        return getBuddyMessage(current.buddyMood, current.streakDays, hoursSinceLog)
    }

    // ── Persist helper ────────────────────────────────────────────────────────
    private suspend fun persist(
        xp:          Int?           = null,
        streak:      Int?           = null,
        lastLogDate: Long?          = null,
        badgeIds:    Set<BadgeId>?  = null,
        mood:        BuddyMood?     = null,
        accessories: Set<String>?   = null
    ) {
        context.dataStore.edit { p ->
            xp?.let          { p[GamifKeys.XP]            = it }
            streak?.let      { p[GamifKeys.STREAK]         = it }
            lastLogDate?.let { p[GamifKeys.LAST_LOG_DATE]  = it }
            badgeIds?.let    { p[GamifKeys.EARNED_BADGES]  = it.joinToString(",") { b -> b.name } }
            mood?.let        { p[GamifKeys.BUDDY_MOOD]     = it.name }
            accessories?.let { p[GamifKeys.ACCESSORIES]    = it.joinToString(",") }
        }
    }

    private fun parseBadgeIds(csv: String): Set<BadgeId> =
        csv.split(",").filter { it.isNotBlank() }
            .mapNotNull { runCatching { BadgeId.valueOf(it) }.getOrNull() }.toSet()

    private fun parseMood(s: String?): BuddyMood =
        runCatching { BuddyMood.valueOf(s ?: "") }.getOrDefault(BuddyMood.NEUTRAL)

    private fun parseAccessories(csv: String): Set<String> =
        csv.split(",").filter { it.isNotBlank() }.toSet()

    private fun todayStartMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
