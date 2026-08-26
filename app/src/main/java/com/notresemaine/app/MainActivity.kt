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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.sp
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
import com.notresemaine.app.ui.AssistantScreen
import com.notresemaine.app.ui.MeScreen
import com.notresemaine.app.ui.ProfileScreen
import com.notresemaine.app.ui.RemindersScreen
import com.notresemaine.app.ui.SyncScreen
import com.notresemaine.app.ui.DayScreen
import com.notresemaine.app.ui.PerformanceScreen
import com.notresemaine.app.ui.WeightScreen
import com.notresemaine.app.ui.CalendarScreen
import com.notresemaine.app.ui.InboxScreen
import com.notresemaine.app.ui.AgendaScreen
import com.notresemaine.app.ui.HabitsScreen
import com.notresemaine.app.ui.AskScreen
import com.notresemaine.app.ui.SportScreen
import com.notresemaine.app.ui.VoiceButton
import com.notresemaine.app.ui.VoiceProposalDialog
import com.notresemaine.app.ui.PlanningScreen
import com.notresemaine.app.ui.UsScreen
import com.notresemaine.app.ui.HouseScreen
import com.notresemaine.app.ui.MealLogScreen
import com.notresemaine.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Destination demandée par un écran de rappel (« Bloquer un créneau »). */
        const val EXTRA_ROUTE = "route"
    }

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
                        else -> MainScaffold(vm, s, intent?.getStringExtra(EXTRA_ROUTE).orEmpty())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.requestSync()
        vm.ensureBlockerRunning()
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// Cinq destinations, une question chacune : qu'est-ce que je fais et quand
// (Planning), où je vais (Objectifs), qu'est-ce qu'on mange (Maison),
// où on en est à deux (Nous), et ce qui me concerne (Moi).
// « Planning » récapitule le jour ET la semaine : c'est la même question à deux
// échelles. « Moi » remplace la roue dentée : une fonction cachée est morte.
private val tabs = listOf(
    Tab("planning", "Planning", Icons.Filled.WbSunny),
    Tab("goals", "Objectifs", Icons.Filled.Flag),
    Tab("house", "Maison", Icons.Filled.Home),
    Tab("us", "Nous", Icons.Filled.Favorite),
    Tab("me", "Moi", Icons.Filled.Person)
)

@Composable
private fun MainScaffold(vm: AppViewModel, settings: AppSettings, openRoute: String = "") {
    val navController = rememberNavController()

    // Arrivée depuis un écran de rappel : on ouvre directement le bon endroit.
    LaunchedEffect(openRoute) {
        if (openRoute.isNotBlank()) runCatching { navController.navigate(openRoute) }
    }
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
                        label = {
                            // Sur un écran étroit — un Honor, par exemple — le
                            // « s » de « Planning » et d'« Objectifs » basculait
                            // seul à la ligne.
                            //
                            // 12 sp au lieu de 14 : c'est la seule entorse de
                            // l'application à sa règle des 14 sp, et elle est
                            // assumée ici. C'est aussi la taille standard d'Android
                            // pour une barre de navigation, l'icône porte déjà le
                            // sens, et un mot coupé en deux se lit bien plus mal
                            // qu'un mot un peu plus petit.
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
        NavHost(
            navController = navController,
            startDestination = "planning"
        ) {
            composable("planning") {
                PlanningScreen(
                    vm, settings,
                    onPrepare = { date -> navController.navigate("prepare/$date") },
                    onDay = { date -> navController.navigate("day/$date") },
                    onRitual = { navController.navigate("ritual") },
                    onGoals = { navController.navigate("goals_pushed") },
                    onReview = { week -> navController.navigate("review/$week") },
                    onMenus = { week -> navController.navigate("menus/$week") },
                    onShopping = { week -> navController.navigate("shopping/$week") },
                    onScreenTime = { navController.navigate("screentime") },
                    onHealth = { navController.navigate("health") },
                    onPerformance = { navController.navigate("performance") },
                    onWeight = { navController.navigate("weight") },
                    onAgenda = { navController.navigate("agenda") },
                    onInbox = { navController.navigate("inbox") },
                    onHabits = { navController.navigate("habits") },
                    onSport = { navController.navigate("sport") },
                    onAsk = { navController.navigate("ask") },
                    onMealLog = { navController.navigate("meallog") },
                    onMethod = { navController.navigate("method") }
                )
            }
            composable("goals") {
                GoalsScreen(vm, settings)
            }
            composable("goals_pushed") {
                GoalsScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("house") {
                HouseScreen(
                    vm, settings,
                    onMenus = { week -> navController.navigate("menus/$week") },
                    onShopping = { week -> navController.navigate("shopping/$week") },
                    onMealLog = { navController.navigate("meallog") }
                )
            }
            composable("us") {
                UsScreen(
                    vm, settings,
                    onGoToSettings = { navController.navigate("sync") },
                    onScreenTime = { navController.navigate("screentime") },
                    onHealth = { navController.navigate("health") }
                )
            }
            composable("me") {
                MeScreen(
                    vm, settings,
                    onRitual = { navController.navigate("ritual") },
                    onScreenTime = { navController.navigate("screentime") },
                    onHealth = { navController.navigate("health") },
                    onPerformance = { navController.navigate("performance") },
                    onWeight = { navController.navigate("weight") },
                    onHabits = { navController.navigate("habits") },
                    onSport = { navController.navigate("sport") },
                    onAsk = { navController.navigate("ask") },
                    onMealLog = { navController.navigate("meallog") },
                    onCalendar = { navController.navigate("calendar") },
                    onAssistant = { navController.navigate("assistant") },
                    onReminders = { navController.navigate("reminders") },
                    onMethod = { navController.navigate("method") },
                    onSync = { navController.navigate("sync") },
                    onProfile = { navController.navigate("profile") }
                )
            }
            composable("day/{date}") { entry ->
                val date = entry.arguments?.getString("date") ?: com.notresemaine.app.data.Dates.todayIso()
                DayScreen(
                    vm, settings, date,
                    onPrepare = { d -> navController.navigate("prepare/$d") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("agenda") {
                AgendaScreen(
                    vm, settings,
                    onDay = { date -> navController.navigate("day/$date") },
                    onSettings = { navController.navigate("calendar") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("ask") {
                AskScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("meallog") {
                MealLogScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("habits") {
                HabitsScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("sport") {
                SportScreen(
                    vm, settings,
                    onGoals = { navController.navigate("goals_pushed") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("inbox") {
                InboxScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("weight") {
                WeightScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("calendar") {
                CalendarScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("performance") {
                PerformanceScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("assistant") {
                AssistantScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("reminders") {
                RemindersScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("sync") {
                SyncScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("profile") {
                ProfileScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("health") {
                HealthScreen(vm, settings, onBack = { navController.popBackStack() })
            }
            composable("ritual") {
                RitualScreen(
                    vm, settings,
                    onPrepare = { date -> navController.navigate("prepare/$date") },
                    onDone = { navController.popBackStack() }
                )
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

        // Le micro, présent sur toutes les pages : parler doit rester le geste
        // le plus court, sinon on ne s'en sert que le premier jour.
        VoiceButton(
            vm = vm,
            settings = settings,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(end = 16.dp, top = 12.dp)
        )
        }
    }

    if (capturing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { capturing = false },
            title = { Text("Vider sa tête") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Ex. : rappeler le plombier mardi") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                        val aiBusy by vm.aiBusy.collectAsState()
                        com.notresemaine.app.ui.AiButton(
                            text = "Clarifier et ranger",
                            busy = aiBusy,
                            enabled = text.isNotBlank(),
                            // La boîte reste ouverte : la proposition s'affiche
                            // par-dessus, et rien n'est enregistré sans validation.
                            onClick = { vm.captureWithAi(text) },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            text = "Vous verrez ce qu'il a compris avant que quoi que ce soit " +
                                "ne soit enregistré.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
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

    VoiceProposalDialog(vm)

    // Le rappel qui arrive pendant qu'on est dans l'application : l'alarme
    // plein écran, elle, ne s'affiche que téléphone posé.
    com.notresemaine.app.ui.DueReminderPopup(vm, settings) { route ->
        navController.navigate(route)
    }

    // La proposition de l'assistant, montrée avant d'agir.
    val proposal by vm.aiCapture.collectAsState()
    proposal?.let { p ->
        com.notresemaine.app.ui.CaptureProposalDialog(
            proposal = p,
            onAccept = { action, whenLabel ->
                vm.acceptCapture(action, whenLabel)
                capturing = false
            },
            onReject = {
                vm.rejectCapture()
                capturing = false
            },
            onDismiss = { vm.clearAiCapture() }
        )
    }
}
