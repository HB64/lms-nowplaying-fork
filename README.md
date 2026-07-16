# Logitech Media Server Now Playing
Android TV app to show the current playing song from your Logitech Media Server instance.

If you have a Jellyfin instance, you can provide the credentials to grab the backdrop image. Otherwise it defaults to showing the album art

| ![space-1.jpg](https://github.com/TroyFernandes/LMS-NowPlaying/blob/main/sample-images/nowplaying-wjellyfin.png?raw=true) | 
|:--:| 
| *w/ Jellyfin* |

| ![space-1.jpg](https://github.com/TroyFernandes/LMS-NowPlaying/blob/main/sample-images/nowplaying-nojellyfin.png?raw=true) | 
|:--:| 
| *w/o Jellyfin* |

# Building the Source
*Project currently builds with Kotlin 2.2.21, AGP 8.1.2, and Jellyfin SDK 1.8.11 (see `gradle/libs.versions.toml`).*

1. Clone the repo and open it in Android Studio.
2. Copy the contents of `sample.local.properties` into a `local.properties` file in the project root (Android Studio creates this file automatically the first time you open the project — just append the settings below to it).
3. Fill in your own values:
   - `LMS_URL` / `LMS_USERNAME` / `LMS_PASSWORD` — required. Point this at your Lyrion Music Server (formerly Logitech Media Server / LMS). If your server has no auth configured, still set both to `""""` (four double quotes — empty credentials must be quoted like this or the build fails).
   - `JELLYFIN_URL` / `JELLYFIN_API_KEY` / `JELLYFIN_USERNAME` / `JELLYFIN_PASSWORD` — optional. Used as a fallback to fetch an artist backdrop image when Lyrion has no portrait for the artist. Leave `JELLYFIN_API_KEY` empty (`""""`) to disable Jellyfin entirely — the app then just shows a blurred album cover as the background.
4. Restart Android Studio (or "Sync Project with Gradle Files") so the new `local.properties` values are picked up by the secrets-gradle-plugin.
5. Change the build variant to `release` (or use `debug` while developing).
6. In Android Studio: Build → Build Bundle(s) / APK → Build APK.
7. Install the APK on your Android TV device (e.g. Nvidia Shield) via `adb install` or by running it directly from Android Studio with the device selected.

## Jellyfin compatibility — important

The Jellyfin SDK version pinned in `gradle/libs.versions.toml` (`jellyfin-sdk`) must roughly match your Jellyfin **server** version, or authentication and artist-image lookups will fail or crash at runtime. Check your server version in Jellyfin under *Dashboard → General*, then pick a matching SDK release from the [jellyfin-sdk-kotlin releases page](https://github.com/jellyfin/jellyfin-sdk-kotlin/releases) — each release lists its "Recommended API Version". As of this writing, SDK `1.8.11` targets Jellyfin server `10.11.x`.

If you bump the SDK version and hit build or runtime errors, these are the two we've already run into:
- **Runtime crash: `NoClassDefFoundError: org.slf4j.LoggerFactory`** — the SDK logs via `kotlin-logging`, which needs an SLF4J binding on the classpath that isn't pulled in automatically. Fixed by adding `implementation(libs.slf4j.nop)` (a no-op logger) to `app/build.gradle.kts`. Already present in this repo, but worth knowing about if you fork/reset.
- **Compile error: `Unresolved reference 'get'` on `SearchApi`** — newer SDK versions renamed `SearchApi.get(...)` to `SearchApi.getSearchHints(...)` (same parameters). Already updated in `JellyfinApi.kt`.
- Bumping the Jellyfin SDK also generally requires a recent Kotlin version (2.2.x+) and the `org.jetbrains.kotlin.plugin.compose` Gradle plugin instead of the old `composeOptions.kotlinCompilerExtensionVersion` setup — both already configured in this repo.

## Known limitation

All server URLs and credentials are compiled into the APK at build time via `BuildConfig` (sourced from `local.properties`). There is currently no in-app settings screen — changing servers means editing `local.properties` and rebuilding. Making this runtime-configurable (in-app settings, stored via DataStore) is a planned but not yet implemented improvement.
