package com.example.lmsnowplaying.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val DEFAULT_PLAYER_NAME = stringPreferencesKey("default_player_name")
    private val DEFAULT_PLAYER_MAC = stringPreferencesKey("default_player_mac")
    private val BACKGROUND_STYLE = stringPreferencesKey("background_style")
    private val STANDBY_ON_EXIT = booleanPreferencesKey("standby_on_exit")

    // Valid values for Settings.backgroundStyle.
    const val BACKGROUND_STYLE_ARTIST = "artist"
    const val BACKGROUND_STYLE_ALBUM = "album"
    const val BACKGROUND_STYLE_STARFIELD = "starfield"
    const val BACKGROUND_STYLE_ARTIST_GRAYSCALE = "artist_grayscale"

    data class Settings(
        val lmsUrl: String = "",
        val lmsUsername: String = "",
        val lmsPassword: String = "",
        val jellyfinUrl: String = "",
        val jellyfinUsername: String = "",
        val jellyfinPassword: String = "",
        val jellyfinApiKey: String = "",
        // Remembers the last-selected player so it's auto-selected again on
        // the next app start, instead of requiring a manual pick every time
        // (useful when the app is launched fresh via e.g. a Harmony activity).
        val defaultPlayerName: String = "",
        val defaultPlayerMac: String = "",
        // Whether the Now Playing backdrop should try an artist photo
        // (BACKGROUND_STYLE_ARTIST, the default) or always just use the
        // blurred album cover (BACKGROUND_STYLE_ALBUM) - some artist photos
        // are ugly or, for multi-artist tracks, end up being the album cover
        // anyway (Lyrion/Jellyfin fall back to that when no real photo is
        // found), which can get repetitive.
        val backgroundStyle: String = BACKGROUND_STYLE_ARTIST,
        // Whether the active player should be put into standby (paused +
        // powered off) when the app closes or is left. Defaults to on,
        // matching the app's original behavior.
        val standbyOnExit: Boolean = true,
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
                defaultPlayerName = prefs[DEFAULT_PLAYER_NAME] ?: "",
                defaultPlayerMac = prefs[DEFAULT_PLAYER_MAC] ?: "",
                backgroundStyle = prefs[BACKGROUND_STYLE] ?: BACKGROUND_STYLE_ARTIST,
                standbyOnExit = prefs[STANDBY_ON_EXIT] ?: true,
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
            prefs[DEFAULT_PLAYER_NAME] = settings.defaultPlayerName
            prefs[DEFAULT_PLAYER_MAC] = settings.defaultPlayerMac
            prefs[BACKGROUND_STYLE] = settings.backgroundStyle
            prefs[STANDBY_ON_EXIT] = settings.standbyOnExit
        }
    }

    // Updates just the remembered default player, without touching (or
    // needing to know) the rest of the settings.
    suspend fun saveDefaultPlayer(context: Context, name: String, mac: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_PLAYER_NAME] = name
            prefs[DEFAULT_PLAYER_MAC] = mac
        }
    }

    // One-time "this app works best with SugarCube + Don't Stop The Music"
    // tip, shown once on first launch and then never again.
    private val PLUGIN_TIP_SHOWN = booleanPreferencesKey("plugin_tip_shown")

    suspend fun hasShownPluginTip(context: Context): Boolean =
        context.dataStore.data.map { it[PLUGIN_TIP_SHOWN] ?: false }.first()

    suspend fun markPluginTipShown(context: Context) {
        context.dataStore.edit { prefs -> prefs[PLUGIN_TIP_SHOWN] = true }
    }
}
