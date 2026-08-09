package com.notresemaine.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notresemaine.app.data.AddResult
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.GoalTemplates
import com.notresemaine.app.data.Repository
import com.notresemaine.app.data.RitualStepEntity
import com.notresemaine.app.notif.Reminders
import com.notresemaine.app.pacte.BlockerService
import com.notresemaine.app.pacte.UsageWorker
import com.notresemaine.app.sync.SyncManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository.get(app)
    val sync = SyncManager(repo)

    /** null tant que les réglages ne sont pas encore lus (évite un flash d'écran). */
    val settings: StateFlow<AppSettings?> =
        repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val syncStatus = MutableStateFlow("")
    val aiBusy = MutableStateFlow(false)
    val aiSteps = MutableStateFlow<List<String>>(emptyList())
    val aiGoalPlan = MutableStateFlow<com.notresemaine.app.ai.Assistant.GoalPlan?>(null)
    val aiWeekAdvice = MutableStateFlow<com.notresemaine.app.ai.Assistant.WeekAdvice?>(null)
    val aiDayAdvice = MutableStateFlow<com.notresemaine.app.ai.Assistant.DayAdvice?>(null)
    val aiPacte = MutableStateFlow<com.notresemaine.app.ai.Assistant.PacteAdvice?>(null)
    val aiHealthRead = MutableStateFlow("")

    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            syncRequests.debounce(3_000).collect { sync.syncNow() }
        }
    }

    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    private fun myId(): String = settings.value?.myUserId ?: ""

    private fun toast(msg: String) {
        messages.tryEmit(msg)
    }

    // ----- Démarrage -----

    fun completeOnboarding(name: String, color: String) {
        viewModelScope.launch {
            val userId = UUID.randomUUID().toString()
            repo.settings.completeOnboarding(userId, name, color)
            repo.saveMyProfile(userId, name, color)
            Reminders.reschedule(getApplication(), repo.settings.current())
        }
    }

    // ----- Tâches -----

    fun addTask(title: String, date: String?, weekStart: String?, isSport: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch {
            when (repo.addTask(myId(), title, date, weekStart, isSport)) {
                AddResult.DayFull -> toast("Maximum ${Repository.MAX_TASKS_PER_DAY} tâches par jour — c'est voulu. L'essentiel d'abord.")
                AddResult.Ok -> requestSync()
            }
        }
    }

    fun toggleDone(taskId: String) {
        viewModelScope.launch { repo.toggleDone(taskId); requestSync() }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repo.deleteTask(taskId); requestSync() }
    }

    fun assignTaskToDay(taskId: String, date: String?) {
        viewModelScope.launch { repo.assignTaskToDay(taskId, date); requestSync() }
    }

    fun moveTaskToWeek(taskId: String, weekStart: String) {
        viewModelScope.launch { repo.moveTaskToWeek(taskId, weekStart); requestSync() }
    }

    // ----- Préparer demain / aujourd'hui -----

    fun savePreparation(
        date: String,
        priority: String,
        secondary: List<String>,
        wakeTime: String?,
        focusBlocks: String?
    ) {
        viewModelScope.launch {
            if (priority.isNotBlank()) repo.setDayPriority(myId(), date, priority)
            repo.reconcileSecondary(myId(), date, secondary)
            repo.saveDayPlan(myId(), date, wakeTime, focusBlocks)
            requestSync()
            toast("C'est prêt ✓")
        }
    }

    // ----- Revue du dimanche -----

    fun saveWeekPlan(weekStart: String, priority: String?, abandon: String?, validate: Boolean) {
        viewModelScope.launch {
            repo.saveWeekPlan(myId(), weekStart, priority, abandon, validate)
            requestSync()
            if (validate) toast("Semaine validée ✓")
        }
    }

    // ----- Objectifs -----

    fun addGoal(
        title: String, domain: String, sessionsPerWeek: Int, minutesPerSession: Int,
        preferredTime: String, preferredDays: List<Int>, nextAction: String, isPrivate: Boolean
    ) {
        viewModelScope.launch {
            val ok = repo.addGoal(myId(), title, domain, sessionsPerWeek, minutesPerSession, preferredTime, preferredDays, nextAction, isPrivate)
            if (ok) {
                toast("Objectif créé ✓ Les séances seront planifiées avec la semaine.")
                requestSync()
            } else {
                toast("Maximum ${GoalTemplates.MAX_ACTIVE_GOALS} objectifs actifs — moins mais mieux.")
            }
        }
    }

    fun setGoalActive(goalId: String, active: Boolean) {
        viewModelScope.launch { repo.setGoalActive(goalId, active); requestSync() }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch { repo.deleteGoal(goalId); requestSync() }
    }

    fun planGoalSessions(weekStart: String) {
        viewModelScope.launch {
            val created = repo.planGoalSessions(myId(), weekStart)
            toast(if (created > 0) "$created séances placées dans la semaine ✓" else "Séances déjà en place ✓")
            requestSync()
        }
    }

    // ----- Rituel du matin -----

    fun ensureRitual() {
        viewModelScope.launch { repo.ensureRitualSteps(myId()) }
    }

    fun saveRitualStep(step: RitualStepEntity) {
        viewModelScope.launch { repo.saveRitualStep(step) }
    }

    fun completeRitual(minutes: Int) {
        viewModelScope.launch {
            repo.completeRitual(myId(), minutes)
            requestSync()
            toast("Rituel du matin accompli ✓")
        }
    }

    fun setWakeAlarm(time: String) {
        viewModelScope.launch { repo.settings.setWakeAlarm(time) }
    }

    // ----- Capture rapide -----

    fun capture(text: String) {
        viewModelScope.launch {
            val message = repo.capture(myId(), text)
            if (message.isNotBlank()) {
                toast(message)
                requestSync()
            }
        }
    }

    fun resolveInbox(itemId: String, action: String) {
        viewModelScope.launch {
            repo.resolveInbox(itemId, action, com.notresemaine.app.data.Dates.weekStartIso())
        }
    }

    // ----- Pacte d'écran -----

    /**
     * Serrer le pacte s'applique tout de suite ; le relâcher attend demain.
     * C'est ce délai qui fait de l'app un engagement plutôt qu'un réglage.
     */
    fun savePacte(
        enabled: Boolean,
        socialApps: List<String>,
        limitMinutes: Int,
        curfewEnabled: Boolean,
        curfewStart: String,
        curfewEnd: String,
        curfewStrict: Boolean
    ) {
        viewModelScope.launch {
            val current = repo.settings.current()
            val loosening = current.pacteEnabled &&
                com.notresemaine.app.data.Curfew.isLooser(current, limitMinutes, curfewEnabled, curfewStart, curfewEnd)

            if (loosening) {
                repo.settings.setPending(
                    limitMinutes, curfewStart, curfewEnd,
                    com.notresemaine.app.data.Dates.tomorrowIso()
                )
                // Le durcissement éventuel (couvre-feu activé, mode strict) s'applique quand même.
                repo.settings.setCurfew(curfewEnabled, current.curfewStart, current.curfewEnd, curfewStrict)
                repo.settings.setPacte(enabled, socialApps.joinToString(","), current.dailyLimitMinutes)
                toast("Assouplissement enregistré — il prendra effet demain.")
            } else {
                repo.settings.setPending(0, "", "", "")
                repo.settings.setCurfew(curfewEnabled, curfewStart, curfewEnd, curfewStrict)
                repo.settings.setPacte(enabled, socialApps.joinToString(","), limitMinutes)
                toast(if (enabled) "Pacte enregistré ✓" else "Pacte désactivé")
            }

            val s = repo.settings.current()
            repo.saveMyProfile(s.myUserId, s.myName, s.myColor)
            BlockerService.startIfEnabled(getApplication(), enabled)
            if (enabled) UsageWorker.schedule(getApplication())
            requestSync()
        }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            UsageWorker.collect(getApplication())
            requestSync()
        }
    }

    fun answerGrace(requestId: String, granted: Boolean) {
        viewModelScope.launch {
            repo.answerGrace(requestId, granted)
            requestSync()
            toast(if (granted) "Pause accordée ✓" else "Demande refusée")
        }
    }

    // ----- Menus & courses -----

    fun saveMeal(date: String, slot: String, title: String, ingredients: String) {
        viewModelScope.launch {
            repo.saveMeal(myId(), date, slot, title, ingredients)
            requestSync()
            toast("Menu enregistré ✓")
        }
    }

    /** Remplit les créneaux vides avec la banque d'idées : hors ligne, instantané. */
    fun fillMenusFromBank(weekStart: String) {
        viewModelScope.launch {
            val filled = repo.fillWeekMenusFromBank(myId(), weekStart)
            requestSync()
            toast(if (filled > 0) "$filled repas proposés ✓ Modifie ce qui ne te plaît pas."
            else "Tous les repas sont déjà décidés ✓")
        }
    }

    // ----- L'assistant -----
    //
    // Une seule enveloppe pour tous les boutons « ✨ » de l'application : elle vérifie
    // que l'assistant est prêt, montre l'attente, et transforme une panne en phrase
    // lisible au lieu d'un plantage. Chaque fonction hors ligne reste disponible à côté.

    private fun runAi(block: suspend (String) -> Unit) {
        viewModelScope.launch {
            val s = repo.settings.current()
            if (!s.aiEnabled || s.aiApiKey.isBlank()) {
                toast("Active d'abord l'assistant dans Réglages.")
                return@launch
            }
            aiBusy.value = true
            runCatching { block(s.aiApiKey) }.onFailure { e ->
                toast(
                    if (e is com.notresemaine.app.ai.Ai.AiException) e.message ?: "Assistant indisponible."
                    else "Assistant indisponible : ${e.message ?: "erreur inconnue"}"
                )
            }
            aiBusy.value = false
        }
    }

    /** Menus sur mesure. Envoie : uniquement vos contraintes de repas. */
    fun suggestMenusWithAi(weekStart: String, constraints: String) = runAi { key ->
        val suggestions = com.notresemaine.app.ai.Assistant.suggestWeekMenus(key, constraints)
        val applied = repo.applyMenuSuggestions(myId(), weekStart, suggestions)
        requestSync()
        toast(if (applied > 0) "$applied repas proposés ✓" else "Rien à ajouter, la semaine est déjà pleine.")
    }

    /** Trois premières actions. Envoie : uniquement l'intitulé de l'objectif. */
    fun suggestFirstStepsWithAi(goalTitle: String) = runAi { key ->
        aiSteps.value = com.notresemaine.app.ai.Assistant.suggestFirstSteps(key, goalTitle)
    }

    fun clearAiSteps() {
        aiSteps.value = emptyList()
    }

    /** Rythme complet d'un objectif : séances, durée, moment, jours, première action. */
    fun suggestGoalPlanWithAi(goalTitle: String, domain: String) = runAi { key ->
        val plan = com.notresemaine.app.ai.Assistant.suggestGoalPlan(key, goalTitle, domain)
        if (plan == null) toast("L'assistant n'a pas su proposer de rythme. Réessaie.")
        aiGoalPlan.value = plan
    }

    fun clearAiGoalPlan() {
        aiGoalPlan.value = null
    }

    /** Priorité de la semaine. Envoie : vos objectifs non privés et vos notes en attente. */
    fun suggestWeekPriorityWithAi(weekStart: String) = runAi { key ->
        val advice = com.notresemaine.app.ai.Assistant.suggestWeekPriority(
            key,
            goals = repo.goalTitlesForAi(myId()),
            inbox = repo.inboxTextsForAi(myId())
        )
        if (advice == null) toast("L'assistant n'a rien su proposer. Réessaie.")
        aiWeekAdvice.value = advice
    }

    fun clearAiWeekAdvice() {
        aiWeekAdvice.value = null
    }

    /** Priorité du jour. Envoie : priorité de la semaine, objectifs non privés, tâches en attente. */
    fun suggestDayWithAi(date: String) = runAi { key ->
        val weekStart = com.notresemaine.app.data.Dates.weekStartIso(java.time.LocalDate.parse(date))
        val advice = com.notresemaine.app.ai.Assistant.suggestTomorrow(
            key,
            weekPriority = repo.weekPriorityOf(myId(), weekStart),
            goals = repo.goalTitlesForAi(myId()),
            backlog = repo.backlogTitlesForAi(myId(), weekStart)
        )
        if (advice == null) toast("L'assistant n'a rien su proposer. Réessaie.")
        aiDayAdvice.value = advice
    }

    fun clearAiDayAdvice() {
        aiDayAdvice.value = null
    }

    /** Capture clarifiée. Envoie : uniquement la note que vous venez d'écrire. */
    fun captureWithAi(text: String) = runAi { key ->
        val clarified = com.notresemaine.app.ai.Assistant.clarifyCapture(key, text)
        if (clarified == null) {
            // L'assistant n'a pas compris : la capture hors ligne prend le relais.
            val message = repo.capture(myId(), text)
            if (message.isNotBlank()) toast(message)
        } else {
            val message = repo.applyClarifiedCapture(myId(), clarified.action, clarified.whenLabel)
            if (message.isNotBlank()) toast("« ${clarified.action} » · $message")
        }
        requestSync()
    }

    /** Rangement des articles restés dans « Divers ». Envoie : uniquement ces articles. */
    fun sortShoppingWithAi(weekStart: String) = runAi { key ->
        val labels = repo.unsortedShoppingLabels(weekStart)
        if (labels.isEmpty()) {
            toast("Tout est déjà rangé ✓")
        } else {
            val aisles = com.notresemaine.app.ai.Assistant.classifyAisles(key, labels)
            val changed = repo.applyAisles(weekStart, aisles)
            requestSync()
            toast(if (changed > 0) "$changed articles rangés ✓" else "Aucun rayon reconnu.")
        }
    }

    /** Pacte réaliste. Envoie : uniquement vos moyennes d'écran et votre heure de lever. */
    fun suggestPacteWithAi() = runAi { key ->
        val averages = repo.usageAverages(myId())
        if (averages.days == 0) {
            toast("Pas encore de relevé : touche « Relever maintenant » d'abord.")
        } else {
            val settings = repo.settings.current()
            val advice = com.notresemaine.app.ai.Assistant.suggestPacte(
                key, averages.socialMinutes, averages.unlocks, settings.wakeAlarm
            )
            if (advice == null) toast("L'assistant n'a rien su proposer. Réessaie.")
            aiPacte.value = advice
        }
    }

    fun clearAiPacte() {
        aiPacte.value = null
    }

    /** Lecture de la semaine. Envoie : uniquement vos moyennes, jamais celles de l'autre. */
    fun readHealthWithAi(weekStart: String) = runAi { key ->
        val averages = repo.healthAverages(myId(), weekStart)
        if (averages.days == 0) {
            toast("Aucune donnée cette semaine.")
        } else {
            aiHealthRead.value = com.notresemaine.app.ai.Assistant.readWeek(
                key, averages.sleepMinutes, averages.steps, averages.exerciseMinutes
            )
        }
    }

    fun clearAiHealthRead() {
        aiHealthRead.value = ""
    }

    fun saveAiSettings(enabled: Boolean, apiKey: String) {
        viewModelScope.launch {
            repo.settings.setAi(enabled, apiKey)
            toast(if (enabled) "Assistant activé ✓" else "Assistant désactivé")
        }
    }

    fun generateShoppingList(weekStart: String) {
        viewModelScope.launch {
            val count = repo.generateShoppingList(myId(), weekStart)
            requestSync()
            toast(
                if (count > 0) "$count articles regroupés par rayon ✓"
                else "Renseigne d'abord les ingrédients des menus."
            )
        }
    }

    fun addShoppingItem(weekStart: String, label: String) {
        viewModelScope.launch { repo.addShoppingItem(myId(), weekStart, label); requestSync() }
    }

    fun toggleShoppingItem(itemId: String) {
        viewModelScope.launch { repo.toggleShoppingItem(itemId); requestSync() }
    }

    fun clearCheckedShopping(weekStart: String) {
        viewModelScope.launch { repo.clearCheckedShopping(weekStart); requestSync() }
    }

    // ----- Sommeil & sport -----

    fun refreshHealth() {
        viewModelScope.launch {
            com.notresemaine.app.health.HealthWorker.collect(getApplication())
            requestSync()
        }
    }

    fun saveSleepManually(date: String, bedTime: String, wakeTime: String) {
        viewModelScope.launch {
            repo.saveSleepManually(myId(), date, bedTime, wakeTime)
            requestSync()
            toast("Nuit enregistrée ✓")
        }
    }

    fun addExerciseManually(date: String, minutes: Int) {
        viewModelScope.launch {
            repo.addExerciseManually(myId(), date, minutes)
            requestSync()
            toast("Séance de $minutes min enregistrée ✓")
        }
    }

    // ----- Nous -----

    fun sendBravo(toUser: String) {
        viewModelScope.launch {
            repo.sendBravo(myId(), toUser, "👏 Bravo !")
            requestSync()
            toast("Bravo envoyé 👏")
        }
    }

    // ----- Réglages -----

    fun saveProfile(name: String, color: String) {
        viewModelScope.launch {
            repo.settings.setProfile(name, color)
            repo.saveMyProfile(myId(), name, color)
            requestSync()
            toast("Profil enregistré ✓")
        }
    }

    fun saveReminders(evening: String, eveningOn: Boolean, sunday: String, sundayOn: Boolean) {
        viewModelScope.launch {
            repo.settings.setReminders(evening, eveningOn, sunday, sundayOn)
            Reminders.reschedule(getApplication(), repo.settings.current())
            toast("Rappels enregistrés ✓")
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { repo.settings.setThemeMode(mode) }
    }

    fun saveSupabaseConfig(url: String, key: String) {
        viewModelScope.launch {
            repo.settings.setSupabaseConfig(url, key)
            toast("Configuration enregistrée ✓")
        }
    }

    fun signUp(email: String, password: String) = authAction { sync.signUp(email, password) }
    fun signIn(email: String, password: String) = authAction { sync.signIn(email, password) }

    private fun authAction(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            syncStatus.value = "Connexion…"
            block().fold(
                onSuccess = {
                    syncStatus.value = "Connecté ✓"
                    requestSync()
                },
                onFailure = { syncStatus.value = it.message ?: "Erreur de connexion" }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sync.signOut()
            syncStatus.value = ""
        }
    }

    fun createCouple() {
        viewModelScope.launch {
            syncStatus.value = "Création…"
            sync.createCouple().fold(
                onSuccess = { syncStatus.value = "Espace créé ✓ Code à partager : $it" },
                onFailure = { syncStatus.value = it.message ?: "Erreur" }
            )
        }
    }

    fun joinCouple(code: String) {
        viewModelScope.launch {
            syncStatus.value = "Vérification…"
            sync.joinCouple(code).fold(
                onSuccess = {
                    syncStatus.value = "Espace rejoint ✓"
                    requestSync()
                },
                onFailure = { syncStatus.value = it.message ?: "Code invalide" }
            )
        }
    }

    fun syncNowManual() {
        viewModelScope.launch {
            syncStatus.value = "Synchronisation…"
            sync.syncNow().fold(
                onSuccess = { syncStatus.value = "À jour ✓" },
                onFailure = { syncStatus.value = "Hors ligne — nouvelle tentative plus tard" }
            )
        }
    }
}
