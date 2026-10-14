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

    // Set once by MainActivity right before showing the player screen: true
    // when a default player was already auto-selected, so initial D-pad
    // focus should land on the play button instead of the options (⋮) menu
    // - there's no need to open the menu when a player is already picked.
    var focusPlayButtonOnStart by mutableStateOf(false)

    // True while the ⋮ options menu is open, so the idle "return focus to
    // the play button" timer never pulls focus out from under it.
    var menuOpen by mutableStateOf(false)

    // True while the library browse screen (opened from the top-left button
    // on Now Playing) is showing.
    var showBrowse by mutableStateOf(false)

    // True after a long stretch without remote input: the focus ring is
    // hidden (focus itself stays put) until the next key press.
    var focusRingHidden by mutableStateOf(false)
}
