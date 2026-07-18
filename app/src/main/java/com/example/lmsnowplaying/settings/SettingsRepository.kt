package com.example.lmsnowplaying.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lyrion_settings")

/**
 * Runtime-configurable settings, replacing the old compile-time BuildConfig values
 * (local.properties). Lets a plain APK be configured from within the app instead
 * of requiring a rebuild via Android Studio.
 */
object SettingsRepository {

    private val LMS_URL = stringPreferencesKey("lms_url")
    private val LMS_USERNAME = stringPreferencesKey("lms_username")
    private val LMS_PASSWORD = stringPreferencesKey("lms_password")
    private val JELLYFIN_URL = stringPreferencesKey("jellyfin_url")
    private val JELLYFIN_USERNAME = stringPreferencesKey("jellyfin_username")
    private val JELLYFIN_PASSWORD = stringPreferencesKey("jellyfin_password")
    private val JELLYFIN_API_KEY = stringPreferencesKey("jellyfin_api_key")

    data class Settings(
        val lmsUrl: String = "",
        val lmsUsername: String = "",
        val lmsPassword: String = "",
        val jellyfinUrl: String = "",
        val jellyfinUsername: String = "",
        val jellyfinPassword: String = "",
        val jellyfinApiKey: String = "",
    )

    fun flow(context: Context): Flow<Settings> =
        context.dataStore.data.map { prefs ->
            Settings(
                lmsUrl = prefs[LMS_URL] ?: "",
                lmsUsername = prefs[LMS_USERNAME] ?: "",
                lmsPassword = prefs[LMS_PASSWORD] ?: "",
                jellyfinUrl = prefs[JELLYFIN_URL] ?: "",
                jellyfinUsername = prefs[JELLYFIN_USERNAME] ?: "",
                jellyfinPassword = prefs[JELLYFIN_PASSWORD] ?: "",
                jellyfinApiKey = prefs[JELLYFIN_API_KEY] ?: "",
            )
        }

    suspend fun get(context: Context): Settings = flow(context).first()

    suspend fun isConfigured(context: Context): Boolean = get(context).lmsUrl.isNotBlank()

    suspend fun save(context: Context, settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[LMS_URL] = settings.lmsUrl
            prefs[LMS_USERNAME] = settings.lmsUsername
            prefs[LMS_PASSWORD] = settings.lmsPassword
            prefs[JELLYFIN_URL] = settings.jellyfinUrl
            prefs[JELLYFIN_USERNAME] = settings.jellyfinUsername
            prefs[JELLYFIN_PASSWORD] = settings.jellyfinPassword
            prefs[JELLYFIN_API_KEY] = settings.jellyfinApiKey
        }
    }
}
