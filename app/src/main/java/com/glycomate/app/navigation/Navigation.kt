package com.glycomate.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Onboarding    : Screen("onboarding")
    object Dashboard     : Screen("dashboard")
    object History       : Screen("history")
    object Statistics    : Screen("statistics")
    object Buddy         : Screen("buddy")
    object Reminders     : Screen("reminders")
    object Sos           : Screen("sos")
    object AiMealScan    : Screen("ai_meal_scan")
    object BarcodeScanner: Screen("barcode_scanner")
    object MoodTracker   : Screen("mood_tracker")
    object Settings      : Screen("settings")
}

data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

// 5 items in bottom nav — the rest accessible from Settings / Dashboard
val bottomNavItems = listOf(
    NavItem(Screen.Dashboard,  "Home",      Icons.Filled.Home),
    NavItem(Screen.Statistics, "Γραφ/τα",   Icons.Filled.BarChart),
    NavItem(Screen.Buddy,      "Buddy",     Icons.Filled.EmojiEmotions),
    NavItem(Screen.Sos,        "SOS",       Icons.Filled.Warning),
    NavItem(Screen.Settings,   "Μενού",     Icons.Filled.Menu),
)
