package com.glycomate.app.gamification

// ── Badge definitions ─────────────────────────────────────────────────────────

enum class BadgeId {
    FIRST_LOG,          // Πρώτη καταγραφή
    WEEK_STREAK,        // 7 συνεχόμενες μέρες
    TWO_WEEK_STREAK,    // 14 μέρες
    MONTH_STREAK,       // 30 μέρες
    PERFECT_DAY,        // 100% TIR μια μέρα
    PERFECT_WEEK,       // TIR > 70% για 7 μέρες
    FIRST_MEAL_LOG,     // Πρώτο γεύμα
    MEAL_MASTER,        // 50 γεύματα
    INSULIN_PRO,        // 100 καταγραφές ινσουλίνης
    NIGHT_OWL,          // Μέτρηση μετά τα μεσάνυχτα
    EARLY_BIRD,         // Μέτρηση πριν τις 7 πμ
    CGM_CONNECTED,      // Σύνδεση CGM
    STABLE_GLUCOSE,     // 6 ώρες εντός στόχου
}

data class Badge(
    val id:          BadgeId,
    val emoji:       String,
    val name:        String,
    val description: String,
    val xpReward:    Int,
    val earnedAtMs:  Long? = null   // null = δεν έχει κερδηθεί
) {
    val isEarned: Boolean get() = earnedAtMs != null
}

val ALL_BADGES = listOf(
    Badge(BadgeId.FIRST_LOG,        "🚀", "Πρώτο βήμα",      "Πρώτη καταγραφή γλυκόζης",          50),
    Badge(BadgeId.WEEK_STREAK,      "⚡", "7 Ημέρες",         "7 συνεχόμενες μέρες καταγραφής",    100),
    Badge(BadgeId.TWO_WEEK_STREAK,  "🛡️", "Iron Will",        "14 συνεχόμενες μέρες",              200),
    Badge(BadgeId.MONTH_STREAK,     "👑", "Champion",         "30 συνεχόμενες μέρες",              500),
    Badge(BadgeId.PERFECT_DAY,      "🏆", "Τέλεια Μέρα",      "100% Time in Range",                150),
    Badge(BadgeId.PERFECT_WEEK,     "🌟", "Star Week",        "TIR > 70% για 7 μέρες",            300),
    Badge(BadgeId.FIRST_MEAL_LOG,   "🍽️", "Foodie",           "Πρώτη καταγραφή γεύματος",          30),
    Badge(BadgeId.MEAL_MASTER,      "👨‍🍳", "Meal Master",      "50 καταγραφές γευμάτων",           200),
    Badge(BadgeId.INSULIN_PRO,      "💉", "Insulin Pro",      "100 καταγραφές ινσουλίνης",         200),
    Badge(BadgeId.NIGHT_OWL,        "🌙", "Night Watch",      "Μέτρηση μετά τα μεσάνυχτα",         50),
    Badge(BadgeId.EARLY_BIRD,       "🌅", "Early Bird",       "Μέτρηση πριν τις 7:00 πμ",          50),
    Badge(BadgeId.CGM_CONNECTED,    "📡", "Connected",        "Σύνδεση CGM αισθητήρα",             100),
    Badge(BadgeId.STABLE_GLUCOSE,   "📊", "Steady as a Rock", "6 ώρες εντός στόχου",              150),
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

    val levelTitle: String get() = when (level) {
        1    -> "Αρχάριος"
        2    -> "Παρατηρητής"
        3    -> "Σταθερός"
        4    -> "Αξιόπιστος"
        5    -> "Προχωρημένος"
        6    -> "Ειδικός"
        7    -> "Warrior"
        8    -> "Champion"
        9    -> "Legend"
        else -> "Master"
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
    VERY_HAPPY,  // Perfect day / new badge
    HAPPY,       // Εντός στόχου
    NEUTRAL,     // Ούτε καλό ούτε κακό
    SAD,         // Ξέχασε να καταγράψει
    WORRIED      // Hypo ή hyper
}

data class BuddyMessage(
    val text:  String,
    val mood:  BuddyMood
)

fun getBuddyMessage(mood: BuddyMood, streakDays: Int, hoursWithoutLog: Int): BuddyMessage {
    val text = when (mood) {
        BuddyMood.VERY_HAPPY -> when {
            streakDays >= 30 -> "Απίστευτο! 30 μέρες! Είσαι ο απόλυτος Champion! 👑"
            streakDays >= 14 -> "Wow! 2 εβδομάδες σερί! Κερδίζεις το badge Iron Will! 🛡️"
            streakDays >= 7  -> "Εξαιρετικό! 7 μέρες συνεχόμενα! Συνέχισε έτσι! ⚡"
            else             -> "Μπράβο! Τέλεια μέρα! Συνέχισε έτσι! 🏆"
        }
        BuddyMood.HAPPY -> when {
            streakDays > 0 -> "Πάμε καλά! $streakDays μέρες σερί — μην το σπάσεις! 🔥"
            else           -> "Καλά πάμε! Κράτα το streak σου! 💪"
        }
        BuddyMood.NEUTRAL -> "Εντάξει σήμερα… Αύριο θα είναι καλύτερα! 🤔"
        BuddyMood.SAD -> when {
            hoursWithoutLog >= 8 -> "Πεινάω πολύ… ${hoursWithoutLog} ώρες χωρίς καταγραφή! Τι τρως; 🍽️"
            hoursWithoutLog >= 4 -> "Γεια σου! Πότε θα με θυμηθείς; Πάμε μέτρηση! 📊"
            else                 -> "Μην ξεχνάς να καταγράφεις! Είμαι εδώ 😊"
        }
        BuddyMood.WORRIED -> "Πρόσεξε! Η γλυκόζη σου χρειάζεται προσοχή τώρα! ⚠️"
    }
    return BuddyMessage(text, mood)
}

// ── XP award amounts ──────────────────────────────────────────────────────────
object XpAward {
    const val GLUCOSE_LOG    = 10
    const val INSULIN_LOG    = 5
    const val MEAL_LOG       = 15
    const val PERFECT_DAY    = 50   // TIR 100%
    const val IN_RANGE_DAY   = 20   // TIR >= 70%
    const val STREAK_BONUS   = 5    // per day of streak
    const val CGM_SYNC       = 2
}
