package com.notresemaine.app.data

import android.content.Context
import java.util.UUID

/** Résultat d'un ajout de tâche : refusé si la limite du jour est atteinte. */
sealed class AddResult {
    data object Ok : AddResult()
    data object DayFull : AddResult()
}

class Repository private constructor(context: Context) {

    val db = AppDb.get(context)
    val settings = SettingsStore(context)

    companion object {
        const val MAX_TASKS_PER_DAY = 3

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }

    private fun now() = System.currentTimeMillis()

    // ----- Tâches -----

    suspend fun addTask(
        userId: String,
        title: String,
        date: String?,
        weekStart: String?,
        isSport: Boolean = false
    ): AddResult {
        if (!isSport && date != null && db.tasks().countForDay(userId, date) >= MAX_TASKS_PER_DAY) {
            return AddResult.DayFull
        }
        db.tasks().upsert(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = title.trim(),
                date = date,
                weekStart = weekStart ?: date?.let { Dates.weekStartIso(java.time.LocalDate.parse(it)) },
                isSport = isSport,
                updatedAt = now()
            )
        )
        return AddResult.Ok
    }

    suspend fun toggleDone(taskId: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(done = !t.done, updatedAt = now()))
    }

    suspend fun deleteTask(taskId: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(deleted = true, updatedAt = now()))
    }

    suspend fun assignTaskToDay(taskId: String, date: String?) {
        val t = db.tasks().byId(taskId) ?: return
        if (date != null && !t.isSport && db.tasks().countForDay(t.userId, date) >= MAX_TASKS_PER_DAY) return
        val ws = date?.let { Dates.weekStartIso(java.time.LocalDate.parse(it)) } ?: t.weekStart
        db.tasks().upsert(t.copy(date = date, weekStart = ws, updatedAt = now()))
    }

    suspend fun moveTaskToWeek(taskId: String, weekStart: String) {
        val t = db.tasks().byId(taskId) ?: return
        db.tasks().upsert(t.copy(date = null, weekStart = weekStart, done = false, updatedAt = now()))
    }

    /** Définit LA priorité d'un jour (remplace l'existante s'il y en a une). */
    suspend fun setDayPriority(userId: String, date: String, title: String) {
        val existing = db.tasks().priorityOfDay(userId, date)
        if (existing != null) {
            db.tasks().upsert(existing.copy(title = title.trim(), updatedAt = now()))
        } else {
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title.trim(),
                    date = date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(date)),
                    isPriority = true,
                    updatedAt = now()
                )
            )
        }
    }

    /**
     * Aligne les tâches secondaires d'un jour sur la liste saisie :
     * les titres retirés sont supprimés, les nouveaux ajoutés (pas de doublon).
     */
    suspend fun reconcileSecondary(userId: String, date: String, titles: List<String>) {
        val wanted = titles.map { it.trim() }.filter { it.isNotBlank() }
        val current = db.tasks().byDateOnce(userId, date).filter { !it.isPriority && !it.isSport }
        current.filter { it.title !in wanted }.forEach {
            db.tasks().upsert(it.copy(deleted = true, updatedAt = now()))
        }
        val existingTitles = current.map { it.title }
        wanted.filter { it !in existingTitles }.forEach { title ->
            db.tasks().upsert(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    date = date,
                    weekStart = Dates.weekStartIso(java.time.LocalDate.parse(date)),
                    updatedAt = now()
                )
            )
        }
    }

    // ----- Plan du jour (réveil, blocs de concentration) -----

    suspend fun saveDayPlan(userId: String, date: String, wakeTime: String?, focusBlocks: String?) {
        db.dayPlans().upsert(
            DayPlanEntity(
                id = "$userId:$date",
                userId = userId,
                date = date,
                wakeTime = wakeTime?.ifBlank { null },
                focusBlocks = focusBlocks?.ifBlank { null },
                updatedAt = now()
            )
        )
    }

    // ----- Plan de la semaine -----

    suspend fun saveWeekPlan(userId: String, weekStart: String, priority: String?, abandon: String?, validate: Boolean) {
        val existing = db.weekPlans().byWeekOnce(userId, weekStart)
        db.weekPlans().upsert(
            WeekPlanEntity(
                id = "$userId:$weekStart",
                userId = userId,
                weekStart = weekStart,
                priority = (priority ?: existing?.priority)?.ifBlank { null },
                abandon = (abandon ?: existing?.abandon)?.ifBlank { null },
                validatedAt = if (validate) now() else existing?.validatedAt,
                updatedAt = now()
            )
        )
    }

    // ----- Profils -----

    suspend fun saveMyProfile(userId: String, name: String, color: String) {
        db.profiles().upsert(ProfileEntity(id = userId, name = name.trim(), color = color, updatedAt = now()))
    }

    // ----- Encouragements -----

    suspend fun sendBravo(fromUser: String, toUser: String, message: String) {
        db.encouragements().upsert(
            EncouragementEntity(
                id = UUID.randomUUID().toString(),
                fromUser = fromUser,
                toUser = toUser,
                date = Dates.todayIso(),
                message = message,
                updatedAt = now()
            )
        )
    }

    /** Quand on se connecte à la synchro, l'identifiant local devient l'identifiant du compte. */
    suspend fun migrateUserId(oldId: String, newId: String) {
        if (oldId == newId || oldId.isBlank()) return
        val t = now()
        db.tasks().migrateUser(oldId, newId, t)
        db.dayPlans().migrateUser(oldId, newId, t)
        db.weekPlans().migrateUser(oldId, newId, t)
        val profile = db.profiles().byId(oldId)
        if (profile != null) {
            db.profiles().delete(oldId)
            db.profiles().upsert(profile.copy(id = newId, updatedAt = t))
        }
    }
}
