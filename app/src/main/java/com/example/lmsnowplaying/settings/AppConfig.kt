package com.example.lmsnowplaying.settings

import android.content.Context
import com.example.lmsnowplaying.BuildConfig

/**
 * Effective, resolved configuration used throughout the app. Prefers the
 * runtime settings entered in SettingsScreen; falls back to the old
 * compile-time BuildConfig/local.properties values so existing builds keep
 * working unchanged. Must be populated once via load() before RetrofitClient
 * or JellyfinApi are touched (MainActivity does this before showing the
 * "ready" screen).
 */
object AppConfig {

    var current: SettingsRepository.Settings = SettingsRepository.Settings(
        lmsUrl = BuildConfig.LMS_URL ?: "",
        lmsUsername = BuildConfig.LMS_USERNAME ?: "",
        lmsPassword = BuildConfig.LMS_PASSWORD ?: "",
        jellyfinUrl = BuildConfig.JELLYFIN_URL ?: "",
        jellyfinUsername = BuildConfig.JELLYFIN_USERNAME ?: "",
        jellyfinPassword = BuildConfig.JELLYFIN_PASSWORD ?: "",
        jellyfinApiKey = BuildConfig.JELLYFIN_API_KEY ?: "",
    )
        private set

    suspend fun load(context: Context): SettingsRepository.Settings {
        val stored = SettingsRepository.get(context)
        current = SettingsRepository.Settings(
            lmsUrl = stored.lmsUrl.ifBlank { BuildConfig.LMS_URL ?: "" },
            lmsUsername = stored.lmsUsername.ifBlank { BuildConfig.LMS_USERNAME ?: "" },
            lmsPassword = stored.lmsPassword.ifBlank { BuildConfig.LMS_PASSWORD ?: "" },
            jellyfinUrl = stored.jellyfinUrl.ifBlank { BuildConfig.JELLYFIN_URL ?: "" },
            jellyfinUsername = stored.jellyfinUsername.ifBlank { BuildConfig.JELLYFIN_USERNAME ?: "" },
            jellyfinPassword = stored.jellyfinPassword.ifBlank { BuildConfig.JELLYFIN_PASSWORD ?: "" },
            jellyfinApiKey = stored.jellyfinApiKey.ifBlank { BuildConfig.JELLYFIN_API_KEY ?: "" },
        )
        return current
    }
}
