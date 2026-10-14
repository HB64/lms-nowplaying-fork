## v1.0.1

### New
- **Four Now Playing background styles to choose from**: artist photo, artist photo in grayscale, blurred album cover, or a calm animated starfield. Configurable from Settings, or switched instantly from the "⋮" menu without opening the settings screen.
- **Standby-on-exit is now optional**: previously the active player always went into standby when the app closed. This can now be turned on or off, either from Settings or with a quick toggle in the "⋮" menu.
- Tracks with multiple artists (e.g. "Pete Namlook / Tetsu Inoue") now automatically fall back to the blurred album cover, so you no longer see the same generic artist image repeated.
- Radio stations and streams without their own album art now show artwork too, using the `artwork_url` Lyrion provides for these sources.
- Clean placeholder (music note icon) instead of a blank box when no artwork is available at all.
- **Choice of smart-replace plugin**: the "replace next track" button can now use either SugarCube or RandomFlow for its music-similarity pick (configurable in Settings or the "⋮" menu), before falling back to Don't Stop The Music and then a plain random track.
- **Refreshed Now Playing layout**, inspired by Material skin: rounded corners on the album covers, a full-width progress bar with elapsed/total time on either side, and title/artist/album plus the "Next" block centered next to the cover.
- **Progress bar color follows the album cover**: the bar picks up the cover's main color (falling back to white when there's no artwork) and fades smoothly between tracks.
- **Softer blurred-cover background**: the blurred album cover background is now blurrier and zoomed in further, for a calmer color-wash look.
- **Material-style transport controls**: play/pause is a solid white circle with a dark symbol, previous/next use solid icons, and all buttons (including the "⋮" menu and replace-next button) have a compact focus circle.
- The not-yet-played part of the progress bar now uses a lighter tint of the played part's color (red becomes light red, blue becomes light blue).
- **Focus returns to play/pause when idle**: after 15 seconds without remote input, D-pad focus moves back to the play/pause button (never while the "⋮" menu is open), and after 30 seconds the focus ring is hidden until the next key press.
- The smart-replace plugin now defaults to "None" on a fresh install, so it works out of the box without SugarCube or RandomFlow. If the plugin you picked doesn't react (for example its Auto Mix/Chain is off), a clear message tells you to check its settings.

### Fixes
- Album art and artist backdrop could stay blank after a full library rescan, because stale queue entries with temporary negative IDs were incorrectly skipped.
- The artist backdrop could keep showing the previous track's photo when switching to a track with no album art; it's now cleared correctly.
- Some internet radio streams showed no cover art at all, because Lyrion reports a synthetic numeric coverid for these tracks (with no real cover behind it) and provides its `artwork_url` as a path relative to the server rather than a full URL. Both cases are now handled correctly, so streams with a matching Last.fm/imageproxy cover show it properly. (Coverage still depends on what the station itself reports, so results can vary a bit by station.)
- Opening Settings from the "⋮" menu and pressing Exit/Back without changing anything used to close the whole app. It now returns to the Now Playing screen instead.
- The "replace next track" button now waits up to 8 seconds for the chosen plugin to finish, so slower similarity requests (e.g. a large MusicIP ask via SugarCube) are no longer mistaken for "nothing happened".
- The "replace next track" button no longer disturbs a queued album or batch: if there's more than just one track left to play, it now leaves the queue alone instead of swapping in an unrelated track.
