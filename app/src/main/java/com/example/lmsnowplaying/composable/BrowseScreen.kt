package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import com.example.lmsnowplaying.network.logitechmediaserver.BasicAuthInterceptor
import com.example.lmsnowplaying.settings.AppConfig
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.network.logitechmediaserver.BrowseItem
import com.example.lmsnowplaying.network.logitechmediaserver.BrowseTrack
import com.example.lmsnowplaying.R
import com.example.lmsnowplaying.network.logitechmediaserver.FavoriteEntry
import com.example.lmsnowplaying.network.logitechmediaserver.FolderEntry
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.network.logitechmediaserver.MixAction
import com.example.lmsnowplaying.network.logitechmediaserver.MixPlugin
import com.example.lmsnowplaying.network.logitechmediaserver.MixState
import com.example.lmsnowplaying.network.logitechmediaserver.SearchResults
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Library browsing panel, modelled on the Material skin's "Browse" page: a
// top-level list that leads to sub-lists (Artists -> Albums -> Tracks,
// plus Search), with play / queue actions on albums and tracks.
private enum class BrowseLevel {
    ROOT, ARTISTS, ALBUMS, TRACKS, TRACK_ACTIONS, SEARCH, GENRES, GENRE_ARTISTS, FOLDER, MIX,
    FAVORITES, FAVORITE_ACTIONS
}

// Lyrion's default "ignored articles" list.
private val ignoredArticles = Regex("^(the|el|la|los|las|le|les)\\s+", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowseScreen() {
    val appContext = LocalContext.current.applicationContext
    var level by remember { mutableStateOf(BrowseLevel.ROOT) }
    // Where we came from, so Back/left retraces the actual route (also when
    // an artist/album was reached through Search instead of the artist list).
    val stack = remember { mutableStateListOf<BrowseLevel>() }
    val go: (BrowseLevel) -> Unit = { to ->
        stack.add(level)
        level = to
    }

    // Artist list, kept so going back returns to the same spot without reloading.
    var artists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val artistListState = rememberLazyListState()
    var selectedArtistIndex by remember { mutableIntStateOf(0) }
    var chosenArtist by remember { mutableStateOf<BrowseItem?>(null) }

    // Albums of the chosen artist.
    var albums by remember { mutableStateOf<List<BrowseItem>?>(null) }
    var albumsArtistId by remember { mutableStateOf("") }
    val albumListState = rememberLazyListState()
    var selectedAlbumIndex by remember { mutableIntStateOf(0) }
    var chosenAlbum by remember { mutableStateOf<BrowseItem?>(null) }

    // The track the action menu (play now / next / add) is open for.
    var actionTrack by remember { mutableStateOf<BrowseTrack?>(null) }

    // Genres, and the artists within the chosen genre.
    var genres by remember { mutableStateOf<List<BrowseItem>?>(null) }
    val genreListState = rememberLazyListState()
    var selectedGenreIndex by remember { mutableIntStateOf(0) }
    var chosenGenre by remember { mutableStateOf<BrowseItem?>(null) }
    var genreArtists by remember { mutableStateOf<List<BrowseItem>?>(null) }
    var genreArtistsGenreId by remember { mutableStateOf("") }
    val genreArtistListState = rememberLazyListState()
    var selectedGenreArtistIndex by remember { mutableIntStateOf(0) }

    // Music folder: the path of folders opened so far (last = the one shown).
    val folderPath = remember { mutableStateListOf<BrowseItem>() }

    // Favorites: the path of favorite folders opened so far (last = the one
    // shown), and the favorite the action menu is open for.
    val favPath = remember { mutableStateListOf<BrowseItem>() }
    var actionFavorite by remember { mutableStateOf<BrowseItem?>(null) }

    // Search
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }
    var lastTerm by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Explanation next to the "Mix" title (stays visible for a while)
    var showMixInfo by remember { mutableStateOf(false) }
    LaunchedEffect(showMixInfo) {
        if (showMixInfo) {
            kotlinx.coroutines.delay(10_000)
            showMixInfo = false
        }
    }

    val chooseArtist: (BrowseItem) -> Unit = { artist ->
        // A different artist means a different album list; the same artist
        // keeps its list and position.
        if (artist.id != albumsArtistId) {
            albums = null
            selectedAlbumIndex = 0
            albumsArtistId = artist.id
        }
        chosenArtist = artist
        go(BrowseLevel.ALBUMS)
    }
    val chooseAlbum: (BrowseItem) -> Unit = { album ->
        chosenAlbum = album
        go(BrowseLevel.TRACKS)
    }
    val chooseTrack: (BrowseTrack) -> Unit = { track ->
        actionTrack = track
        go(BrowseLevel.TRACK_ACTIONS)
    }

    val chooseGenre: (BrowseItem) -> Unit = { genre ->
        if (genre.id != genreArtistsGenreId) {
            genreArtists = null
            selectedGenreArtistIndex = 0
            genreArtistsGenreId = genre.id
        }
        chosenGenre = genre
        go(BrowseLevel.GENRE_ARTISTS)
    }

    // Back goes up one step (inside the music folder: one folder up), and
    // from the top level closes the panel.
    val goBack: () -> Unit = {
        if (level == BrowseLevel.FOLDER && folderPath.size > 1) {
            folderPath.removeAt(folderPath.size - 1)
        } else if (level == BrowseLevel.FAVORITES && favPath.size > 1) {
            favPath.removeAt(favPath.size - 1)
        } else if (stack.isEmpty()) NavState.showBrowse = false
        else level = stack.removeAt(stack.size - 1)
    }
    BackHandler(enabled = true) { goBack() }

    // Shown as a panel on top of Now Playing (like the player menu), not
    // as a full screen.
    Box(
        modifier = Modifier
            .width(300.dp)
            .heightIn(max = 366.dp)
            // Keep D-pad focus inside the panel: it stays open until Back,
            // so focus must not wander off to the buttons behind it.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            // D-pad left on a list row = one step back, like Back (the A-Z
            // strip handles its own left key first, so it isn't affected).
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    goBack()
                    true
                } else false
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2181818))
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (level) {
                    BrowseLevel.ROOT -> stringResource(R.string.browse_title)
                    BrowseLevel.ARTISTS -> stringResource(R.string.browse_artists)
                    BrowseLevel.ALBUMS -> chosenArtist?.name ?: stringResource(R.string.browse_albums)
                    BrowseLevel.TRACKS -> chosenAlbum?.name ?: stringResource(R.string.browse_tracks)
                    BrowseLevel.TRACK_ACTIONS -> actionTrack?.title ?: stringResource(R.string.browse_track)
                    BrowseLevel.SEARCH -> stringResource(R.string.browse_search)
                    BrowseLevel.GENRES -> stringResource(R.string.browse_genres)
                    BrowseLevel.GENRE_ARTISTS -> chosenGenre?.name ?: stringResource(R.string.browse_genre)
                    BrowseLevel.FOLDER -> folderPath.lastOrNull()?.name ?: stringResource(R.string.browse_music_folder)
                    BrowseLevel.MIX -> stringResource(R.string.browse_mix)
                    BrowseLevel.FAVORITES -> favPath.lastOrNull()?.name ?: stringResource(R.string.browse_favorites)
                    BrowseLevel.FAVORITE_ACTIONS -> actionFavorite?.name ?: stringResource(R.string.browse_favorite)
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            if (level == BrowseLevel.MIX) {
                FocusIconButton(
                    onClick = { showMixInfo = !showMixInfo },
                    size = 28.dp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            }
            if (level == BrowseLevel.MIX && showMixInfo) {
                Text(
                    stringResource(R.string.mix_info),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                )
            }
            when (level) {
                BrowseLevel.ROOT -> RootList(
                    onSearch = { go(BrowseLevel.SEARCH) },
                    onArtists = { go(BrowseLevel.ARTISTS) },
                    onGenres = { go(BrowseLevel.GENRES) },
                    onFolders = {
                        folderPath.clear()
                        folderPath.add(BrowseItem("", appContext.getString(R.string.browse_music_folder)))
                        go(BrowseLevel.FOLDER)
                    },
                    onFavorites = {
                        favPath.clear()
                        favPath.add(BrowseItem("", appContext.getString(R.string.browse_favorites)))
                        go(BrowseLevel.FAVORITES)
                    },
                    onMix = { go(BrowseLevel.MIX) },
                )
                BrowseLevel.MIX -> MixPanel()
                BrowseLevel.FAVORITES -> favPath.lastOrNull()?.let { folder ->
                    FavoritesList(
                        folder = folder,
                        onFolder = { favPath.add(it) },
                        onItem = {
                            actionFavorite = it
                            go(BrowseLevel.FAVORITE_ACTIONS)
                        },
                    )
                }
                BrowseLevel.FAVORITE_ACTIONS -> actionFavorite?.let { FavoriteActions(it, onDone = goBack) }
                BrowseLevel.GENRES -> LoadedList(
                    items = genres,
                    load = { LMS.getGenres() },
                    onLoaded = { genres = it },
                    emptyText = stringResource(R.string.browse_no_genres),
                    listState = genreListState,
                    selectedIndex = selectedGenreIndex,
                    onSelect = { index ->
                        selectedGenreIndex = index
                        genres?.getOrNull(index)?.let { chooseGenre(it) }
                    }
                )
                BrowseLevel.GENRE_ARTISTS -> LoadedList(
                    items = genreArtists,
                    load = { LMS.getArtistsByGenre(chosenGenre?.id ?: "") },
                    onLoaded = { genreArtists = it },
                    emptyText = stringResource(R.string.browse_no_artists),
                    listState = genreArtistListState,
                    selectedIndex = selectedGenreArtistIndex,
                    onSelect = { index ->
                        selectedGenreArtistIndex = index
                        genreArtists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.FOLDER -> folderPath.lastOrNull()?.let { folder ->
                    FolderList(
                        folder = folder,
                        onFolder = { folderPath.add(it) },
                        onTrack = chooseTrack,
                    )
                }
                BrowseLevel.ARTISTS -> ArtistList(
                    artists = artists,
                    onLoaded = { artists = it },
                    listState = artistListState,
                    selectedIndex = selectedArtistIndex,
                    onSelect = { index ->
                        selectedArtistIndex = index
                        artists?.getOrNull(index)?.let { chooseArtist(it) }
                    }
                )
                BrowseLevel.ALBUMS -> AlbumList(
                    artistId = chosenArtist?.id ?: "",
                    albums = albums,
                    onLoaded = { albums = it },
                    listState = albumListState,
                    selectedIndex = selectedAlbumIndex,
                    onSelect = { index ->
                        selectedAlbumIndex = index
                        albums?.getOrNull(index)?.let { chooseAlbum(it) }
                    }
                )
                BrowseLevel.TRACKS -> TrackList(
                    albumId = chosenAlbum?.id ?: "",
                    onTrack = chooseTrack,
                )
                BrowseLevel.TRACK_ACTIONS -> actionTrack?.let { TrackActions(it, onDone = goBack) }
                BrowseLevel.SEARCH -> SearchPanel(
                    query = query,
                    onQueryChange = { query = it },
                    results = results,
                    searching = searching,
                    lastTerm = lastTerm,
                    onSearch = {
                        val term = query.trim()
                        if (term.isNotEmpty()) {
                            // A newer search replaces one still running
                            searchJob?.cancel()
                            lastTerm = term
                            searchJob = scope.launch {
                                searching = true
                                results = withContext(Dispatchers.IO) { LMS.search(term) }
                                searching = false
                            }
                        }
                    },
                    onArtist = chooseArtist,
                    onAlbum = chooseAlbum,
                    onTrack = chooseTrack,
                )
            }
        }
    }
}

@Composable
private fun RootList(
    onSearch: () -> Unit,
    onArtists: () -> Unit,
    onGenres: () -> Unit,
    onFolders: () -> Unit,
    onFavorites: () -> Unit,
    onMix: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.browse_search), onClick = onSearch, modifier = Modifier.focusRequester(firstFocus))
        BrowseRow(stringResource(R.string.browse_artists), onClick = onArtists)
        BrowseRow(stringResource(R.string.browse_genres), onClick = onGenres)
        BrowseRow(stringResource(R.string.browse_music_folder), onClick = onFolders)
        BrowseRow(stringResource(R.string.browse_favorites), onClick = onFavorites)
        BrowseRow(stringResource(R.string.browse_mix), onClick = onMix)
        // Playlists could follow later.
    }
}

// One level of the Lyrion favorites: favorite folders and favorites, plus
// play / add actions for a whole folder (not for the top level).
@Composable
private fun FavoritesList(
    folder: BrowseItem,
    onFolder: (BrowseItem) -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    var entries by remember(folder.id) { mutableStateOf<List<FavoriteEntry>?>(null) }
    LaunchedEffect(folder.id) {
        entries = withContext(Dispatchers.IO) { LMS.getFavorites(folder.id) }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val runFolder: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.favoritesControl(cmd, folder.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
    }

    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_favorites), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val imageLoader = rememberLmsImageLoader()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    val hasActions = folder.id.isNotBlank()
    LazyColumn {
        if (hasActions) {
            item {
                BrowseRow(stringResource(R.string.fav_play_all),
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { runFolder("load", context.getString(R.string.fav_playing)) })
            }
            item {
                BrowseRow(stringResource(R.string.fav_add_all),
                    onClick = { runFolder("add", context.getString(R.string.fav_added)) })
            }
        }
        items(list.size) { index ->
            val entry = list[index]
            ThumbRow(
                text = entry.name,
                imageUrl = null,
                placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.Star,
                round = false,
                imageLoader = imageLoader,
                onClick = {
                    if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name))
                    else onItem(BrowseItem(entry.id, entry.name))
                },
                modifier = if (!hasActions && index == 0) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

// Action menu for one favorite: play now / next / add to the end.
@Composable
private fun FavoriteActions(favorite: BrowseItem, onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.favoritesControl(cmd, favorite.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(favorite) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.action_play_now), modifier = Modifier.focusRequester(firstFocus),
            onClick = { run("load", context.getString(R.string.fav_item_playing)) })
        BrowseRow(stringResource(R.string.action_play_next), onClick = { run("insert", context.getString(R.string.fav_item_next)) })
        BrowseRow(stringResource(R.string.action_add_end), onClick = { run("add", context.getString(R.string.fav_item_added)) })
    }
}

// Auto Mix control: which plugin keeps the queue filled (only one at a
// time - switching one on switches the others off), plus start/add actions
// for the active one.
@Composable
private fun MixPanel() {
    var state by remember { mutableStateOf<MixState?>(null) }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // Read the state now and keep it fresh (a plugin may switch another off).
    // A plugin that was just switched on: keep showing it as on for a few
    // seconds, until the server reports it as on as well.
    var pendingOn by remember { mutableStateOf<Pair<MixPlugin, Long>?>(null) }
    fun withPending(s: MixState, p: Pair<MixPlugin, Long>?): MixState =
        if (p != null && System.currentTimeMillis() - p.second < 6000 && p.first !in s.on)
            s.copy(on = setOf(p.first))
        else s

    LaunchedEffect(Unit) {
        while (true) {
            state = withPending(withContext(Dispatchers.IO) { LMS.getMixState() }, pendingOn)
            kotlinx.coroutines.delay(3000)
        }
    }

    val current = state
    if (current == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    // Don't Stop The Music is not listed here (it is set with the repeat button)
    if (current.available.none { it != MixPlugin.DSTM }) {
        Text(stringResource(R.string.mix_no_plugins), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    val order = listOf(MixPlugin.SUGARCUBE, MixPlugin.MIPSTER, MixPlugin.RANDOMFLOW, MixPlugin.RANDOMPLAY)
    val shown = order.filter { it in current.available }
    // The plugin the start/batch actions belong to (Don't Stop The Music has none)
    val active = current.on.firstOrNull { it != MixPlugin.DSTM }

    LazyColumn {
        items(shown.size) { index ->
            val plugin = shown[index]
            val isOn = plugin in current.on
            BrowseRow(
                text = (if (isOn) "☑ " else "☐ ") + plugin.label,
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = {
                    // Show the new state right away, then really switch
                    state = current.copy(on = if (isOn) current.on - plugin else setOf(plugin))
                    scope.launch(Dispatchers.IO) {
                        LMS.setMixPlugin(plugin, !isOn, current)
                        // A plugin can take a moment before it reports itself
                        // as on (Random Mix starts its mix right away but
                        // answers "not active" for a second or two), so the
                        // checkmark is held for a few seconds (see pendingOn).
                        pendingOn = if (isOn) null else plugin to System.currentTimeMillis()
                        state = withPending(LMS.getMixState(), pendingOn)
                    }
                }
            )
        }
        if (active != null) {
            item { SectionHeader(active.label) }
            item {
                BrowseRow(stringResource(R.string.mix_start_new), onClick = {
                    scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.START_MIX) }
                    Toast.makeText(context, context.getString(R.string.mix_started), Toast.LENGTH_SHORT).show()
                })
            }
            // Lyrion's Random Mix has no batches
            if (active != MixPlugin.RANDOMPLAY) {
                item {
                    BrowseRow(stringResource(R.string.mix_start_batch), onClick = {
                        scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.START_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_started), Toast.LENGTH_SHORT).show()
                    })
                }
                item {
                    BrowseRow(stringResource(R.string.mix_add_batch), onClick = {
                        scope.launch(Dispatchers.IO) { LMS.mixAction(active, MixAction.ADD_BATCH) }
                        Toast.makeText(context, context.getString(R.string.mix_batch_added), Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

// A plain scrollable list of items loaded on first use and then kept (so
// going back returns to the same spot without reloading).
@Composable
private fun LoadedList(
    items: List<BrowseItem>?,
    load: suspend () -> List<BrowseItem>,
    onLoaded: (List<BrowseItem>) -> Unit,
    emptyText: String,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LaunchedEffect(items == null) {
        if (items == null) {
            onLoaded(withContext(Dispatchers.IO) { load() })
        }
    }

    if (items == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (items.isEmpty()) {
        Text(emptyText, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val startFocus = remember { FocusRequester() }
    LaunchedEffect(items) {
        listState.scrollToItem(selectedIndex.coerceIn(0, items.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    LazyColumn(state = listState) {
        items(items.size) { index ->
            BrowseRow(
                text = items[index].name,
                onClick = { onSelect(index) },
                modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
            )
        }
    }
}

// One level of the music folder tree: sub folders and tracks, plus play /
// add actions for the whole folder (not for the top level).
@Composable
private fun FolderList(
    folder: BrowseItem,
    onFolder: (BrowseItem) -> Unit,
    onTrack: (BrowseTrack) -> Unit,
) {
    var entries by remember(folder.id) { mutableStateOf<List<FolderEntry>?>(null) }
    LaunchedEffect(folder.id) {
        entries = withContext(Dispatchers.IO) { LMS.getFolder(folder.id) }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val runFolder: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, folderId = folder.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
    }

    val list = entries
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val imageLoader = rememberLmsImageLoader()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    if (list.isEmpty() && folder.id.isBlank()) {
        Text(stringResource(R.string.browse_no_folders), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val hasActions = folder.id.isNotBlank()
    LazyColumn {
        if (hasActions) {
            item {
                // A folder that holds more folders (e.g. an artist) plays all
                // its albums; one that only holds tracks is a single album.
                val many = list.any { it.isFolder }
                BrowseRow(if (many) stringResource(R.string.folder_play_all_albums) else stringResource(R.string.folder_play_album),
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { runFolder("load", if (many) context.getString(R.string.folder_albums_playing) else context.getString(R.string.folder_album_playing)) })
            }
            item {
                val many = list.any { it.isFolder }
                BrowseRow(if (many) stringResource(R.string.folder_add_all_albums) else stringResource(R.string.folder_add_album),
                    onClick = { runFolder("add", if (many) context.getString(R.string.folder_albums_added) else context.getString(R.string.folder_album_added)) })
            }
        }
        items(list.size) { index ->
            val entry = list[index]
            ThumbRow(
                text = entry.name,
                imageUrl = null,
                placeholder = if (entry.isFolder) Icons.Filled.Folder else Icons.Outlined.MusicNote,
                round = false,
                imageLoader = imageLoader,
                onClick = {
                    if (entry.isFolder) onFolder(BrowseItem(entry.id, entry.name))
                    else onTrack(BrowseTrack(entry.id, entry.name, 0))
                },
                modifier = if (!hasActions && index == 0) Modifier.focusRequester(firstFocus) else Modifier
            )
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults?,
    searching: Boolean,
    lastTerm: String,
    onSearch: () -> Unit,
    onArtist: (BrowseItem) -> Unit,
    onAlbum: (BrowseItem) -> Unit,
    onTrack: (BrowseTrack) -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Only an explicit search (keyboard's search key) moves focus to the
    // results; live results while typing must leave focus in the field.
    var jumpToResults by remember { mutableStateOf(false) }

    // Land in the text field when opening a fresh search.
    LaunchedEffect(Unit) {
        if (results == null) fieldFocus.requestFocus()
    }
    // Live search: shortly after typing stops, search what's typed so far.
    // (Restarts on every keystroke, which gives the pause its debounce.)
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length >= 2 && term != lastTerm) {
            kotlinx.coroutines.delay(500)
            onSearch()
        }
    }
    // After an explicit search, jump to the first result.
    LaunchedEffect(results) {
        if (results != null && jumpToResults) {
            jumpToResults = false
            try { firstResultFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    Column {
        // Search field: OK on the remote opens the on-screen keyboard.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.browse_type_to_search), color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    jumpToResults = true
                    // Already searched live for this exact text: just move on
                    if (query.trim() == lastTerm) {
                        try { firstResultFocus.requestFocus() } catch (_: Exception) { }
                        jumpToResults = false
                    } else onSearch()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            keyboard?.show()
                            true
                        } else false
                    }
            )
        }

        if (searching) {
            Text(stringResource(R.string.browse_searching), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return@Column
        }
        if (results == null) return@Column
        if (results.artists.isEmpty() && results.albums.isEmpty() && results.tracks.isEmpty()) {
            Text(stringResource(R.string.browse_nothing_found), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            return@Column
        }

        // The very first result row (whichever group it is in) gets the
        // focus after a search.
        val imageLoader = rememberLmsImageLoader()

        // Only the first few artists, so albums and tracks stay within reach
        val maxArtists = 5
        var artistsExpanded by remember(results) { mutableStateOf(false) }
        val shownArtists = if (artistsExpanded) results.artists else results.artists.take(maxArtists)

        LazyColumn {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_artists)) }
                items(shownArtists.size) { i ->
                    // No artist photos available: generic round placeholder
                    ThumbRow(shownArtists[i].name, imageUrl = null, placeholder = Icons.Filled.Person,
                        round = true, imageLoader = imageLoader,
                        onClick = { onArtist(shownArtists[i]) },
                        modifier = if (i == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                }
                if (!artistsExpanded && results.artists.size > maxArtists) {
                    item {
                        BrowseRow(stringResource(R.string.browse_more_artists, results.artists.size - maxArtists),
                            onClick = { artistsExpanded = true })
                    }
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_albums)) }
                items(results.albums.size) { i ->
                    ThumbRow(results.albums[i].name, imageUrl = coverUrl(results.albums[i].artworkId),
                        placeholder = Icons.Outlined.MusicNote, round = false, imageLoader = imageLoader,
                        onClick = { onAlbum(results.albums[i]) },
                        modifier = if (results.artists.isEmpty() && i == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                }
            }
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.browse_tracks)) }
                items(results.tracks.size) { i ->
                    ThumbRow(results.tracks[i].title, imageUrl = coverUrl(results.tracks[i].coverId),
                        placeholder = Icons.Outlined.MusicNote, round = false, imageLoader = imageLoader,
                        onClick = { onTrack(results.tracks[i]) },
                        modifier = if (results.artists.isEmpty() && results.albums.isEmpty() && i == 0)
                            Modifier.focusRequester(firstResultFocus) else Modifier)
                }
            }
        }
    }
}

// Cover thumbnails come from the Lyrion server (needs its login).
@Composable
internal fun rememberLmsImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                    .build()
            }
            .build()
    }
}

// Small cover picture address for a cover id / artwork track id, or null if unknown.
internal fun coverUrl(id: String): String? =
    if (id.isBlank()) null else "${AppConfig.current.lmsUrl}/music/$id/cover_80x80_o.jpg"

// A list row with a small picture in front (cover, or a generic icon when
// there is none / while it is loading).
@Composable
internal fun ThumbRow(
    text: String,
    imageUrl: String?,
    placeholder: ImageVector,
    round: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            // Placeholder icon underneath; the picture covers it once loaded
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text, color = Color.White, fontSize = 14.sp, maxLines = 1,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ArtistList(
    artists: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LaunchedEffect(Unit) {
        if (artists == null) {
            onLoaded(withContext(Dispatchers.IO) { LMS.getArtists(count = 100000) })
        }
    }

    if (artists == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (artists.isEmpty()) {
        Text(stringResource(R.string.browse_no_artists), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    // Put focus on the artist we came from (the first one on first open).
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(artists) {
        listState.scrollToItem(selectedIndex.coerceIn(0, artists.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    // First list position of each starting letter (anything that isn't
    // A-Z, such as digits, counts as "#"); a letter without artists jumps
    // to the next letter that has some.
    val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val letterStart = remember(artists) {
        val first = mutableMapOf<String, Int>()
        artists.forEachIndexed { i, a ->
            // Lyrion sorts ignoring leading articles ("The 3 Jays" sorts
            // under 3), so the index must do the same.
            val c = a.name.trim().replace(ignoredArticles, "").firstOrNull()?.uppercaseChar()
            // Digits and symbols (which sort first) are grouped under "#"
            val key = if (c != null && c in 'A'..'Z') c.toString() else "#"
            if (key !in first) first[key] = i
        }
        letters.indices.map { li ->
            (li until letters.size).firstNotNullOfOrNull { first[letters[it]] } ?: (artists.size - 1)
        }
    }
    var stripIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Row {
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(artists.size) { index ->
                BrowseRow(
                    text = artists[index].name,
                    onClick = { onSelect(index) },
                    modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
                )
            }
        }
        AlphabetStrip(
            letters = letters,
            selected = stripIndex,
            onMove = { newIndex ->
                stripIndex = newIndex
                scope.launch { listState.scrollToItem(letterStart[newIndex]) }
            },
        )
    }
}

// Narrow A-Z index on the right side of a long list, like in the Material
// skin. D-pad right from the list enters it, up/down jumps through the
// letters (the list scrolls along), left/OK goes back to the list.
@Composable
private fun AlphabetStrip(letters: List<String>, selected: Int, onMove: (Int) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(start = 4.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onMove((selected - 1).coerceAtLeast(0)); true }
                    Key.DirectionDown -> { onMove((selected + 1).coerceAtMost(letters.size - 1)); true }
                    Key.DirectionLeft, Key.DirectionCenter, Key.Enter -> {
                        focusManager.moveFocus(FocusDirection.Left); true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { i, letter ->
            val isSelected = focused && i == selected
            Text(
                text = letter,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = if (focused) 0.9f else 0.55f),
                modifier = Modifier
                    .width(18.dp)
                    .height(11.5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) Color.White else Color.Transparent),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumList(
    artistId: String,
    albums: List<BrowseItem>?,
    onLoaded: (List<BrowseItem>) -> Unit,
    listState: LazyListState,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LaunchedEffect(artistId, albums == null) {
        if (albums == null) {
            onLoaded(withContext(Dispatchers.IO) { LMS.getAlbums(artistId) })
        }
    }

    if (albums == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (albums.isEmpty()) {
        Text(stringResource(R.string.browse_no_albums), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    // Focus on the album we came back from (the first one on first open).
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(albums) {
        listState.scrollToItem(selectedIndex.coerceIn(0, albums.size - 1))
        try { startFocus.requestFocus() } catch (_: Exception) { }
    }

    val imageLoader = rememberLmsImageLoader()
    LazyColumn(state = listState) {
        items(albums.size) { index ->
            ThumbRow(
                text = albums[index].name,
                imageUrl = coverUrl(albums[index].artworkId),
                placeholder = Icons.Outlined.MusicNote,
                round = false,
                imageLoader = imageLoader,
                onClick = { onSelect(index) },
                modifier = if (index == selectedIndex) Modifier.focusRequester(startFocus) else Modifier
            )
        }
    }
}

@Composable
private fun TrackList(albumId: String, onTrack: (BrowseTrack) -> Unit) {
    var tracks by remember(albumId) { mutableStateOf<List<BrowseTrack>?>(null) }
    LaunchedEffect(albumId) {
        if (albumId.isNotBlank()) {
            tracks = withContext(Dispatchers.IO) { LMS.getTracks(albumId) }
        }
    }

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // Runs a play/queue command for the whole album and tells the user what happened.
    val runAlbum: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, albumId = albumId) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
    }

    val list = tracks
    if (list == null) {
        Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }
    if (list.isEmpty()) {
        Text(stringResource(R.string.browse_no_tracks), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        return
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) { firstFocus.requestFocus() }

    LazyColumn {
        // Two rows for the whole album first, then the tracks.
        item {
            BrowseRow(
                text = stringResource(R.string.folder_play_album),
                modifier = Modifier.focusRequester(firstFocus),
                onClick = { runAlbum("load", context.getString(R.string.folder_album_playing)) }
            )
        }
        item {
            BrowseRow(
                text = stringResource(R.string.folder_add_album),
                onClick = { runAlbum("add", context.getString(R.string.folder_album_added)) }
            )
        }
        items(list.size) { index ->
            val track = list[index]
            BrowseRow(
                text = if (track.trackNum > 0) "${track.trackNum}. ${track.title}" else track.title,
                onClick = { onTrack(track) },
            )
        }
    }
}

// Action menu for one track: play now / next / add to the end.
@Composable
private fun TrackActions(track: BrowseTrack, onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val run: (String, String) -> Unit = { cmd, message ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { LMS.playlistControl(cmd, trackId = track.id) }
            Toast.makeText(context, if (ok) message else context.getString(R.string.common_no_player), Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(track) { firstFocus.requestFocus() }

    Column {
        BrowseRow(stringResource(R.string.action_play_now), modifier = Modifier.focusRequester(firstFocus),
            onClick = { run("load", context.getString(R.string.track_playing)) })
        BrowseRow(stringResource(R.string.action_play_next), onClick = { run("insert", context.getString(R.string.track_next)) })
        BrowseRow(stringResource(R.string.action_add_end), onClick = { run("add", context.getString(R.string.track_added)) })
    }
}

@Composable
private fun BrowseRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
