package com.glycomate.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: GlycoViewModel = viewModel()
            val theme by vm.repo.prefs.appTheme.collectAsState(initial = "System")
            var showCustomSplash by remember { mutableStateOf(true) }

            GlycoMateTheme(theme = theme) {
                if (showCustomSplash) {
                    SplashScreenContent(onFinished = { showCustomSplash = false })
                } else {
                    GlycoMateRoot(vm)
                }
            }
        }
    }
}

@Composable
fun SplashScreenContent(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter      = painterResource(id = R.drawable.splash_logo),
            contentDescription = null,
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun GlycoMateRoot(vm: GlycoViewModel) {
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

    var xpToast:      Pair<Int, String>? by remember { mutableStateOf(null) }
    var badgeDialog:  Badge?             by remember { mutableStateOf(null) }
    var levelUpState: Pair<Int, Int>?    by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is AppEvent.XpGained     -> xpToast = event.amount to event.reason
                is AppEvent.BadgeUnlocked -> badgeDialog = event.badge
                is AppEvent.LevelUp      -> levelUpState = event.newLevel to event.titleRes
                else -> {}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val label = stringResource(item.labelRes)
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
                            icon  = { Icon(item.icon, label) },
                            label = { Text(label) }
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
                    composable(Screen.AiMealScan.route)      { AiMealScanScreen(vm, onBack = { navController.popBackStack() }) }
                    composable(Screen.BarcodeScanner.route)  { BarcodeScannerScreen(vm, onBack = { navController.popBackStack() }) }
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

        xpToast?.let { (amount, reason) ->
            Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                XpToast(amount = amount, reason = reason, onDismiss = { xpToast = null })
            }
        }
    }

    badgeDialog?.let { BadgeUnlockedDialog(badge = it, onDismiss = { badgeDialog = null }) }
    levelUpState?.let { LevelUpDialog(level = it.first, titleRes = it.second, onDismiss = { levelUpState = null }) }
}
