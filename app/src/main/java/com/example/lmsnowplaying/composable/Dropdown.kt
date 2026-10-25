package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.NavState
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Preview
@Composable
fun MyUI() {

    val data = remember { mutableStateListOf<Pair<String, String>>() }
    data.clear()
    data.addAll(LMS.players)


    val contextForToast = LocalContext.current.applicationContext

    // state of the menu
    var expanded by remember {
        mutableStateOf(false)
    }
    val menuButtonFocusRequester = remember { FocusRequester() }
    var wasExpanded by remember { mutableStateOf(false) }
    var menuButtonHasFocus by remember { mutableStateOf(false) }
    // NavState.menuClosedAt: when the menu closed last. The Enter press that
    // closed it must not also "click" the ⋮ button that gets focus back and
    // reopen the menu.
    LaunchedEffect(expanded) {
        NavState.menuOpen = expanded
        if (wasExpanded && !expanded) NavState.menuClosedAt = System.currentTimeMillis()
        // Menu just closed: put focus back on the ⋮ button instead of
        // leaving it nowhere (it stays there until the idle timeout or
        // until the user moves to another button).
        // The focus the removed menu leaves behind can be handed to another
        // element a moment later, so ask a few times until the button has it.
        if (wasExpanded && !expanded && !NavState.showSettings) {
            repeat(8) {
                if (!menuButtonHasFocus) {
                    try { menuButtonFocusRequester.requestFocus() } catch (_: Exception) { }
                }
                kotlinx.coroutines.delay(100)
            }
        }
        wasExpanded = expanded
    }

    Box(
        contentAlignment = Alignment.TopEnd
    ) {
        // 3 vertical dots icon

        // Give this button focus as soon as the screen appears, so the
        // remote is immediately ready to open the settings/options menu
        // without needing to navigate there first - unless a default
        // player was already auto-selected, in which case the play button
        // takes initial focus instead (see MainActivity/NavState).
        LaunchedEffect(Unit) {
            // This button was (re)created right after the menu closed - the
            // screen swaps layout when the artwork changes, which throws the
            // old button away together with its focus. Take focus back.
            if (System.currentTimeMillis() - NavState.menuClosedAt < 3000 && !NavState.showSettings) {
                repeat(10) {
                    if (!menuButtonHasFocus) {
                        try { menuButtonFocusRequester.requestFocus() } catch (_: Exception) { }
                    }
                    kotlinx.coroutines.delay(100)
                }
                return@LaunchedEffect
            }
            if (!NavState.focusPlayButtonOnStart && !NavState.startFocusDone) {
                NavState.startFocusDone = true
                menuButtonFocusRequester.requestFocus()
            }
        }

        FocusIconButton(
            onClick = {
                if (System.currentTimeMillis() - NavState.menuClosedAt > 600) expanded = true
            },
            size = 30.dp,
            modifier = Modifier
                .padding(top = 2.dp)
                .focusRequester(menuButtonFocusRequester)
                .onFocusChanged { menuButtonHasFocus = it.isFocused }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Open Options",
                tint = Color.LightGray,
                modifier = Modifier.size(15.dp)

            )
        }

        // Menu rendered directly in the same composition/window (not a
        // separate Popup), with D-pad navigation handled manually instead of
        // relying on Compose's ambient focus system - neither the Popup-based
        // DropdownMenu nor plain focusable() reliably showed a focus
        // highlight on this TV setup, so we track/drive it ourselves,
        // mirroring how the media-key handling already works reliably.
        if (expanded) {
            // Start on the player that is currently selected
            var highlightedIndex by remember {
                mutableStateOf(data.indexOfFirst { it.second == LMS.playerMac }.coerceAtLeast(0))
            }
            var menuHasFocus by remember { mutableStateOf(false) }
            // +1 for the background-style row, +1 for the standby-on-exit
            // row, +1 for the "Settings" entry.
            val totalItems = data.size + 3
            val backgroundRowIndex = data.size
            val standbyRowIndex = data.size + 1
            val settingsRowIndex = data.size + 2
            val menuFocusRequester = remember { FocusRequester() }

            // Which player (by mac) is remembered as the default, shown as a
            // checkbox next to each row. Toggled with left/right so it
            // doesn't collide with up/down navigation or the connect action.
            var defaultMac by remember { mutableStateOf(AppConfig.current.defaultPlayerMac) }

            // Quick toggle for the Now Playing backdrop style, so switching
            // it doesn't require going all the way into the full Settings
            // screen (which is a real chore to navigate with a D-pad).
            var backgroundStyle by remember { mutableStateOf(AppConfig.current.backgroundStyle) }

            // Quick toggle for whether the active player is put into standby
            // when the app closes - same reasoning as the background-style
            // toggle above.
            var standbyOnExit by remember { mutableStateOf(AppConfig.current.standbyOnExit) }

            // The first requestFocus can happen before the menu is ready for
            // it, which left the menu open without key focus (D-pad did
            // nothing). Keep asking until the menu really has focus.
            LaunchedEffect(Unit) {
                while (true) {
                    if (!menuHasFocus) {
                        try { menuFocusRequester.requestFocus() } catch (_: Exception) { }
                    }
                    kotlinx.coroutines.delay(150)
                }
            }

            Column(
                modifier = Modifier
                    // The menu floats over the screen: it must not add its
                    // height to this Box, or the whole layout shifts down
                    // while the menu is open.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        layout(placeable.width, 0) { placeable.place(0, 0) }
                    }
                    .offset(y = 28.dp)
                    .width(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE6222222))
                    .padding(vertical = 8.dp)
                    .focusRequester(menuFocusRequester)
                    .onFocusChanged { menuHasFocus = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                highlightedIndex = (highlightedIndex + 1) % totalItems
                                true
                            }
                            Key.DirectionUp -> {
                                highlightedIndex = (highlightedIndex - 1 + totalItems) % totalItems
                                true
                            }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (highlightedIndex < data.size) {
                                    val itemValue = data[highlightedIndex]
                                    Toast.makeText(contextForToast, " Connected to: ${itemValue.first}", Toast.LENGTH_SHORT).show()
                                    LMS.setPlayer(itemValue.first, itemValue.second)
                                    expanded = false
                                } else if (highlightedIndex == backgroundRowIndex) {
                                    val newStyle = when (backgroundStyle) {
                                        SettingsRepository.BACKGROUND_STYLE_ARTIST -> SettingsRepository.BACKGROUND_STYLE_ARTIST_GRAYSCALE
                                        SettingsRepository.BACKGROUND_STYLE_ARTIST_GRAYSCALE -> SettingsRepository.BACKGROUND_STYLE_ALBUM
                                        SettingsRepository.BACKGROUND_STYLE_ALBUM -> SettingsRepository.BACKGROUND_STYLE_STARFIELD
                                        else -> SettingsRepository.BACKGROUND_STYLE_ARTIST
                                    }
                                    backgroundStyle = newStyle
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val current = SettingsRepository.get(contextForToast)
                                        SettingsRepository.save(contextForToast, current.copy(backgroundStyle = newStyle))
                                        AppConfig.load(contextForToast)
                                    }
                                    // Leave the menu open - this is a quick toggle
                                    // people may want to flip back and forth on.
                                } else if (highlightedIndex == standbyRowIndex) {
                                    val newValue = !standbyOnExit
                                    standbyOnExit = newValue
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val current = SettingsRepository.get(contextForToast)
                                        SettingsRepository.save(contextForToast, current.copy(standbyOnExit = newValue))
                                        AppConfig.load(contextForToast)
                                    }
                                    // Leave the menu open, same as the background toggle.
                                } else {
                                    NavState.showSettings = true
                                    expanded = false
                                }
                                true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (highlightedIndex < data.size) {
                                    val itemValue = data[highlightedIndex]
                                    val newDefaultMac = if (defaultMac == itemValue.second) "" else itemValue.second
                                    val newDefaultName = if (newDefaultMac.isEmpty()) "" else itemValue.first
                                    defaultMac = newDefaultMac
                                    val toastMsg = if (newDefaultMac.isEmpty())
                                        " Default player cleared"
                                    else
                                        " Default player: ${itemValue.first}"
                                    Toast.makeText(contextForToast, toastMsg, Toast.LENGTH_SHORT).show()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        SettingsRepository.saveDefaultPlayer(contextForToast, newDefaultName, newDefaultMac)
                                        AppConfig.load(contextForToast)
                                    }
                                }
                                true
                            }
                            Key.Back -> {
                                expanded = false
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                data.forEachIndexed { itemIndex, itemValue ->
                    val isDefault = defaultMac == itemValue.second
                    Text(
                        text = (if (isDefault) "☑ " else "☐ ") + itemValue.first,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (highlightedIndex == itemIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                val backgroundLabel = when (backgroundStyle) {
                    SettingsRepository.BACKGROUND_STYLE_ALBUM -> stringResource(R.string.settings_background_album)
                    SettingsRepository.BACKGROUND_STYLE_STARFIELD -> stringResource(R.string.settings_background_starfield)
                    SettingsRepository.BACKGROUND_STYLE_ARTIST_GRAYSCALE -> stringResource(R.string.settings_background_artist_grayscale)
                    else -> stringResource(R.string.settings_background_artist)
                }
                Text(
                    text = "${stringResource(R.string.menu_background_prefix)}: $backgroundLabel",
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlightedIndex == backgroundRowIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                val standbyLabel = if (standbyOnExit)
                    stringResource(R.string.menu_standby_on)
                else
                    stringResource(R.string.menu_standby_off)
                Text(
                    text = "${stringResource(R.string.menu_standby_prefix)}: $standbyLabel",
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlightedIndex == standbyRowIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                Text(
                    text = stringResource(R.string.menu_settings),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (highlightedIndex == settingsRowIndex) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
