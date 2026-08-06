package com.notresemaine.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val graceUntil: Long = 0L           // pause accordée par le partenaire (horodatage local)
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
            graceUntil = p[K.graceUntil] ?: 0L
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

    suspend fun setSupabaseConfig(url: String, key: String) {
        context.dataStore.edit { p ->
            p[K.supabaseUrl] = url.trim().trimEnd('/')
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

    suspend fun setSyncMarks(pull: Long, push: Long) {
        context.dataStore.edit { p ->
            p[K.lastPullTs] = pull
            p[K.lastPushTs] = push
        }
    }
}
