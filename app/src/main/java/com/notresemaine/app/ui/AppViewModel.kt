package com.notresemaine.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notresemaine.app.data.AddResult
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.GoalTemplates
import com.notresemaine.app.data.Repository
import com.notresemaine.app.data.RitualStepEntity
import com.notresemaine.app.notif.Alarms
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
    val aiWeightRead = MutableStateFlow("")
    val aiPlan = MutableStateFlow<com.notresemaine.app.ai.Assistant.ActionPlan?>(null)
    val aiReview = MutableStateFlow<com.notresemaine.app.ai.Assistant.WeekReview?>(null)
    val aiAbandon = MutableStateFlow<List<String>>(emptyList())

    /** Ce que l'assistant propose pour une note capturée, tant que rien n'est validé. */
    data class CaptureProposal(
        val original: String,
        val action: String,
        val whenLabel: String,
        val why: String
    )

    val aiCapture = MutableStateFlow<CaptureProposal?>(null)

    /** Le repas proposé pour remplacer celui du jour — tant qu'on n'a pas dit oui. */
    data class MealProposal(
        val date: String,
        val slot: String,
        val previousTitle: String,
        val suggestion: com.notresemaine.app.ai.Assistant.MealSuggestion
    )

    val aiMeal = MutableStateFlow<MealProposal?>(null)
    val aiHabits = MutableStateFlow<List<com.notresemaine.app.ai.Assistant.HabitIdea>>(emptyList())
    val aiSport = MutableStateFlow<List<com.notresemaine.app.ai.Assistant.SportSession>>(emptyList())
    val aiAgenda = MutableStateFlow("")

    /** L'échange de questions-réponses, du plus ancien au plus récent. */
    data class Exchange(val question: String, val answer: String)

    val aiConversation = MutableStateFlow<List<Exchange>>(emptyList())

    /**
     * Les consignes du jour pour le rituel : étape → quoi faire ce matin.
     * Volontairement non enregistrées — elles ne valent que pour aujourd'hui.
     */
    val aiRitual = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Ce que l'assistant a compris d'une phrase dictée, avant toute action. */
    val aiVoice = MutableStateFlow<com.notresemaine.app.ai.Assistant.VoiceCommand?>(null)

    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            syncRequests.debounce(3_000).collect {
                sync.syncNow()
                com.notresemaine.app.notif.Announcements.afterSync(getApplication(), repo)
            }
        }
    }

    /** Réserve le rituel dans le planning : une intention sans heure n'arrive pas. */
    fun bookRitualSlot(totalMinutes: Int) {
        viewModelScope.launch {
            val s = repo.settings.current()
            val today = com.notresemaine.app.data.Dates.todayIso()
            val existing = repo.db.tasks().byDateOnce(myId(), today)
                .firstOrNull { it.title == "Rituel du matin" && !it.deleted }
            val id = existing?.id ?: run {
                repo.addTask(myId(), "Rituel du matin", today, null, isSport = true)
                repo.db.tasks().byDateOnce(myId(), today)
                    .firstOrNull { it.title == "Rituel du matin" && !it.deleted }?.id
            }
            if (id == null) {
                toast("Impossible de poser le créneau.")
                return@launch
            }
            repo.setTaskSlot(id, s.wakeAlarm, totalMinutes.coerceAtLeast(5))
            Alarms.rescheduleAll(getApplication())
            repo.pushDayToCalendar(getApplication(), myId(), today)
            requestSync()
            toast("Créneau posé à ${s.wakeAlarm} ✓")
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
            Alarms.rescheduleAll(getApplication())
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

    fun renameTask(taskId: String, title: String) {
        viewModelScope.launch { repo.renameTask(taskId, title); requestSync() }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repo.deleteTask(taskId); requestSync() }
    }

    fun giveTaskToPartner(taskId: String) {
        viewModelScope.launch {
            val partner = repo.db.profiles().partnerOf(myId())
            if (partner == null) {
                toast("Personne n'est encore relié à vous (Moi → Synchronisation).")
                return@launch
            }
            repo.giveTaskToPartner(taskId, partner.id, myId())
            requestSync()
            toast("Confiée à ${partner.name} ✓")
        }
    }

    /** Réserve un créneau et reprogramme les rappels : la tâche devient un rendez-vous. */
    fun setTaskSlot(taskId: String, startTime: String, durationMinutes: Int) {
        viewModelScope.launch {
            repo.setTaskSlot(taskId, startTime, durationMinutes)
            Alarms.rescheduleAll(getApplication())
            // Réserver une heure, c'est prendre un rendez-vous : il va dans l'agenda.
            repo.db.tasks().byId(taskId)?.date?.let { date ->
                repo.pushDayToCalendar(getApplication(), myId(), date)
            }
            requestSync()
        }
    }

    fun saveHouseItem(id: String?, section: String, title: String, detail: String, amount: Double, dueDate: String?) {
        viewModelScope.launch {
            repo.saveHouseItem(id, section, title, detail, amount, dueDate)
            requestSync()
            toast("Enregistré ✓")
        }
    }

    fun toggleHouseItem(id: String) {
        viewModelScope.launch { repo.toggleHouseItem(id); requestSync() }
    }

    fun deleteHouseItem(id: String) {
        viewModelScope.launch { repo.deleteHouseItem(id); requestSync() }
    }

    fun assignTaskToDay(taskId: String, date: String?) {
        viewModelScope.launch { repo.assignTaskToDay(taskId, date); requestSync() }
    }

    /** Étale les tâches sans date sur les jours les moins chargés. */
    fun spreadTasks(weekStart: String) {
        viewModelScope.launch {
            val placed = repo.spreadTasksOverWeek(myId(), weekStart)
            requestSync()
            toast(
                if (placed > 0) "$placed tâche" + (if (placed > 1) "s" else "") + " posée" +
                    (if (placed > 1) "s" else "") + " ✓"
                else "Rien à poser — ou les jours sont déjà pleins."
            )
        }
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
                // Les séances sont posées TOUT DE SUITE, sur cette semaine et la
                // suivante. Avant, elles attendaient le bilan du dimanche : on
                // créait un objectif et il ne se passait rien, ce qui est la
                // meilleure façon de ne jamais s'y mettre.
                val thisWeek = com.notresemaine.app.data.Dates.weekStartIso()
                val nextWeek = com.notresemaine.app.data.Dates.weekStartIsoOffset(1)
                val placed = repo.planGoalSessions(myId(), thisWeek) +
                    repo.planGoalSessions(myId(), nextWeek)
                // Un objectif, ce sont des rendez-vous : ils entrent dans les rappels.
                Alarms.rescheduleAll(getApplication())
                // Et dans l'agenda du téléphone, si on l'a relié.
                repo.pushWeekToCalendar(getApplication(), myId(), thisWeek)
                requestSync()
                toast(
                    if (placed > 0) "Objectif créé ✓ $placed séances posées dans le planning"
                    else "Objectif créé ✓"
                )
            } else {
                toast("Maximum ${GoalTemplates.MAX_ACTIVE_GOALS} objectifs actifs — moins mais mieux.")
            }
        }
    }

    fun updateGoal(
        goalId: String, title: String, sessionsPerWeek: Int, minutesPerSession: Int,
        preferredTime: String, preferredDays: List<Int>, nextAction: String, isPrivate: Boolean
    ) {
        viewModelScope.launch {
            repo.updateGoal(goalId, title, sessionsPerWeek, minutesPerSession,
                preferredTime, preferredDays, nextAction, isPrivate)
            // Changer le rythme doit changer le planning tout de suite, sinon on
            // modifie un objectif sans qu'il se passe quoi que ce soit.
            val thisWeek = com.notresemaine.app.data.Dates.weekStartIso()
            val placed = repo.planGoalSessions(myId(), thisWeek) +
                repo.planGoalSessions(myId(), com.notresemaine.app.data.Dates.weekStartIsoOffset(1))
            Alarms.rescheduleAll(getApplication())
            repo.pushWeekToCalendar(getApplication(), myId(), thisWeek)
            requestSync()
            toast(
                if (placed > 0) "Objectif modifié ✓ $placed séances ajoutées"
                else "Objectif modifié ✓"
            )
        }
    }

    fun setGoalActive(goalId: String, active: Boolean) {
        viewModelScope.launch {
            repo.setGoalActive(goalId, active)
            Alarms.rescheduleAll(getApplication())
            requestSync()
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            repo.deleteGoal(goalId)
            Alarms.rescheduleAll(getApplication())
            requestSync()
        }
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

    /**
     * Guide le rituel pour LA journée qui commence.
     * Envoie : les intitulés de vos étapes, votre priorité du jour et vos
     * objectifs non privés. Rien d'autre.
     */
    fun guideRitualWithAi() = runAi { key ->
        val steps = repo.db.ritual().stepsOnce(myId())
            .filter { it.enabled }
            .map { it.name to it.minutes }
        if (steps.isEmpty()) {
            toast("Aucune étape active dans le rituel.")
            return@runAi
        }
        val today = com.notresemaine.app.data.Dates.todayIso()
        val priority = repo.db.tasks().priorityOfDay(myId(), today)?.title.orEmpty()
        val guides = com.notresemaine.app.ai.Assistant.guideRitual(
            key, steps, priority, repo.goalTitlesForAi(myId())
        )
        if (guides.isEmpty()) toast("L'assistant n'a pas su guider le rituel. Réessaie.")
        aiRitual.value = guides.associate { it.step.lowercase().trim() to it.instruction }
    }

    fun clearAiRitual() {
        aiRitual.value = emptyMap()
    }

    fun completeRitual(minutes: Int) {
        viewModelScope.launch {
            repo.completeRitual(myId(), minutes)
            requestSync()
            toast("Rituel du matin accompli ✓")
        }
    }

    fun setWakeAlarm(time: String) {
        viewModelScope.launch {
            repo.settings.setWakeAlarm(time)
            Alarms.rescheduleAll(getApplication())
        }
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

    /** Une note traitée par erreur repart en attente : rien n'est définitif. */
    fun reopenInbox(itemId: String) {
        viewModelScope.launch { repo.reopenInbox(itemId); requestSync() }
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
            // Le couvre-feu vient de bouger : le rappel « dans 15 minutes » aussi.
            Alarms.rescheduleAll(getApplication())
            BlockerService.startIfEnabled(getApplication(), enabled)
            if (enabled) UsageWorker.schedule(getApplication())
            requestSync()
        }
    }

    // ----- Catégories : de l'intention au plan d'action -----

    /** Envoie : la catégorie, l'intitulé et la description que vous avez écrits. */
    fun planActionWithAi(category: String, title: String, description: String) = runAi { key ->
        val plan = com.notresemaine.app.ai.Assistant.planAction(
            key, com.notresemaine.app.data.Categories.labelOf(category), title, description
        )
        if (plan == null) toast("L'assistant n'a pas su découper ça. Reformule en une phrase.")
        aiPlan.value = plan
    }

    fun clearAiPlan() {
        aiPlan.value = null
    }

    /** Crée la tâche directement chez le binôme : elle porte la trace de qui l'a confiée. */
    fun addTaskForPartner(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val partner = repo.db.profiles().partnerOf(myId())
            if (partner == null) {
                toast("Personne n'est encore relié à vous (Moi → Synchronisation).")
                return@launch
            }
            repo.addTaskAssigned(partner.id, myId(), title, com.notresemaine.app.data.Dates.weekStartIso())
            requestSync()
            toast("Confiée à ${partner.name} ✓")
        }
    }

    /** Une routine, c'est un objectif hebdomadaire : deux séances par semaine par défaut. */
    fun makeRoutineFromPlan(title: String, category: String) {
        viewModelScope.launch {
            val plan = aiPlan.value
            val ok = repo.addGoal(
                userId = myId(),
                title = title,
                domain = category,
                sessionsPerWeek = 2,
                minutesPerSession = 30,
                preferredTime = "soir",
                preferredDays = listOf(2, 5),
                nextAction = plan?.steps?.firstOrNull() ?: title,
                isPrivate = false
            )
            if (ok) {
                Alarms.rescheduleAll(getApplication())
                requestSync()
                toast("Routine créée ✓ Les séances seront placées avec la semaine.")
            } else {
                toast("Maximum 3 objectifs actifs — terminez-en un d'abord.")
            }
            aiPlan.value = null
        }
    }

    // ----- Bilan de la semaine et boîte de réception -----

    /**
     * Envoie : uniquement vos chiffres agrégés. Ni le détail, ni rien de l'autre.
     *
     * L'écart entre le menu prévu et ce qui a été réellement noté est ajouté ici
     * plutôt que dans l'écran, parce qu'il demande de lire deux tables — et qu'un
     * écran ne doit pas attendre une base de données pour s'afficher.
     */
    fun reviewWeekWithAi(facts: String, weekStart: String = "") = runAi { key ->
        val gap = if (weekStart.isBlank()) "" else repo.menuGapForWeek(myId(), weekStart)
        val review = com.notresemaine.app.ai.Assistant.reviewWeek(
            key, if (gap.isBlank()) facts else "$facts\n$gap"
        )
        if (review == null) toast("L'assistant n'a pas su faire le bilan. Réessaie.")
        aiReview.value = review
    }

    fun clearAiReview() {
        aiReview.value = null
    }

    /** Envoie : votre priorité, vos objectifs non privés, le nombre de tâches en attente. */
    fun suggestAbandonWithAi(priority: String, pendingTasks: Int) = runAi { key ->
        aiAbandon.value = com.notresemaine.app.ai.Assistant.suggestAbandon(
            key, priority, repo.goalTitlesForAi(myId()), pendingTasks
        )
    }

    fun clearAiAbandon() {
        aiAbandon.value = emptyList()
    }

    /**
     * Vide la boîte de réception d'un coup : chaque note devient une action datée.
     * Envoie : uniquement le texte de vos notes.
     */
    fun inboxToActionsWithAi(weekStart: String) = runAi { key ->
        val notes = repo.db.inbox().pendingOnce(myId())
        if (notes.isEmpty()) {
            toast("La boîte est déjà vide.")
            return@runAi
        }
        val actions = com.notresemaine.app.ai.Assistant.inboxToActions(key, notes.map { it.text })
        val applied = repo.applyInboxActions(myId(), notes, actions, weekStart)
        requestSync()
        toast(
            if (applied > 0) "$applied note" + (if (applied > 1) "s" else "") + " transformée" +
                (if (applied > 1) "s" else "") + " en action ✓"
            else "Rien n'a pu être transformé — triez à la main."
        )
    }

    // ----- Habitudes -----

    fun addHabit(title: String, source: String) {
        viewModelScope.launch {
            repo.saveHabit(null, myId(), title, source, 8, 21, 2)
            Alarms.rescheduleAll(getApplication())
            requestSync()
            toast("Habitude ajoutée ✓")
        }
    }

    fun saveHabit(id: String?, title: String, source: String, from: Int, to: Int, perDay: Int) {
        viewModelScope.launch {
            repo.saveHabit(id, myId(), title, source, from, to, perDay)
            Alarms.rescheduleAll(getApplication())
            requestSync()
            toast("Enregistré ✓")
        }
    }

    fun toggleHabit(id: String) {
        viewModelScope.launch {
            repo.toggleHabit(id)
            Alarms.rescheduleAll(getApplication())
            requestSync()
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch {
            repo.deleteHabit(id)
            Alarms.rescheduleAll(getApplication())
            requestSync()
        }
    }

    /** Envoie : uniquement le thème que vous avez écrit. */
    fun suggestHabitsWithAi(focus: String) = runAi { key ->
        aiHabits.value = com.notresemaine.app.ai.Assistant.suggestHabits(key, focus)
    }

    fun clearAiHabits() {
        aiHabits.value = emptyList()
    }

    // ----- Sport -----

    /** Envoie : votre niveau, la fréquence et votre but. Rien de médical. */
    fun buildSportProgramWithAi(level: String, sessionsPerWeek: Int, aim: String) = runAi { key ->
        val program = com.notresemaine.app.ai.Assistant.sportProgram(key, level, sessionsPerWeek, aim)
        if (program.isEmpty()) toast("L'assistant n'a pas su bâtir de programme. Réessaie.")
        aiSport.value = program
    }

    /** Rien n'est posé tant qu'on n'a pas vu le programme et dit oui. */
    fun applySportProgram(weekStart: String) {
        viewModelScope.launch {
            val placed = repo.applySportProgram(myId(), weekStart, aiSport.value)
            aiSport.value = emptyList()
            Alarms.rescheduleAll(getApplication())
            requestSync()
            toast(if (placed > 0) "$placed séance(s) posée(s) ✓" else "Rien à poser.")
        }
    }

    fun clearAiSport() {
        aiSport.value = emptyList()
    }

    // ----- Agenda : ce que l'assistant en dit -----

    fun readAgendaWithAi(facts: String) = runAi { key ->
        aiAgenda.value = com.notresemaine.app.ai.Assistant.agendaNote(key, facts)
    }

    // ----- Poser une question -----

    /**
     * Envoie : votre question et le résumé de VOS données non privées.
     * C'est le seul appel qui assemble un contexte large — le filtrage est fait
     * dans le dépôt, pas ici, pour qu'il n'y ait qu'un seul endroit à vérifier.
     */
    fun askAssistant(question: String) = runAi { key ->
        if (question.isBlank()) return@runAi
        val context = repo.weekContextForAi(myId(), com.notresemaine.app.data.Dates.weekStartIso())
        val answer = com.notresemaine.app.ai.Assistant.ask(key, question, context)
        aiConversation.value = aiConversation.value + Exchange(
            question = question.trim(),
            answer = answer.ifBlank { "Je n'ai pas su répondre à partir de ce que contient l'application." }
        )
    }

    fun clearConversation() {
        aiConversation.value = emptyList()
    }

    // ----- La voix -----

    /**
     * Une phrase dictée est interprétée, puis MONTRÉE — jamais appliquée
     * directement. Se tromper à l'oral est trop facile pour agir sans confirmer.
     */
    fun understandVoice(spoken: String) = runAi { key ->
        val command = com.notresemaine.app.ai.Assistant.understandVoice(key, spoken)
        if (command == null) {
            repo.capture(myId(), spoken)
            requestSync()
            toast("Pas compris — gardé tel quel dans les notes.")
        } else {
            aiVoice.value = command
        }
    }

    fun clearAiVoice() {
        aiVoice.value = null
    }

    /** Exécute la commande dictée, une fois validée à l'écran. */
    fun applyVoice(command: com.notresemaine.app.ai.Assistant.VoiceCommand) {
        viewModelScope.launch {
            when (command.kind) {
                "tache" -> {
                    val message = repo.applyClarifiedCapture(myId(), command.payload, command.whenLabel)
                    if (message.isNotBlank()) toast("« ${command.payload} » · $message")
                }
                "poids" -> {
                    val kilos = command.payload.replace(',', '.').filter { it.isDigit() || it == '.' }
                        .toDoubleOrNull()
                    if (kilos == null) toast("Poids non reconnu.")
                    else {
                        repo.saveWeight(myId(), com.notresemaine.app.data.Dates.todayIso(), kilos)
                        toast("Pesée notée ✓")
                    }
                }
                "habitude" -> {
                    repo.saveHabit(null, myId(), command.payload, command.detail, 8, 21, 2)
                    toast("Habitude ajoutée ✓")
                }
                "menus" -> {
                    // Les menus passent par leur propre chemin : on garde la consigne
                    // et on laisse l'écran des menus faire la proposition complète.
                    aiVoice.value = null
                    suggestMenusWithAi(
                        com.notresemaine.app.data.Dates.weekStartIso(),
                        listOfNotNull(command.payload.ifBlank { null }, command.detail.ifBlank { null })
                            .joinToString(". ")
                    )
                    return@launch
                }
                else -> {
                    val message = repo.capture(myId(), command.payload)
                    if (message.isNotBlank()) toast(message)
                }
            }
            aiVoice.value = null
            requestSync()
        }
    }

    // ----- Poids -----

    fun saveWeight(raw: String) {
        val kilos = raw.replace(',', '.').trim().toDoubleOrNull()
        if (kilos == null || kilos <= 0) {
            toast("Un poids en kilos, par exemple 72,4.")
            return
        }
        viewModelScope.launch {
            repo.saveWeight(myId(), com.notresemaine.app.data.Dates.todayIso(), kilos)
            requestSync()
            toast("Pesée notée ✓")
        }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch {
            repo.deleteWeight(id)
            requestSync()
        }
    }

    fun saveWeightGoal(rawTarget: String, shared: Boolean) {
        viewModelScope.launch {
            val target = rawTarget.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
            repo.settings.setWeightGoal(target, shared)
            requestSync()
            toast(if (shared) "Objectif enregistré · partagé" else "Objectif enregistré · privé")
        }
    }

    /** Récupère la pesée du jour depuis Health Connect si une balance l'y a écrite. */
    fun importWeightFromHealth() {
        viewModelScope.launch {
            val today = java.time.LocalDate.now()
            val kilos = com.notresemaine.app.health.Health.readWeight(getApplication(), today)
            if (kilos == null || kilos <= 0.0) {
                toast("Aucune pesée trouvée dans Health Connect aujourd'hui.")
            } else {
                repo.saveWeight(myId(), today.toString(), kilos)
                requestSync()
                toast("Pesée récupérée ✓")
            }
        }
    }

    /** Envoie : une suite de kilos et l'objectif. Ni date, ni prénom, ni données de l'autre. */
    fun readWeightWithAi() = runAi { key ->
        val s = repo.settings.current()
        val serie = repo.myWeightSeries(s.myUserId)
        aiWeightRead.value = com.notresemaine.app.ai.Assistant.readWeight(key, serie, s.weightTarget)
    }

    // ----- Agenda du téléphone -----

    fun chooseCalendar(id: Long, name: String) {
        viewModelScope.launch {
            val s = repo.settings.current()
            repo.settings.setCalendar(s.calendarEnabled, id, name)
            toast("Agenda « $name » choisi")
        }
    }

    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val s = repo.settings.current()
            repo.settings.setCalendar(enabled, s.calendarId, s.calendarName)
        }
    }

    fun pushDayToCalendar(context: android.content.Context, date: String) {
        viewModelScope.launch { repo.pushDayToCalendar(context, myId(), date) }
    }

    fun pushWeekToCalendar(context: android.content.Context, weekStart: String) {
        viewModelScope.launch {
            val count = repo.pushWeekToCalendar(context, myId(), weekStart)
            toast(
                if (count > 0) "$count créneau" + (if (count > 1) "x" else "") + " posé" +
                    (if (count > 1) "s" else "") + " dans l'agenda ✓"
                else "Aucune tâche n'a d'heure cette semaine."
            )
        }
    }

    fun eraseCalendar(context: android.content.Context) {
        viewModelScope.launch {
            val s = repo.settings.current()
            com.notresemaine.app.calendar.PhoneCalendar.eraseOurs(context, s.calendarId)
            toast("Événements de l'application retirés.")
        }
    }

    // ----- Foyer : couverts et besoin calorique -----

    fun saveHousehold(size: Int, calories: Int) {
        viewModelScope.launch {
            repo.settings.setHousehold(size, calories)
            toast("Enregistré ✓")
        }
    }

    /**
     * MagicOS et One UI tuent volontiers les services en arrière-plan. À chaque
     * retour dans l'application, on remet la surveillance en marche si besoin.
     */
    fun ensureBlockerRunning() {
        viewModelScope.launch {
            if (repo.settings.current().pacteEnabled) {
                BlockerService.startIfEnabled(getApplication(), true)
            }
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

    fun saveMeal(
        date: String, slot: String, title: String, ingredients: String,
        quantities: String = "", calories: Int = 0
    ) {
        viewModelScope.launch {
            repo.saveMeal(myId(), date, slot, title, ingredients, quantities, calories)
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

    /** Menus sur mesure. Envoie : uniquement vos contraintes de repas et le nombre de couverts. */
    fun suggestMenusWithAi(weekStart: String, constraints: String) = runAi { key ->
        val people = repo.settings.current().householdSize
        val suggestions = com.notresemaine.app.ai.Assistant.suggestWeekMenus(key, constraints, people)
        val applied = repo.applyMenuSuggestions(myId(), weekStart, suggestions)
        requestSync()
        toast(if (applied > 0) "$applied repas proposés ✓" else "Rien à ajouter, la semaine est déjà pleine.")
    }

    /**
     * Refait UN repas à la demande : « plus léger », « il me reste du poulet ».
     * Envoie : ce repas et votre consigne, rien d'autre.
     */
    fun reworkMealWithAi(date: String, slot: String, instruction: String) = runAi { key ->
        val s = repo.settings.current()
        val current = repo.db.meals().byId("$date:$slot")
        val suggestion = com.notresemaine.app.ai.Assistant.reworkMeal(
            apiKey = key,
            slot = slot,
            currentTitle = current?.title.orEmpty(),
            currentIngredients = current?.ingredients.orEmpty(),
            instruction = instruction,
            people = s.householdSize,
            targetCalories = s.dailyCalories
        )
        if (suggestion == null) {
            toast("L'assistant n'a pas su proposer autre chose. Reformule ta consigne.")
        } else {
            // Remplacer un repas efface celui qui était prévu : on montre d'abord.
            aiMeal.value = MealProposal(date, slot, current?.title.orEmpty(), suggestion)
        }
    }

    fun acceptMeal() {
        val proposal = aiMeal.value ?: return
        viewModelScope.launch {
            repo.replaceMeal(myId(), proposal.date, proposal.slot, proposal.suggestion)
            aiMeal.value = null
            requestSync()
            toast("Repas remplacé ✓")
        }
    }

    fun clearAiMeal() {
        aiMeal.value = null
    }

    // ----- Ce qu'on a vraiment mangé -----

    /**
     * Ce que l'assistant a lu sur la photo, **avant** tout enregistrement.
     * Comme partout ailleurs : on montre ce qui a été compris, on ne l'écrit pas
     * dans le dos de l'utilisateur.
     */
    data class PhotoMealProposal(
        val date: String,
        val slot: String,
        val plannedTitle: String,
        val reading: com.notresemaine.app.ai.Assistant.PhotoMeal
    )

    val aiPhotoMeal = MutableStateFlow<PhotoMealProposal?>(null)

    /**
     * Envoie la photo à l'assistant, puis l'efface du téléphone.
     *
     * L'effacement a lieu quoi qu'il arrive — réponse, panne de réseau ou refus du
     * modèle. Une photo de repas n'a aucune raison de traîner dans le cache.
     */
    fun analyzeMealPhoto(uri: android.net.Uri, date: String, slot: String, note: String) = runAi { key ->
        val app = getApplication<android.app.Application>()
        val s = repo.settings.current()
        if (!s.mealPhotoEnabled) {
            toast("Activez d'abord « Analyse photo des repas » dans Moi → Assistant.")
            return@runAi
        }
        val encoded = com.notresemaine.app.data.Photo.toBase64(app, uri)
        if (encoded == null) {
            com.notresemaine.app.data.Photo.cleanUp(app)
            toast("Photo illisible. Réessayez.")
            return@runAi
        }
        val reading = try {
            com.notresemaine.app.ai.Assistant.readMealPhoto(
                key, encoded, com.notresemaine.app.data.Photo.MIME, note
            )
        } finally {
            com.notresemaine.app.data.Photo.cleanUp(app)
        }
        if (reading == null) {
            toast("Aucun plat reconnu sur cette photo. Notez-le à la main.")
            return@runAi
        }
        val planned = repo.db.meals().byId("$date:$slot")?.title.orEmpty()
        aiPhotoMeal.value = PhotoMealProposal(date, slot, planned, reading)
    }

    /**
     * Enregistre ce qui a été mangé — corrigé à la main si besoin.
     * Le titre, les aliments et la fourchette arrivent depuis l'écran : ce sont
     * ceux que l'utilisateur a validés, pas forcément ceux du modèle.
     */
    fun logMeal(
        date: String,
        slot: String,
        title: String,
        detail: String,
        caloriesLow: Int,
        caloriesHigh: Int,
        source: String,
        time: String = "",
        protein: Int = 0,
        carbs: Int = 0,
        fat: Int = 0,
        fiber: Int = 0
    ) {
        viewModelScope.launch {
            repo.saveMealLog(
                myId(), date, slot, title, detail, caloriesLow, caloriesHigh, source, time,
                protein, carbs, fat, fiber
            )
            aiPhotoMeal.value = null
            requestSync()
            toast("Repas noté ✓")
        }
    }

    fun clearPhotoMeal() {
        aiPhotoMeal.value = null
    }

    /**
     * L'estimation de calories d'un repas décrit à la main, dès qu'on a fini d'écrire.
     *
     * Volontairement **hors de [runAi]** : cette estimation part toute seule pendant
     * la frappe. Une panne de réseau ferait alors apparaître un bandeau d'erreur à
     * chaque pause au clavier — insupportable pour un confort facultatif. Ici, un
     * échec ne dit rien : le champ reste vide, et on tape le chiffre soi-même.
     */
    val aiMealEstimate = MutableStateFlow<com.notresemaine.app.ai.Assistant.PhotoMeal?>(null)
    val aiMealEstimating = MutableStateFlow(false)
    private var estimateJob: kotlinx.coroutines.Job? = null

    fun estimateMealCalories(description: String) {
        estimateJob?.cancel()
        if (description.trim().length < 3) {
            aiMealEstimate.value = null
            return
        }
        estimateJob = viewModelScope.launch {
            val s = repo.settings.current()
            if (!s.aiEnabled || s.aiApiKey.isBlank()) return@launch
            aiMealEstimating.value = true
            aiMealEstimate.value = runCatching {
                com.notresemaine.app.ai.Assistant.estimateMeal(s.aiApiKey, description)
            }.getOrNull()
            aiMealEstimating.value = false
        }
    }

    fun clearMealEstimate() {
        estimateJob?.cancel()
        aiMealEstimate.value = null
        aiMealEstimating.value = false
    }

    /** « J'ai jeûné ce repas » : une ligne à zéro calorie, assumée — ou son retrait. */
    fun toggleFasted(date: String, slot: String) {
        viewModelScope.launch {
            val added = repo.toggleFasted(myId(), date, slot)
            requestSync()
            toast(if (added) "Repas sauté, noté ✓" else "Jeûne annulé")
        }
    }

    /** Cocher un repas du menu comme réellement mangé — ou le décocher. */
    fun toggleMenuEaten(date: String, slot: String) {
        viewModelScope.launch {
            when (repo.toggleMenuEaten(myId(), date, slot)) {
                null -> toast("Rien de prévu à ce créneau — remplissez d'abord le menu.")
                true -> requestSync()
                false -> requestSync()
            }
        }
    }

    fun deleteMealLog(id: String) {
        viewModelScope.launch {
            repo.deleteMealLog(id)
            requestSync()
        }
    }

    /** Le journal du réel part-il vers l'espace commun ? Éteint par défaut. */
    fun saveMealLogShared(shared: Boolean) {
        viewModelScope.launch {
            repo.settings.setMealLogShared(shared)
            if (shared) requestSync()
            toast(
                if (shared) "Vos repas seront visibles par votre binôme"
                else "Vos repas restent sur ce téléphone"
            )
        }
    }

    /** L'interrupteur qui autorise une image à quitter le téléphone. */
    fun saveMealPhotoSetting(enabled: Boolean) {
        viewModelScope.launch {
            repo.settings.setMealPhoto(enabled)
            toast(if (enabled) "Analyse photo activée ✓" else "Analyse photo désactivée")
        }
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
    /**
     * Demande à l'assistant ce qu'il comprend — et s'arrête là.
     *
     * L'ancienne version appliquait directement : la note disparaissait, une
     * tâche apparaissait ailleurs, et on ne savait ni ce qui avait été compris
     * ni pourquoi ce jour-là. Une proposition qu'on ne voit pas est une décision
     * prise à votre place. Rien n'est enregistré tant que vous n'avez pas validé.
     */
    fun captureWithAi(text: String) = runAi { key ->
        val clarified = com.notresemaine.app.ai.Assistant.clarifyCapture(key, text)
        if (clarified == null) {
            // L'assistant n'a pas compris : la capture hors ligne prend le relais.
            val message = repo.capture(myId(), text)
            if (message.isNotBlank()) toast(message)
            requestSync()
        } else {
            aiCapture.value = CaptureProposal(
                original = text,
                action = clarified.action,
                whenLabel = clarified.whenLabel,
                why = clarified.why
            )
        }
    }

    /** Valide la proposition, éventuellement corrigée à la main. */
    fun acceptCapture(action: String, whenLabel: String) {
        viewModelScope.launch {
            val message = repo.applyClarifiedCapture(myId(), action, whenLabel)
            if (message.isNotBlank()) toast("« $action » · $message")
            aiCapture.value = null
            requestSync()
        }
    }

    /** Refuse la proposition : la note d'origine part telle quelle dans la boîte. */
    fun rejectCapture() {
        val proposal = aiCapture.value ?: return
        viewModelScope.launch {
            val message = repo.capture(myId(), proposal.original)
            if (message.isNotBlank()) toast(message)
            aiCapture.value = null
            requestSync()
        }
    }

    fun clearAiCapture() {
        aiCapture.value = null
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

    fun deleteShoppingItem(itemId: String) {
        viewModelScope.launch { repo.deleteShoppingItem(itemId); requestSync() }
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
            Alarms.rescheduleAll(getApplication())
            toast("Rappels enregistrés ✓")
        }
    }

    /** Les trois rendez-vous fixes pour noter ce qu'on a vraiment mangé. */
    fun saveMealReminders(enabled: Boolean, morning: String, noon: String, evening: String) {
        viewModelScope.launch {
            repo.settings.setMealReminders(enabled, morning, noon, evening)
            Alarms.rescheduleAll(getApplication())
        }
    }

    /** Rappels sonores : alarme plein écran à chaque action, ou notification discrète. */
    fun saveAlerts(enabled: Boolean, sound: Boolean) {
        viewModelScope.launch {
            repo.settings.setAlerts(enabled, sound)
            Alarms.rescheduleAll(getApplication())
            toast(if (enabled) "Rappels sonores activés ✓" else "Rappels sonores désactivés")
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
                onFailure = { syncStatus.value = syncErrorText(it) }
            )
        }
    }

    /**
     * Ce qui a réellement échoué, en une phrase.
     *
     * « Hors ligne — nouvelle tentative plus tard » était affiché pour *toutes*
     * les pannes : adresse fautive, colonne manquante, mot de passe changé. Un
     * message qui recouvre tout n'aide à rien — on ne peut pas corriger ce qu'on
     * ne voit pas.
     */
    private fun syncErrorText(t: Throwable): String = when {
        t is java.net.UnknownHostException ->
            "Adresse introuvable. Vérifiez l'adresse du projet dans « Modifier l'adresse et la clé »."
        t is java.net.SocketTimeoutException ->
            "Le serveur ne répond pas. Réessayez dans un instant."
        t is javax.net.ssl.SSLException ->
            "Connexion sécurisée refusée. Vérifiez la date et l'heure du téléphone."
        t is java.net.ConnectException ->
            "Connexion impossible. Vérifiez le réseau du téléphone."
        // Une colonne absente est le symptôme d'un schéma non mis à jour : c'est
        // la panne la plus fréquente après une livraison, et la plus vite réglée.
        t.message?.contains("column", ignoreCase = true) == true ->
            "Base incomplète : repassez le fichier supabase/schema.sql dans le SQL " +
                "Editor. (${t.message?.take(120)})"
        else -> t.message?.take(160) ?: "Échec de la synchronisation."
    }
}
