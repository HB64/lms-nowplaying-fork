## v1.0.2

### New
- **Library browser** (☰ button, top left): browse artists (with an A–Z jump), albums, tracks, genres and the music folder, search your library, and open your Lyrion favorites. Lists show cover art, and every item can be played now, played next or added to the end of the queue.
- **Queue panel** (button at the bottom right): see everything that is queued, with covers and the current track highlighted. Pick a track to jump to it or remove it from the queue.
- **Mix menu** (inside the ☰ browser): switch the Auto Mix plugins SugarCube, MIPster, Random Flow and Lyrion's own Random Mix on or off for the selected player, start a new mix, start a batch or add a batch, and read a short info text about each plugin's own settings. Switching one mix plugin on switches the other mix plugins off.
- **Start-mix button on Now Playing**: while an internet radio stream plays and an Auto Mix plugin is on, a button next to the player controls (with that plugin's icon) starts a mix right away.
- **Material-style shuffle and repeat buttons**: shuffle songs / shuffle releases / off, and repeat queue / repeat one song / Don't Stop The Music / off. Don't Stop The Music keeps its own setting and now works alongside the mix plugins.
- **"Replace next track" follows the active mix plugin** (SugarCube, MIPster, Random Flow, then Don't Stop The Music, then a random track), so the old plugin choice in Settings is no longer needed. The button is hidden while a stream or a batch plays.
- **Progress bar with a position dot**: the not-yet-played part is now a neutral translucent white, so the bar stays readable on any background. Covers that are mostly grey or black get a light bar instead of blue.
- **English and Dutch interface**: all texts follow the language of the device.
- The browser and queue panels close by themselves after 15 seconds without remote input.
- After closing the "⋮" menu or the ☰ browser, focus returns to the button you opened it with, and moves to play/pause after the idle time.
- The name of the selected player is updated immediately, and the screen shows the state of the newly selected player, also when that player has nothing queued.

### Fixes
- Selecting a player in the "⋮" menu was unreliable: the screen could keep showing the previous player's track or name, a late answer from the old player could overwrite the new one, and the player list could be briefly empty or half filled.
- The "⋮" menu sometimes stayed open or reopened after choosing a player, and the focus could end up on the queue button instead of play/pause.
- The Random Mix checkmark in the Mix menu disappeared after a few seconds although the mix was running. The app now keeps track of a Random Mix it started.
- Opening the "⋮" menu no longer shifts the whole screen down, and the menu starts on the currently selected player.
- A hover-related crash on older Android versions is fixed.
- Removed unused code and resources left over from the old smart-replace plugin setting.
