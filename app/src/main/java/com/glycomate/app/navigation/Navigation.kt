package com.glycomate.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.glycomate.app.R

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

data class NavItem(val screen: Screen, val labelRes: Int, val icon: ImageVector)

// 5 items in bottom nav — the rest accessible from Settings / Dashboard
val bottomNavItems = listOf(
    NavItem(Screen.Dashboard,  R.string.nav_home,  Icons.Filled.Home),
    NavItem(Screen.Statistics, R.string.nav_stats, Icons.Filled.BarChart),
    NavItem(Screen.Buddy,      R.string.nav_buddy, Icons.Filled.EmojiEmotions),
    NavItem(Screen.Sos,        R.string.nav_sos,   Icons.Filled.Warning),
    NavItem(Screen.Settings,   R.string.nav_menu,  Icons.Filled.Menu),
)
