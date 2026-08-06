package com.notresemaine.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val lastPushTs: Long = 0L
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
            lastPushTs = p[K.lastPushTs] ?: 0L
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

    suspend fun setSyncMarks(pull: Long, push: Long) {
        context.dataStore.edit { p ->
            p[K.lastPullTs] = pull
            p[K.lastPushTs] = push
        }
    }
}
