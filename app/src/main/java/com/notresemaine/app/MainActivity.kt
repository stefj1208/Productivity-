package com.notresemaine.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.ui.AppViewModel
import com.notresemaine.app.ui.OnboardingScreen
import com.notresemaine.app.ui.PrepareScreen
import com.notresemaine.app.ui.ReviewScreen
import com.notresemaine.app.ui.SettingsScreen
import com.notresemaine.app.ui.TodayScreen
import com.notresemaine.app.ui.UsScreen
import com.notresemaine.app.ui.WeekScreen
import com.notresemaine.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            val s = settings
            AppTheme(mode = s?.themeMode ?: "sombre") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        s == null -> {} // réglages en cours de lecture
                        !s.onboarded -> OnboardingScreen(vm)
                        else -> MainScaffold(vm, s)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.requestSync()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Aujourd'hui", Icons.Filled.WbSunny),
    Tab("week", "Semaine", Icons.Filled.DateRange),
    Tab("us", "Nous", Icons.Filled.Favorite),
    Tab("settings", "Réglages", Icons.Filled.Settings)
)

@Composable
private fun MainScaffold(vm: AppViewModel, settings: AppSettings) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding)
        ) {
            composable("today") {
                TodayScreen(vm, settings, onPrepare = { date -> navController.navigate("prepare/$date") })
            }
            composable("week") {
                WeekScreen(vm, settings, onReview = { navController.navigate("review") })
            }
            composable("us") {
                UsScreen(vm, settings, onGoToSettings = { navController.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(vm, settings)
            }
            composable("prepare/{date}") { entry ->
                val date = entry.arguments?.getString("date") ?: com.notresemaine.app.data.Dates.tomorrowIso()
                PrepareScreen(vm, settings, date, onDone = { navController.popBackStack() })
            }
            composable("review") {
                ReviewScreen(vm, settings, onDone = { navController.popBackStack() })
            }
        }
    }
}
