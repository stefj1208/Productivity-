package com.notresemaine.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.ui.AppViewModel
import com.notresemaine.app.ui.GoalsScreen
import com.notresemaine.app.ui.HealthScreen
import com.notresemaine.app.ui.MenusScreen
import com.notresemaine.app.ui.MethodScreen
import com.notresemaine.app.ui.OnboardingScreen
import com.notresemaine.app.ui.PrepareScreen
import com.notresemaine.app.ui.ShoppingScreen
import com.notresemaine.app.ui.ReviewScreen
import com.notresemaine.app.ui.RitualScreen
import com.notresemaine.app.ui.ScreenTimeScreen
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
    Tab("goals", "Objectifs", Icons.Filled.Flag),
    Tab("us", "Nous", Icons.Filled.Favorite)
)

@Composable
private fun MainScaffold(vm: AppViewModel, settings: AppSettings) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(onClick = { capturing = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Noter quelque chose")
            }
        },
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
                TodayScreen(
                    vm, settings,
                    onPrepare = { date -> navController.navigate("prepare/$date") },
                    onRitual = { navController.navigate("ritual") },
                    onSettings = { navController.navigate("settings") },
                    onGoals = { navController.navigate("goals") },
                    onReview = { week -> navController.navigate("review/$week") }
                )
            }
            composable("week") {
                WeekScreen(
                    vm, settings,
                    onReview = { week -> navController.navigate("review/$week") },
                    onMenus = { week -> navController.navigate("menus/$week") },
                    onShopping = { week -> navController.navigate("shopping/$week") }
                )
            }
            composable("goals") {
                GoalsScreen(vm, settings)
            }
            composable("us") {
                UsScreen(vm, settings, onGoToSettings = { navController.navigate("settings") })
            }
            composable("settings") {
                SettingsScreen(
                    vm, settings,
                    onMethod = { navController.navigate("method") },
                    onScreenTime = { navController.navigate("screentime") },
                    onHealth = { navController.navigate("health") }
                )
            }
            composable("health") {
                HealthScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("ritual") {
                RitualScreen(vm, settings, onDone = { navController.popBackStack() })
            }
            composable("method") {
                MethodScreen(onBack = { navController.popBackStack() })
            }
            composable("screentime") {
                ScreenTimeScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("prepare/{date}") { entry ->
                val date = entry.arguments?.getString("date") ?: com.notresemaine.app.data.Dates.tomorrowIso()
                PrepareScreen(vm, settings, date, onDone = { navController.popBackStack() })
            }
            composable("review/{weekStart}") { entry ->
                val week = entry.arguments?.getString("weekStart")
                    ?: com.notresemaine.app.data.Dates.planningTargetWeekIso()
                ReviewScreen(
                    vm, settings, week,
                    onDone = { navController.popBackStack() },
                    onMenus = { w -> navController.navigate("menus/$w") }
                )
            }
            composable("menus/{weekStart}") { entry ->
                val week = entry.arguments?.getString("weekStart")
                    ?: com.notresemaine.app.data.Dates.weekStartIso()
                MenusScreen(
                    vm, settings, week,
                    onShopping = { navController.navigate("shopping/$week") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("shopping/{weekStart}") { entry ->
                val week = entry.arguments?.getString("weekStart")
                    ?: com.notresemaine.app.data.Dates.weekStartIso()
                ShoppingScreen(vm, settings, week, onBack = { navController.popBackStack() })
            }
        }
    }

    if (capturing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { capturing = false },
            title = { Text("Vider sa tête") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Ex. : rappeler le plombier mardi") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.capture(text)
                    capturing = false
                }) { Text("Noter") }
            },
            dismissButton = {
                TextButton(onClick = { capturing = false }) { Text("Annuler") }
            }
        )
    }
}
