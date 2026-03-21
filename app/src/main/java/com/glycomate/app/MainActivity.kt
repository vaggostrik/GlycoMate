package com.glycomate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.glycomate.app.gamification.Badge
import com.glycomate.app.navigation.*
import com.glycomate.app.ui.components.*
import com.glycomate.app.ui.screens.*
import com.glycomate.app.ui.theme.GlycoMateTheme
import com.glycomate.app.viewmodel.AppEvent
import com.glycomate.app.viewmodel.GlycoViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showCustomSplash by remember { mutableStateOf(true) }

            GlycoMateTheme {
                if (showCustomSplash) {
                    SplashScreenContent(onFinished = { showCustomSplash = false })
                } else {
                    GlycoMateRoot()
                }
            }
        }
    }
}

@Composable
fun SplashScreenContent(onFinished: () -> Unit) {
    // Show for 2 seconds
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter      = painterResource(id = R.drawable.splash_logo),
            contentDescription = null,
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop // This ensures it fills the whole screen
        )
    }
}

@Composable
fun GlycoMateRoot() {
    val vm: GlycoViewModel = viewModel()
    val onboardingDone by vm.onboardingDone.collectAsState()

    if (!onboardingDone) {
        OnboardingScreen(viewModel = vm, onDone = {})
    } else {
        MainApp(vm)
    }
}

@Composable
private fun MainApp(vm: GlycoViewModel) {
    val navController   = rememberNavController()
    val currentBack     by navController.currentBackStackEntryAsState()
    val currentRoute    = currentBack?.destination?.route
    val gamState        by vm.gamificationState.collectAsState()

    // Popup states
    var xpToast:      Pair<Int, String>? by remember { mutableStateOf(null) }
    var badgeDialog:  Badge?             by remember { mutableStateOf(null) }
    var levelUpState: Pair<Int, String>? by remember { mutableStateOf(null) }

    // Collect events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is AppEvent.XpGained     -> xpToast = event.amount to event.reason
                is AppEvent.BadgeUnlocked -> badgeDialog = event.badge
                is AppEvent.LevelUp      -> levelUpState = event.newLevel to event.title
                is AppEvent.StreakUpdated -> { /* handled in BuddyScreen */ }
                is AppEvent.ShowSnackbar -> { /* TODO: snackbar */ }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick  = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(item.icon, item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                NavHost(navController    = navController,
                        startDestination = Screen.Dashboard.route) {
                    composable(Screen.Dashboard.route) { DashboardScreen(vm,
                        onOpenAiScan      = { navController.navigate(Screen.AiMealScan.route) },
                        onOpenBarcodeScan = { navController.navigate(Screen.BarcodeScanner.route) }
                    )}
                    composable(Screen.History.route)         { HistoryScreen(vm) }
                    composable(Screen.Statistics.route)      { StatisticsScreen(vm) }
                    composable(Screen.Buddy.route)           { BuddyScreen(vm) }
                    composable(Screen.MoodTracker.route)     { MoodTrackerScreen(vm) }
                    composable(Screen.Reminders.route)       { RemindersScreen() }
                    composable(Screen.Sos.route)             { SosScreen(vm) }
                    composable(Screen.AiMealScan.route)      { AiMealScanScreen(vm, onBack = {
                        navController.popBackStack()
                    }) }
                    composable(Screen.BarcodeScanner.route)  { BarcodeScannerScreen(vm, onBack = {
                        navController.popBackStack()
                    }) }
                    composable(Screen.Settings.route) {
                        SettingsScreen(vm,
                            onOpenMoodTracker = { navController.navigate(Screen.MoodTracker.route) },
                            onOpenReminders   = { navController.navigate(Screen.Reminders.route) },
                            onOpenHistory     = { navController.navigate(Screen.History.route) }
                        )
                    }
                }
            }
        }

        // XP Toast — floats on top at the top of screen
        xpToast?.let { (amount, reason) ->
            Box(modifier = Modifier.fillMaxSize().padding(top = WindowInsets.statusBars
                .asPaddingValues().calculateTopPadding()),
                contentAlignment = Alignment.TopCenter) {
                XpToast(amount = amount, reason = reason, onDismiss = { xpToast = null })
            }
        }
    }

    // Dialogs
    badgeDialog?.let { badge ->
        BadgeUnlockedDialog(badge = badge, onDismiss = { badgeDialog = null })
    }
    levelUpState?.let { (level, title) ->
        LevelUpDialog(level = level, title = title, onDismiss = { levelUpState = null })
    }
}
