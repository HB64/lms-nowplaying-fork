package com.example.lmsnowplaying

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.lmsnowplaying.network.jellyfin.JellyfinApi
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)


        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Blocking on purpose: this is a deliberate app-exit
                    // action, and we need the standby command to actually
                    // reach the player (e.g. a Boom) before the process
                    // dies - a fire-and-forget coroutine could get killed
                    // mid-request by the exitProcess() call right after.
                    runBlocking(Dispatchers.IO) {
                        LMS.playPause(false)
                        LMS.power(false)
                    }
                    this@MainActivity.finish()
                    exitProcess(0)
                }
            }
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // "loading" while checking stored settings, then "setup" or "ready"
            var screenState by remember { mutableStateOf("loading") }

            LaunchedEffect(Unit) {
                val resolved = AppConfig.load(context)
                screenState = if (resolved.lmsUrl.isNotBlank()) "ready" else "setup"
            }

            when (screenState) {
                "setup" -> {
                    SettingsScreen(onSaved = {
                        scope.launch {
                            AppConfig.load(context)
                            screenState = "ready"
                        }
                    })
                }
                "ready" -> {
                    if (NavState.showSettings) {
                        SettingsScreen(
                            initial = AppConfig.current,
                            onSaved = {
                                scope.launch {
                                    AppConfig.load(context)
                                    NavState.showSettings = false
                                }
                            },
                            onCancel = { NavState.showSettings = false }
                        )
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            LMS.getPlayers()
                        }

                        JellyfinApi.SetAPIToken(context)

                        SecondaryScreen()
                    }
                }
            }
        }
    }

}
