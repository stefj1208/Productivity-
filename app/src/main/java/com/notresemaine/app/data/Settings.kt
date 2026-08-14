package com.notresemaine.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val onboarded: Boolean = false,
    val myUserId: String = "",
    val myName: String = "",
    val myColor: String = "A", // "A" = bleu, "B" = orange
    val eveningReminder: String = "21:00",
    val eveningEnabled: Boolean = true,
    val sundayReminder: String = "18:00",
    val sundayEnabled: Boolean = true,
    val themeMode: String = "sombre", // sombre | clair | auto
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val authEmail: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val coupleCode: String = "",
    val lastPullTs: Long = 0L,
    val lastPushTs: Long = 0L,
    // Rituel du matin
    val wakeAlarm: String = "05:00",
    // Pacte d'écran
    val pacteEnabled: Boolean = false,
    val socialApps: String = "",        // noms de paquets séparés par des virgules
    val dailyLimitMinutes: Int = 45,
    val graceUntil: Long = 0L,          // pause accordée par le partenaire (horodatage local)
    // Couvre-feu : plus de réseaux (ou plus rien) entre ces deux heures
    val curfewEnabled: Boolean = false,
    val curfewStart: String = "22:30",
    val curfewEnd: String = "06:30",
    val curfewStrict: Boolean = false,  // strict = toutes les applis, pas seulement les réseaux
    // Assouplissement différé : un relâchement ne prend effet que le lendemain
    val pendingLimitMinutes: Int = 0,
    val pendingCurfewStart: String = "",
    val pendingCurfewEnd: String = "",
    val pendingFromDate: String = "",
    // Assistant (facultatif, clé fournie par l'utilisateur)
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    // Rappels sonores : une alarme plein écran à chaque action à faire
    val alertsEnabled: Boolean = true,
    val alertSound: Boolean = true,
    // Poids : donnée de santé, partagée seulement si on le décide
    val weightTarget: Double = 0.0,
    val weightShared: Boolean = false,
    // Menus : pour qui on cuisine, et le besoin quotidien de chacun
    val householdSize: Int = 2,
    val dailyCalories: Int = 2000,
    // Agenda du téléphone (donc Google Agenda, qui s'y synchronise déjà)
    val calendarEnabled: Boolean = false,
    val calendarId: Long = -1L,
    val calendarName: String = ""
)

class SettingsStore(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val myUserId = stringPreferencesKey("myUserId")
        val myName = stringPreferencesKey("myName")
        val myColor = stringPreferencesKey("myColor")
        val eveningReminder = stringPreferencesKey("eveningReminder")
        val eveningEnabled = booleanPreferencesKey("eveningEnabled")
        val sundayReminder = stringPreferencesKey("sundayReminder")
        val sundayEnabled = booleanPreferencesKey("sundayEnabled")
        val themeMode = stringPreferencesKey("themeMode")
        val supabaseUrl = stringPreferencesKey("supabaseUrl")
        val supabaseKey = stringPreferencesKey("supabaseKey")
        val authEmail = stringPreferencesKey("authEmail")
        val accessToken = stringPreferencesKey("accessToken")
        val refreshToken = stringPreferencesKey("refreshToken")
        val coupleCode = stringPreferencesKey("coupleCode")
        val lastPullTs = longPreferencesKey("lastPullTs")
        val lastPushTs = longPreferencesKey("lastPushTs")
        val wakeAlarm = stringPreferencesKey("wakeAlarm")
        val pacteEnabled = booleanPreferencesKey("pacteEnabled")
        val socialApps = stringPreferencesKey("socialApps")
        val dailyLimitMinutes = intPreferencesKey("dailyLimitMinutes")
        val graceUntil = longPreferencesKey("graceUntil")
        val curfewEnabled = booleanPreferencesKey("curfewEnabled")
        val curfewStart = stringPreferencesKey("curfewStart")
        val curfewEnd = stringPreferencesKey("curfewEnd")
        val curfewStrict = booleanPreferencesKey("curfewStrict")
        val pendingLimitMinutes = intPreferencesKey("pendingLimitMinutes")
        val pendingCurfewStart = stringPreferencesKey("pendingCurfewStart")
        val pendingCurfewEnd = stringPreferencesKey("pendingCurfewEnd")
        val pendingFromDate = stringPreferencesKey("pendingFromDate")
        val aiEnabled = booleanPreferencesKey("aiEnabled")
        val aiApiKey = stringPreferencesKey("aiApiKey")
        val alertsEnabled = booleanPreferencesKey("alertsEnabled")
        val alertSound = booleanPreferencesKey("alertSound")
        val weightTarget = doublePreferencesKey("weightTarget")
        val weightShared = booleanPreferencesKey("weightShared")
        val householdSize = intPreferencesKey("householdSize")
        val dailyCalories = intPreferencesKey("dailyCalories")
        val calendarEnabled = booleanPreferencesKey("calendarEnabled")
        val calendarId = longPreferencesKey("calendarId")
        val calendarName = stringPreferencesKey("calendarName")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboarded = p[K.onboarded] ?: false,
            myUserId = p[K.myUserId] ?: "",
            myName = p[K.myName] ?: "",
            myColor = p[K.myColor] ?: "A",
            eveningReminder = p[K.eveningReminder] ?: "21:00",
            eveningEnabled = p[K.eveningEnabled] ?: true,
            sundayReminder = p[K.sundayReminder] ?: "18:00",
            sundayEnabled = p[K.sundayEnabled] ?: true,
            themeMode = p[K.themeMode] ?: "sombre",
            supabaseUrl = p[K.supabaseUrl] ?: "",
            supabaseKey = p[K.supabaseKey] ?: "",
            authEmail = p[K.authEmail] ?: "",
            accessToken = p[K.accessToken] ?: "",
            refreshToken = p[K.refreshToken] ?: "",
            coupleCode = p[K.coupleCode] ?: "",
            lastPullTs = p[K.lastPullTs] ?: 0L,
            lastPushTs = p[K.lastPushTs] ?: 0L,
            wakeAlarm = p[K.wakeAlarm] ?: "05:00",
            pacteEnabled = p[K.pacteEnabled] ?: false,
            socialApps = p[K.socialApps] ?: "",
            dailyLimitMinutes = p[K.dailyLimitMinutes] ?: 45,
            graceUntil = p[K.graceUntil] ?: 0L,
            curfewEnabled = p[K.curfewEnabled] ?: false,
            curfewStart = p[K.curfewStart] ?: "22:30",
            curfewEnd = p[K.curfewEnd] ?: "06:30",
            curfewStrict = p[K.curfewStrict] ?: false,
            pendingLimitMinutes = p[K.pendingLimitMinutes] ?: 0,
            pendingCurfewStart = p[K.pendingCurfewStart] ?: "",
            pendingCurfewEnd = p[K.pendingCurfewEnd] ?: "",
            pendingFromDate = p[K.pendingFromDate] ?: "",
            aiEnabled = p[K.aiEnabled] ?: false,
            aiApiKey = p[K.aiApiKey] ?: "",
            alertsEnabled = p[K.alertsEnabled] ?: true,
            alertSound = p[K.alertSound] ?: true,
            weightTarget = p[K.weightTarget] ?: 0.0,
            weightShared = p[K.weightShared] ?: false,
            householdSize = p[K.householdSize] ?: 2,
            dailyCalories = p[K.dailyCalories] ?: 2000,
            calendarEnabled = p[K.calendarEnabled] ?: false,
            calendarId = p[K.calendarId] ?: -1L,
            calendarName = p[K.calendarName] ?: ""
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun completeOnboarding(userId: String, name: String, color: String) {
        context.dataStore.edit { p ->
            p[K.onboarded] = true
            p[K.myUserId] = userId
            p[K.myName] = name
            p[K.myColor] = color
        }
    }

    suspend fun setProfile(name: String, color: String) {
        context.dataStore.edit { p ->
            p[K.myName] = name
            p[K.myColor] = color
        }
    }

    suspend fun setReminders(evening: String, eveningOn: Boolean, sunday: String, sundayOn: Boolean) {
        context.dataStore.edit { p ->
            p[K.eveningReminder] = evening
            p[K.eveningEnabled] = eveningOn
            p[K.sundayReminder] = sunday
            p[K.sundayEnabled] = sundayOn
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { p -> p[K.themeMode] = mode }
    }

    /**
     * L'adresse doit être nue : `https://xxxx.supabase.co`.
     * Le tableau de bord affiche souvent le point d'accès REST avec un chemin —
     * on le retire ici plutôt que de laisser l'utilisateur deviner son erreur.
     */
    suspend fun setSupabaseConfig(url: String, key: String) {
        var clean = url.trim().trimEnd('/')
        listOf("/rest/v1", "/auth/v1", "/storage/v1", "/realtime/v1", "/functions/v1").forEach { suffix ->
            if (clean.endsWith(suffix, ignoreCase = true)) {
                clean = clean.dropLast(suffix.length).trimEnd('/')
            }
        }
        context.dataStore.edit { p ->
            p[K.supabaseUrl] = clean
            p[K.supabaseKey] = key.trim()
        }
    }

    suspend fun setSession(email: String, userId: String, access: String, refresh: String) {
        context.dataStore.edit { p ->
            p[K.authEmail] = email
            p[K.myUserId] = userId
            p[K.accessToken] = access
            p[K.refreshToken] = refresh
        }
    }

    suspend fun setTokens(access: String, refresh: String) {
        context.dataStore.edit { p ->
            p[K.accessToken] = access
            p[K.refreshToken] = refresh
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { p ->
            p[K.authEmail] = ""
            p[K.accessToken] = ""
            p[K.refreshToken] = ""
            p[K.coupleCode] = ""
            p[K.lastPullTs] = 0L
            p[K.lastPushTs] = 0L
        }
    }

    suspend fun setCoupleCode(code: String) {
        context.dataStore.edit { p -> p[K.coupleCode] = code }
    }

    suspend fun setWakeAlarm(time: String) {
        context.dataStore.edit { p -> p[K.wakeAlarm] = time }
    }

    suspend fun setPacte(enabled: Boolean, socialApps: String, limitMinutes: Int) {
        context.dataStore.edit { p ->
            p[K.pacteEnabled] = enabled
            p[K.socialApps] = socialApps
            p[K.dailyLimitMinutes] = limitMinutes
        }
    }

    suspend fun setGraceUntil(ts: Long) {
        context.dataStore.edit { p -> p[K.graceUntil] = ts }
    }

    suspend fun setCurfew(enabled: Boolean, start: String, end: String, strict: Boolean) {
        context.dataStore.edit { p ->
            p[K.curfewEnabled] = enabled
            p[K.curfewStart] = start
            p[K.curfewEnd] = end
            p[K.curfewStrict] = strict
        }
    }

    /** Un assouplissement est mis en attente : il ne s'appliquera que demain. */
    suspend fun setPending(limitMinutes: Int, curfewStart: String, curfewEnd: String, fromDate: String) {
        context.dataStore.edit { p ->
            p[K.pendingLimitMinutes] = limitMinutes
            p[K.pendingCurfewStart] = curfewStart
            p[K.pendingCurfewEnd] = curfewEnd
            p[K.pendingFromDate] = fromDate
        }
    }

    suspend fun clearPending() {
        context.dataStore.edit { p ->
            p[K.pendingLimitMinutes] = 0
            p[K.pendingCurfewStart] = ""
            p[K.pendingCurfewEnd] = ""
            p[K.pendingFromDate] = ""
        }
    }

    suspend fun setAi(enabled: Boolean, apiKey: String) {
        context.dataStore.edit { p ->
            p[K.aiEnabled] = enabled
            p[K.aiApiKey] = apiKey.trim()
        }
    }

    suspend fun setAlerts(enabled: Boolean, sound: Boolean) {
        context.dataStore.edit { p ->
            p[K.alertsEnabled] = enabled
            p[K.alertSound] = sound
        }
    }

    suspend fun setWeightGoal(target: Double, shared: Boolean) {
        context.dataStore.edit { p ->
            p[K.weightTarget] = target
            p[K.weightShared] = shared
        }
    }

    suspend fun setHousehold(size: Int, calories: Int) {
        context.dataStore.edit { p ->
            p[K.householdSize] = size.coerceIn(1, 12)
            p[K.dailyCalories] = calories.coerceIn(1200, 4000)
        }
    }

    suspend fun setCalendar(enabled: Boolean, id: Long, name: String) {
        context.dataStore.edit { p ->
            p[K.calendarEnabled] = enabled
            p[K.calendarId] = id
            p[K.calendarName] = name
        }
    }

    suspend fun setSyncMarks(pull: Long, push: Long) {
        context.dataStore.edit { p ->
            p[K.lastPullTs] = pull
            p[K.lastPushTs] = push
        }
    }
}
