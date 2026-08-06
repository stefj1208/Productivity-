package com.notresemaine.app.sync

import com.notresemaine.app.data.DayPlanEntity
import com.notresemaine.app.data.EncouragementEntity
import com.notresemaine.app.data.ProfileEntity
import com.notresemaine.app.data.Repository
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.data.WeekPlanEntity
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
    @SerialName("is_priority") val isPriority: Boolean = false,
    @SerialName("is_sport") val isSport: Boolean = false,
    val done: Boolean = false,
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
                    TaskDto(it.id, it.userId, it.title, it.date, it.weekStart, it.isPriority, it.isSport, it.done, it.deleted, it.updatedAt)
                }, TaskDto.serializer())

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
                    ProfileDto(it.id, it.name, it.color, it.updatedAt)
                }, ProfileDto.serializer())

                val encouragements = db.encouragements().modifiedSince(s.lastPushTs).filter { it.fromUser == myId }
                api.upsert("encouragements", token, encouragements.map {
                    EncouragementDto(it.id, it.fromUser, it.toUser, it.date, it.message, it.deleted, it.updatedAt)
                }, EncouragementDto.serializer())

                val pushMark = (tasks.map { it.updatedAt } + dayPlans.map { it.updatedAt } +
                        weekPlans.map { it.updatedAt } + profiles.map { it.updatedAt } +
                        encouragements.map { it.updatedAt } + s.lastPushTs).max()

                // Réception : tout ce qui a changé dans le couple ; la ligne la plus récente gagne.
                var pullMark = s.lastPullTs

                api.select("tasks", token, s.lastPullTs, TaskDto.serializer()).forEach { dto ->
                    pullMark = maxOf(pullMark, dto.updatedAt)
                    val local = db.tasks().byId(dto.id)
                    if (local == null || dto.updatedAt > local.updatedAt) {
                        db.tasks().upsert(TaskEntity(dto.id, dto.userId, dto.title, dto.date, dto.weekStart, dto.isPriority, dto.isSport, dto.done, dto.deleted, dto.updatedAt))
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
                        db.profiles().upsert(ProfileEntity(dto.id, dto.name, dto.color, dto.updatedAt))
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
