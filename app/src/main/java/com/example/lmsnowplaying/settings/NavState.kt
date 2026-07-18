package com.example.lmsnowplaying.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tiny app-wide navigation flag so the player dropdown (deep in the
 * composable tree) can ask MainActivity to show the Settings screen again,
 * without needing a NavHost for a single extra screen.
 */
object NavState {
    var showSettings by mutableStateOf(false)
}
