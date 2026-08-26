package com.notresemaine.app.sync

import com.notresemaine.app.data.DayPlanEntity
import com.notresemaine.app.data.EncouragementEntity
import com.notresemaine.app.data.GoalEntity
import com.notresemaine.app.data.GraceRequestEntity
import com.notresemaine.app.data.HabitEntity
import com.notresemaine.app.data.HealthDayEntity
import com.notresemaine.app.data.MealEntity
import com.notresemaine.app.data.ProfileEntity
import com.notresemaine.app.data.ShoppingItemEntity
import com.notresemaine.app.data.Repository
import com.notresemaine.app.data.RitualLogEntity
import com.notresemaine.app.data.HouseItemEntity
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.data.UsageDayEntity
import com.notresemaine.app.data.WeekPlanEntity
import com.notresemaine.app.data.WeightEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---------- Modèles envoyés/reçus (colonnes en snake_case côté Supabase) ----------

@Serializable
data class TaskDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val date: String? = null,
    @SerialName("week_start") val weekStart: String? = null,
    @SerialName("assigned_by") val assignedBy: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("duration_minutes") val durationMinutes: Int = 0,
    @SerialName("is_priority") val isPriority: Boolean = false,
    @SerialName("is_sport") val isSport: Boolean = false,
    @SerialName("goal_id") val goalId: String? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class GoalDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val domain: String,
    @SerialName("sessions_per_week") val sessionsPerWeek: Int,
    @SerialName("minutes_per_session") val minutesPerSession: Int,
    @SerialName("preferred_time") val preferredTime: String,
    @SerialName("preferred_days") val preferredDays: String,
    @SerialName("next_action") val nextAction: String,
    @SerialName("is_private") val isPrivate: Boolean = false,
    val active: Boolean = true,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class HouseItemDto(
    val id: String,
    val section: String,
    val title: String,
    val detail: String = "",
    val amount: Double = 0.0,
    @SerialName("due_date") val dueDate: String? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class RitualLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val minutes: Int,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class UsageDayDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("total_minutes") val totalMinutes: Int,
    @SerialName("social_minutes") val socialMinutes: Int,
    val unlocks: Int,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class GraceRequestDto(
    val id: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    val date: String,
    val minutes: Int,
    val status: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class DayPlanDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("wake_time") val wakeTime: String? = null,
    @SerialName("focus_blocks") val focusBlocks: String? = null,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class WeekPlanDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("week_start") val weekStart: String,
    val priority: String? = null,
    val abandon: String? = null,
    @SerialName("validated_at") val validatedAt: Long? = null,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val color: String,
    @SerialName("pacte_enabled") val pacteEnabled: Boolean = false,
    @SerialName("daily_limit_minutes") val dailyLimitMinutes: Int = 45,
    @SerialName("curfew_enabled") val curfewEnabled: Boolean = false,
    @SerialName("curfew_start") val curfewStart: String = "22:30",
    @SerialName("curfew_end") val curfewEnd: String = "06:30",
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class EncouragementDto(
    val id: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    val date: String,
    val message: String,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class MealDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val slot: String,
    val title: String,
    val ingredients: String,
    val quantities: String = "",
    val calories: Int = 0,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

/** Ce qui a été réellement mangé — distinct du menu prévu, qui est [MealDto]. */
@Serializable
data class MealLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val slot: String,
    val title: String,
    val detail: String = "",
    val calories: Int = 0,
    @SerialName("calories_low") val caloriesLow: Int = 0,
    @SerialName("calories_high") val caloriesHigh: Int = 0,
    val source: String = "manuel",
    val time: String = "",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class HabitDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val source: String = "",
    val enabled: Boolean = true,
    @SerialName("from_hour") val fromHour: Int = 8,
    @SerialName("to_hour") val toHour: Int = 21,
    @SerialName("per_day") val perDay: Int = 2,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

/** Pesée. Ne quitte le téléphone que si son propriétaire a coché le partage. */
@Serializable
data class WeightDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val kilos: Double,
    val note: String = "",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("week_start") val weekStart: String,
    val label: String,
    val aisle: String,
    val checked: Boolean = false,
    val manual: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class HealthDayDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("sleep_minutes") val sleepMinutes: Int = 0,
    val steps: Int = 0,
    @SerialName("exercise_minutes") val exerciseMinutes: Int = 0,
    val source: String = "manuel",
    val deleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
data class AuthUser(val id: String)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val user: AuthUser? = null
)

class SupabaseException(message: String) : IOException(message)

// ---------- Client REST minimal pour Supabase (auth + données) ----------

class SupabaseApi(private val baseUrl: String, private val anonKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = try {
                    val obj = json.parseToJsonElement(body) as? JsonObject
                    (obj?.get("msg") ?: obj?.get("message") ?: obj?.get("error_description"))
                        ?.jsonPrimitive?.content
                } catch (_: Exception) { null }
                throw SupabaseException(msg ?: "Erreur ${resp.code}")
            }
            return body
        }
    }

    fun signUp(email: String, password: String): AuthSession {
        val body = buildJsonObject { put("email", email); put("password", password) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .header("apikey", anonKey)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(AuthSession.serializer(), execute(req))
    }

    fun signIn(email: String, password: String): AuthSession {
        val body = buildJsonObject { put("email", email); put("password", password) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .header("apikey", anonKey)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(AuthSession.serializer(), execute(req))
    }

    fun refresh(refreshToken: String): AuthSession {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", anonKey)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return json.decodeFromString(AuthSession.serializer(), execute(req))
    }

    fun <T> select(table: String, token: String, sinceTs: Long, serializer: kotlinx.serialization.KSerializer<T>): List<T> {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table?select=*&updated_at=gt.$sinceTs")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return json.decodeFromString(ListSerializer(serializer), execute(req))
    }

    fun <T> upsert(table: String, token: String, rows: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        if (rows.isEmpty()) return
        val payload = json.encodeToString(ListSerializer(serializer), rows)
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table?on_conflict=id")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        execute(req)
    }

    fun rpc(name: String, token: String, args: JsonObject): String {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .post(args.toString().toRequestBody(jsonMedia))
            .build()
        return execute(req)
    }
}

// ---------- Orchestration : session, couple, envoi/réception ----------

class SyncManager(private val repo: Repository) {

    private suspend fun api(): SupabaseApi? {
        val s = repo.settings.current()
        if (s.supabaseUrl.isBlank() || s.supabaseKey.isBlank()) return null
        return SupabaseApi(s.supabaseUrl, s.supabaseKey)
    }

    val isConfigured: suspend () -> Boolean = {
        val s = repo.settings.current()
        s.supabaseUrl.isNotBlank() && s.supabaseKey.isNotBlank() && s.refreshToken.isNotBlank()
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val api = api() ?: throw SupabaseException("Renseignez d'abord l'adresse et la clé Supabase.")
            var session = api.signUp(email, password)
            if (session.accessToken.isBlank()) session = api.signIn(email, password)
            adoptSession(email, session)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val api = api() ?: throw SupabaseException("Renseignez d'abord l'adresse et la clé Supabase.")
            adoptSession(email, api.signIn(email, password))
        }
    }

    private suspend fun adoptSession(email: String, session: AuthSession) {
        val userId = session.user?.id
            ?: throw SupabaseException("Connexion incomplète : vérifiez que la confirmation d'e-mail est désactivée dans Supabase.")
        val old = repo.settings.current().myUserId
        repo.migrateUserId(old, userId)
        repo.settings.setSession(email, userId, session.accessToken, session.refreshToken)
        val s = repo.settings.current()
        repo.saveMyProfile(userId, s.myName, s.myColor)
    }

    suspend fun signOut() {
        repo.settings.clearSession()
    }

    /** Exécute [block] avec un jeton valide ; rafraîchit et réessaie une fois si le jeton a expiré. */
    private suspend fun <T> withToken(block: suspend (SupabaseApi, String) -> T): T {
        val api = api() ?: throw SupabaseException("Synchronisation non configurée.")
        val s = repo.settings.current()
        if (s.refreshToken.isBlank()) throw SupabaseException("Non connecté.")
        return try {
            block(api, s.accessToken)
        } catch (e: SupabaseException) {
            val session = api.refresh(s.refreshToken)
            repo.settings.setTokens(session.accessToken, session.refreshToken)
            block(api, session.accessToken)
        }
    }

    suspend fun createCouple(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val code = withToken { api, token ->
                api.rpc("create_couple", token, buildJsonObject { }).trim('"', '\n', ' ')
            }
            repo.settings.setCoupleCode(code)
            code
        }
    }

    suspend fun joinCouple(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withToken { api, token ->
                api.rpc("join_couple", token, buildJsonObject { put("p_code", code.trim().uppercase()) })
            }
            repo.settings.setCoupleCode(code.trim().uppercase())
            Unit
        }
    }

    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured()) return@runCatching
            val s = repo.settings.current()
            val myId = s.myUserId
            val db = repo.db

            withToken { api, token ->
                // Envoi : uniquement mes lignes modifiées depuis le dernier envoi.
                val tasks = db.tasks().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("tasks", token, tasks.map {
                    TaskDto(it.id, it.userId, it.title, it.date, it.weekStart, it.assignedBy, it.startTime, it.durationMinutes, it.isPriority, it.isSport, it.goalId, it.done, it.deleted, it.updatedAt)
                }, TaskDto.serializer())

                val goals = db.goals().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("goals", token, goals.map {
                    GoalDto(it.id, it.userId, it.title, it.domain, it.sessionsPerWeek, it.minutesPerSession,
                        it.preferredTime, it.preferredDays, it.nextAction, it.isPrivate, it.active, it.deleted, it.updatedAt)
                }, GoalDto.serializer())

                val ritualLogs = db.ritual().logsModifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("ritual_logs", token, ritualLogs.map {
                    RitualLogDto(it.id, it.userId, it.date, it.minutes, it.deleted, it.updatedAt)
                }, RitualLogDto.serializer())

                val usageDays = db.usage().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("usage_days", token, usageDays.map {
                    UsageDayDto(it.id, it.userId, it.date, it.totalMinutes, it.socialMinutes, it.unlocks, it.deleted, it.updatedAt)
                }, UsageDayDto.serializer())

                val graces = db.grace().modifiedSince(s.lastPushTs).filter { it.fromUser == myId || it.toUser == myId }
                api.upsert("grace_requests", token, graces.map {
                    GraceRequestDto(it.id, it.fromUser, it.toUser, it.date, it.minutes, it.status, it.deleted, it.updatedAt)
                }, GraceRequestDto.serializer())

                // Menus et courses sont communs au couple : on envoie tout ce qui a changé ici.
                val houseItems = db.houseItems().modifiedSince(s.lastPushTs)
                api.upsert("house_items", token, houseItems.map {
                    HouseItemDto(it.id, it.section, it.title, it.detail, it.amount, it.dueDate, it.done, it.deleted, it.updatedAt)
                }, HouseItemDto.serializer())

                val meals = db.meals().modifiedSince(s.lastPushTs)
                api.upsert("meals", token, meals.map {
                    MealDto(it.id, myId, it.date, it.slot, it.title, it.ingredients,
                        it.quantities, it.calories, it.deleted, it.updatedAt)
                }, MealDto.serializer())

                // Le journal du réel suit le même chemin que le menu : il appartient
                // au couple, mais chaque écran ne montre que ses propres lignes.
                val mealLogs = db.mealLogs().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("meal_logs", token, mealLogs.map {
                    MealLogDto(it.id, it.userId, it.date, it.slot, it.title, it.detail,
                        it.calories, it.caloriesLow, it.caloriesHigh, it.source,
                        it.time, it.deleted, it.updatedAt)
                }, MealLogDto.serializer())

                val habits = db.habits().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("habits", token, habits.map {
                    HabitDto(it.id, it.userId, it.title, it.source, it.enabled,
                        it.fromHour, it.toHour, it.perDay, it.deleted, it.updatedAt)
                }, HabitDto.serializer())

                // Le poids ne part que si son propriétaire a coché le partage.
                val weights = if (s.weightShared) {
                    db.weights().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                } else emptyList()
                if (weights.isNotEmpty()) {
                    api.upsert("weights", token, weights.map {
                        WeightDto(it.id, it.userId, it.date, it.kilos, it.note, it.deleted, it.updatedAt)
                    }, WeightDto.serializer())
                }

                val shopping = db.shopping().modifiedSince(s.lastPushTs)
                api.upsert("shopping_items", token, shopping.map {
                    ShoppingItemDto(it.id, myId, it.weekStart, it.label, it.aisle, it.checked, it.manual, it.deleted, it.updatedAt)
                }, ShoppingItemDto.serializer())

                val healthDays = db.health().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("health_days", token, healthDays.map {
                    HealthDayDto(it.id, it.userId, it.date, it.sleepMinutes, it.steps, it.exerciseMinutes, it.source, it.deleted, it.updatedAt)
                }, HealthDayDto.serializer())

                val dayPlans = db.dayPlans().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("day_plans", token, dayPlans.map {
                    DayPlanDto(it.id, it.userId, it.date, it.wakeTime, it.focusBlocks, it.deleted, it.updatedAt)
                }, DayPlanDto.serializer())

                val weekPlans = db.weekPlans().modifiedSince(s.lastPushTs).filter { it.userId == myId }
                api.upsert("week_plans", token, weekPlans.map {
                    WeekPlanDto(it.id, it.userId, it.weekStart, it.priority, it.abandon, it.validatedAt, it.deleted, it.updatedAt)
                }, WeekPlanDto.serializer())

                val profiles = db.profiles().modifiedSince(s.lastPushTs).filter { it.id == myId }
                api.upsert("profiles", token, profiles.map {
                    ProfileDto(it.id, it.name, it.color, it.pacteEnabled, it.dailyLimitMinutes,
                        it.curfewEnabled, it.curfewStart, it.curfewEnd, it.updatedAt)
                }, ProfileDto.serializer())

                val encouragements = db.encouragements().modifiedSince(s.lastPushTs).filter { it.fromUser == myId }
                api.upsert("encouragements", token, encouragements.map {
                    EncouragementDto(it.id, it.fromUser, it.toUser, it.date, it.message, it.deleted, it.updatedAt)
                }, EncouragementDto.serializer())

                val pushMark = (tasks.map { it.updatedAt } + dayPlans.map { it.updatedAt } +
                        weekPlans.map { it.updatedAt } + profiles.map { it.updatedAt } +
                        encouragements.map { it.updatedAt } + goals.map { it.updatedAt } +
                        ritualLogs.map { it.updatedAt } + usageDays.map { it.updatedAt } +
                        graces.map { it.updatedAt } + meals.map { it.updatedAt } +
                        mealLogs.map { it.updatedAt } +
                        shopping.map { it.updatedAt } + healthDays.map { it.updatedAt } +
                        houseItems.map { it.updatedAt } + weights.map { it.updatedAt } +
                        habits.map { it.updatedAt } +
                        s.lastPushTs).max()

                // Réception : tout ce qui a changé dans le couple ; la ligne la plus récente gagne.
                var pullMark = s.lastPullTs

                api.select("tasks", token, s.lastPullTs, TaskDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.tasks().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.tasks().upsert(TaskEntity(dto.id, dto.userId, dto.title, dto.date, dto.weekStart, dto.isPriority, dto.isSport, dto.goalId, dto.done, dto.assignedBy, dto.startTime, dto.durationMinutes, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("goals", token, s.lastPullTs, GoalDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.goals().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.goals().upsert(GoalEntity(dto.id, dto.userId, dto.title, dto.domain, dto.sessionsPerWeek,
                            dto.minutesPerSession, dto.preferredTime, dto.preferredDays, dto.nextAction,
                            dto.isPrivate, dto.active, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("ritual_logs", token, s.lastPullTs, RitualLogDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.ritual().logById(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.ritual().upsertLog(RitualLogEntity(dto.id, dto.userId, dto.date, dto.minutes, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("usage_days", token, s.lastPullTs, UsageDayDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.usage().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.usage().upsert(UsageDayEntity(dto.id, dto.userId, dto.date, dto.totalMinutes, dto.socialMinutes, dto.unlocks, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("grace_requests", token, s.lastPullTs, GraceRequestDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.grace().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.grace().upsert(GraceRequestEntity(dto.id, dto.fromUser, dto.toUser, dto.date, dto.minutes, dto.status, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("house_items", token, s.lastPullTs, HouseItemDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.houseItems().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.houseItems().upsert(HouseItemEntity(dto.id, dto.section, dto.title, dto.detail,
                            dto.amount, dto.dueDate, dto.done, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("meals", token, s.lastPullTs, MealDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.meals().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.meals().upsert(MealEntity(dto.id, dto.userId, dto.date, dto.slot,
                            dto.title, dto.ingredients, dto.quantities, dto.calories,
                            dto.deleted, dto.updatedAt))
                    }
                }
                api.select("meal_logs", token, s.lastPullTs, MealLogDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.mealLogs().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.mealLogs().upsert(
                            com.notresemaine.app.data.MealLogEntity(
                                dto.id, dto.userId, dto.date, dto.slot, dto.title, dto.detail,
                                dto.calories, dto.caloriesLow, dto.caloriesHigh, dto.source,
                                dto.time, dto.deleted, dto.updatedAt
                            )
                        )
                    }
                }
                api.select("habits", token, s.lastPullTs, HabitDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.habits().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.habits().upsert(HabitEntity(dto.id, dto.userId, dto.title, dto.source,
                            dto.enabled, dto.fromHour, dto.toHour, dto.perDay, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("weights", token, s.lastPullTs, WeightDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.weights().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.weights().upsert(WeightEntity(dto.id, dto.userId, dto.date, dto.kilos,
                            dto.note, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("shopping_items", token, s.lastPullTs, ShoppingItemDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.shopping().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.shopping().upsert(ShoppingItemEntity(dto.id, dto.userId, dto.weekStart, dto.label, dto.aisle, dto.checked, dto.manual, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("health_days", token, s.lastPullTs, HealthDayDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.health().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.health().upsert(HealthDayEntity(dto.id, dto.userId, dto.date, dto.sleepMinutes, dto.steps, dto.exerciseMinutes, dto.source, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("day_plans", token, s.lastPullTs, DayPlanDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.dayPlans().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.dayPlans().upsert(DayPlanEntity(dto.id, dto.userId, dto.date, dto.wakeTime, dto.focusBlocks, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("week_plans", token, s.lastPullTs, WeekPlanDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.weekPlans().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.weekPlans().upsert(WeekPlanEntity(dto.id, dto.userId, dto.weekStart, dto.priority, dto.abandon, dto.validatedAt, dto.deleted, dto.updatedAt))
                    }
                }
                api.select("profiles", token, s.lastPullTs, ProfileDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.profiles().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.profiles().upsert(ProfileEntity(dto.id, dto.name, dto.color, dto.pacteEnabled,
                            dto.dailyLimitMinutes, dto.curfewEnabled, dto.curfewStart, dto.curfewEnd, dto.updatedAt))
                    }
                }
                api.select("encouragements", token, s.lastPullTs, EncouragementDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.encouragements().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.encouragements().upsert(EncouragementEntity(dto.id, dto.fromUser, dto.toUser, dto.date, dto.message, dto.deleted, dto.updatedAt))
                    }
                }

                repo.settings.setSyncMarks(pullMark, pushMark)
            }
            Unit
        }
    }
}
