package com.glycomate.app.gamification

import com.glycomate.app.R

// ── Badge definitions ─────────────────────────────────────────────────────────

enum class BadgeId {
    FIRST_LOG,
    WEEK_STREAK,
    TWO_WEEK_STREAK,
    MONTH_STREAK,
    PERFECT_DAY,
    PERFECT_WEEK,
    FIRST_MEAL_LOG,
    MEAL_MASTER,
    INSULIN_PRO,
    NIGHT_OWL,
    EARLY_BIRD,
    CGM_CONNECTED,
    STABLE_GLUCOSE,
}

data class Badge(
    val id:          BadgeId,
    val emoji:       String,
    val nameRes:     Int,
    val descRes:     Int,
    val xpReward:    Int,
    val earnedAtMs:  Long? = null
) {
    val isEarned: Boolean get() = earnedAtMs != null
}

val ALL_BADGES = listOf(
    Badge(BadgeId.FIRST_LOG,        "🚀", R.string.badge_1_name,  R.string.badge_1_desc,  50),
    Badge(BadgeId.WEEK_STREAK,      "⚡", R.string.badge_2_name,  R.string.badge_2_desc,  100),
    Badge(BadgeId.TWO_WEEK_STREAK,  "🛡️", R.string.badge_3_name,  R.string.badge_3_desc,  200),
    Badge(BadgeId.MONTH_STREAK,     "👑", R.string.badge_4_name,  R.string.badge_4_desc,  500),
    Badge(BadgeId.PERFECT_DAY,      "🏆", R.string.badge_5_name,  R.string.badge_5_desc,  150),
    Badge(BadgeId.PERFECT_WEEK,     "🌟", R.string.badge_6_name,  R.string.badge_6_desc,  300),
    Badge(BadgeId.FIRST_MEAL_LOG,   "🍽️", R.string.badge_7_name,  R.string.badge_7_desc,  30),
    Badge(BadgeId.MEAL_MASTER,      "👨‍🍳", R.string.badge_8_name,  R.string.badge_8_desc,  200),
    Badge(BadgeId.INSULIN_PRO,      "💉", R.string.badge_9_name,  R.string.badge_9_desc,  200),
    Badge(BadgeId.NIGHT_OWL,        "🌙", R.string.badge_10_name, R.string.badge_10_desc, 50),
    Badge(BadgeId.EARLY_BIRD,       "🌅", R.string.badge_11_name, R.string.badge_11_desc, 50),
    Badge(BadgeId.CGM_CONNECTED,    "📡", R.string.badge_12_name, R.string.badge_12_desc, 100),
    Badge(BadgeId.STABLE_GLUCOSE,   "📊", R.string.badge_13_name, R.string.badge_13_desc, 150),
)

// ── XP & Level ────────────────────────────────────────────────────────────────

data class GamificationState(
    val xp:             Int               = 0,
    val streakDays:     Int               = 0,
    val lastLogDateMs:  Long              = 0L,
    val earnedBadgeIds: Set<BadgeId>      = emptySet(),
    val buddyMood:      BuddyMood         = BuddyMood.HAPPY,
    val buddyAccessories: Set<String>     = emptySet()
) {
    val level: Int get() = xpToLevel(xp)
    val xpForCurrentLevel: Int get() = levelThreshold(level)
    val xpForNextLevel: Int get() = levelThreshold(level + 1)
    val xpProgressInLevel: Int get() = xp - xpForCurrentLevel
    val xpNeededForNextLevel: Int get() = xpForNextLevel - xpForCurrentLevel
    val progressFraction: Float
        get() = if (xpNeededForNextLevel == 0) 1f
        else xpProgressInLevel.toFloat() / xpNeededForNextLevel.toFloat()

    val earnedBadges: List<Badge>
        get() = ALL_BADGES.filter { it.id in earnedBadgeIds }
            .map { b -> b.copy(earnedAtMs = System.currentTimeMillis()) }

    val unearnedBadges: List<Badge>
        get() = ALL_BADGES.filter { it.id !in earnedBadgeIds }

    val levelTitleRes: Int get() = when (level) {
        1    -> R.string.level_1
        2    -> R.string.level_2
        3    -> R.string.level_3
        4    -> R.string.level_4
        5    -> R.string.level_5
        6    -> R.string.level_6
        7    -> R.string.level_7
        8    -> R.string.level_8
        9    -> R.string.level_9
        else -> R.string.level_10
    }
}

private fun levelThreshold(level: Int): Int = when (level) {
    0    -> 0
    1    -> 0
    2    -> 200
    3    -> 500
    4    -> 900
    5    -> 1400
    6    -> 2000
    7    -> 2700
    8    -> 3600
    9    -> 4700
    else -> 6000
}

private fun xpToLevel(xp: Int): Int {
    var lvl = 1
    while (xp >= levelThreshold(lvl + 1)) lvl++
    return lvl.coerceAtMost(10)
}

// ── Buddy ─────────────────────────────────────────────────────────────────────

enum class BuddyMood {
    VERY_HAPPY,
    HAPPY,
    NEUTRAL,
    SAD,
    WORRIED
}

data class BuddyMessage(
    val resId: Int,
    val args:  List<Any> = emptyList(),
    val mood:  BuddyMood
)

fun getBuddyMessage(mood: BuddyMood, streakDays: Int, hoursWithoutLog: Int): BuddyMessage {
    return when (mood) {
        BuddyMood.VERY_HAPPY -> when {
            streakDays >= 30 -> BuddyMessage(R.string.msg_perfect_30, mood = mood)
            streakDays >= 14 -> BuddyMessage(R.string.msg_perfect_14, mood = mood)
            streakDays >= 7  -> BuddyMessage(R.string.msg_perfect_7, mood = mood)
            else             -> BuddyMessage(R.string.msg_perfect, mood = mood)
        }
        BuddyMood.HAPPY -> when {
            streakDays > 0 -> BuddyMessage(R.string.msg_happy_streak, listOf(streakDays), mood)
            else           -> BuddyMessage(R.string.msg_happy, mood = mood)
        }
        BuddyMood.NEUTRAL -> BuddyMessage(R.string.msg_neutral, mood = mood)
        BuddyMood.SAD -> when {
            hoursWithoutLog >= 8 -> BuddyMessage(R.string.msg_sad_8, listOf(hoursWithoutLog), mood)
            hoursWithoutLog >= 4 -> BuddyMessage(R.string.msg_sad_4, mood = mood)
            else                 -> BuddyMessage(R.string.msg_sad, mood = mood)
        }
        BuddyMood.WORRIED -> BuddyMessage(R.string.msg_worried, mood = mood)
    }
}

object XpAward {
    const val GLUCOSE_LOG    = 10
    const val INSULIN_LOG    = 5
    const val MEAL_LOG       = 15
    const val PERFECT_DAY    = 50
    const val IN_RANGE_DAY   = 20
    const val STREAK_BONUS   = 5
    const val CGM_SYNC       = 2
}
