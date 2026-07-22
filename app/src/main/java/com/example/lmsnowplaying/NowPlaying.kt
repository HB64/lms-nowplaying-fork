package com.example.lmsnowplaying

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.util.DebugLogger
import com.commit451.coiltransformations.BlurTransformation
import com.example.lmsnowplaying.composable.PlayPauseButton
import com.example.lmsnowplaying.composable.PlayerSelector
import com.example.lmsnowplaying.composable.QueueButton
import com.example.lmsnowplaying.composable.Starfield
import com.example.lmsnowplaying.helpers.HandleButton
import com.example.lmsnowplaying.network.logitechmediaserver.BasicAuthInterceptor
import com.example.lmsnowplaying.network.jellyfin.JellyfinApi
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.settings.AppConfig
import com.example.lmsnowplaying.settings.SettingsRepository
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient


@Composable
@Preview(heightDp = 540, widthDp = 960)
fun SecondaryScreen(){
    val currentView = LocalView.current
    DisposableEffect(Unit){
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier
        .background(Color.Blue)
        .onKeyEvent {
            CoroutineScope(Dispatchers.IO).launch {
                HandleButton(it)
            }
            false
        }
        .fillMaxSize()) {
        PlayerScreen()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(){

    var songName by remember { mutableStateOf("Song Name") }
    var artistName by remember { mutableStateOf("Artist") }
    var albumName by remember { mutableStateOf("") }
    var albumYear by remember { mutableStateOf("") }
    var jellyfinArtUrl by remember { mutableStateOf("")}
    var lmsArtUrl by remember { mutableStateOf("")}
    var artistDefaultArtUrl by remember { mutableStateOf("")}
    var albumArtUrl by remember { mutableStateOf("")}
    var playing by remember { mutableStateOf(LMS.isPlaying) }
    var elapsedTime by remember { mutableStateOf(LMS.elapsedTime) }
    var trackDuration by remember { mutableStateOf(LMS.trackDuration) }
    var currentSong = ""

    var nextTitle by remember { mutableStateOf("") }
    var nextArtist by remember { mutableStateOf("") }
    var nextCoverUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit){
        while (true){
            if (LMS.playerMac != "00:00:00:00:00:00"){
                CoroutineScope(Dispatchers.IO).launch{
                    LMS.status()
                    playing = LMS.isPlaying
                    elapsedTime = LMS.elapsedTime
                    trackDuration = LMS.trackDuration
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val result = LMS.update(LMS.playerMac)
                    val _songName = result[0]
                    val _artistName = result[1]
                    val _coverId = result[2]
                    val _portraitId = result[3]
                    val _albumName = result.getOrElse(4) { "" }
                    val _albumYear = result.getOrElse(5) { "" }
                    val _artworkUrl = result.getOrElse(6) { "" }
                    // Only skip re-fetching artwork for a song we've already
                    // seen if we actually managed to resolve art for it last
                    // time - otherwise a transient failure (e.g. the getSongInfo
                    // call hiccuping during a library rescan) would leave the
                    // cover/backdrop permanently blank until the next track,
                    // since it would never be retried.
                    if (_songName == "" || (_songName == currentSong && albumArtUrl.isNotEmpty())) return@launch
                    currentSong = _songName
                    songName = _songName
                    albumName = _albumName
                    albumYear = _albumYear
                    artistName = _artistName
                    println("Refreshing")
                    if (_coverId == "" && _artworkUrl == ""){
                        // No cover for this track at all - clear every art
                        // field, not just the album art, otherwise the
                        // backdrop keeps showing whatever the previous
                        // track's artist image was.
                        albumArtUrl = ""
                        lmsArtUrl = ""
                        artistDefaultArtUrl = ""
                        jellyfinArtUrl = ""
                    }else if (_coverId != "") {
                        albumArtUrl = "${AppConfig.current.lmsUrl}/music/$_coverId/cover.jpg"
                        artistDefaultArtUrl = "${AppConfig.current.lmsUrl}/music/$_coverId/cover.jpg"

                        if (skipArtistBackdrop(_artistName)) {
                            // User prefers the album cover, or this looks like
                            // a multi-artist credit ("A / B", "A & B") - those
                            // rarely have a real photo, so Lyrion/Jellyfin tend
                            // to silently fall back to the album cover anyway,
                            // which just gets repetitive across tracks.
                            lmsArtUrl = ""
                            jellyfinArtUrl = ""
                        } else {
                            // Resolve both candidates. Lyrion's portraitId isn't a
                            // reliable "has a real photo" signal (it can point at
                            // Lyrion's own generic/no-photo artwork), so we always
                            // also try Jellyfin; GetArtistArt tries Jellyfin first,
                            // then Lyrion, then falls back to the blurred cover.
                            lmsArtUrl = if (_portraitId != "") "${AppConfig.current.lmsUrl}/contributor/$_portraitId/image" else ""

                            var tempName = _artistName
                            tempName = tempName.replace(" ", "%20").replace("&", "%26")
                            jellyfinArtUrl = if(!_artistName.contains("[!\"#$%'()*+,-./:;\\\\<=>?@\\[\\]^_`{|}~]".toRegex())){
                                "${AppConfig.current.jellyfinUrl}/Artists/$tempName/Images/Backdrop/0"
                            }else{
                                JellyfinApi.GetArtistUrl(_artistName) ?: ""
                            }
                        }

                        println("DEBUG_ART artist=$_artistName lms=$lmsArtUrl jellyfin=$jellyfinArtUrl")
                    }else{
                        // Remote track (internet radio, Spotify/Tidal-style
                        // plugin, etc.) with no local coverid, but the server
                        // gave us a direct artwork_url for it - use that for
                        // both the small cover and the backdrop fallback.
                        albumArtUrl = _artworkUrl
                        artistDefaultArtUrl = _artworkUrl
                        lmsArtUrl = ""

                        var tempName = _artistName
                        tempName = tempName.replace(" ", "%20").replace("&", "%26")
                        jellyfinArtUrl = if (!skipArtistBackdrop(_artistName) && _artistName != "" && !_artistName.contains("[!\"#$%'()*+,-./:;\\\\<=>?@\\[\\]^_`{|}~]".toRegex())){
                            "${AppConfig.current.jellyfinUrl}/Artists/$tempName/Images/Backdrop/0"
                        }else{
                            ""
                        }

                        println("DEBUG_ART (remote) artist=$_artistName artwork=$_artworkUrl jellyfin=$jellyfinArtUrl")
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val upcoming = LMS.getUpcoming(LMS.playerMac, 2)
                    val next = upcoming.getOrNull(1)
                    if (next != null) {
                        nextTitle = next.title
                        nextArtist = next.artist
                        nextCoverUrl = if (next.coverId.isNotEmpty())
                            "${AppConfig.current.lmsUrl}/music/${next.coverId}/cover.jpg"
                        else ""
                    } else {
                        nextTitle = ""
                        nextArtist = ""
                        nextCoverUrl = ""
                    }
                }
            }
            delay(2000)
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier
        .background(Color.Black)
        .fillMaxSize()) {


        GetArtistArt(jellyfinArtUrl, lmsArtUrl, artistDefaultArtUrl){


            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 80.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier
                    .background(Color.Black)
                    .size(240.dp)){
                    GetAlbumArt(albumArtUrl)
                }
                Spacer(modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
                    Spacer(modifier = Modifier.size(35.dp))
                    Text(songName, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                    Spacer(modifier = Modifier.size(10.dp))

                    val byLabel = stringResource(R.string.now_playing_by_label)
                    val fromLabel = stringResource(R.string.now_playing_from_label)
                    val mutedWhite = Color.White.copy(alpha = 0.65f)

                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = mutedWhite)) { append("$byLabel ") }
                            withStyle(SpanStyle(color = Color.White)) { append(artistName) }
                        },
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )

                    if (albumName.isNotBlank()) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = mutedWhite)) { append("$fromLabel ") }
                                withStyle(SpanStyle(color = Color.White)) {
                                    append(if (albumYear.isNotBlank()) "$albumName ($albumYear)" else albumName)
                                }
                            },
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))

                    val progress = if (trackDuration > 0.0) {
                        (elapsedTime / trackDuration).toFloat().coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(elapsedTime), color = Color.White, fontSize = 12.sp)
                        Text(formatDuration(trackDuration), color = Color.White, fontSize = 12.sp)
                    }

                    if (nextTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.size(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier
                                .background(Color.Black)
                                .size(48.dp)) {
                                GetAlbumArt(nextCoverUrl)
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.now_playing_next_label),
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                )
                                Text(
                                    if (nextArtist.isNotBlank()) "$nextTitle – $nextArtist" else nextTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee()
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            QueueButton()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prevInteractionSource = remember { MutableInteractionSource() }
                val prevFocused by prevInteractionSource.collectIsFocusedAsState()
                TextButton(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            LMS.prev()
                        }
                    },
                    interactionSource = prevInteractionSource,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (prevFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Icon(modifier = Modifier.size(32.dp), tint = Color.White, imageVector = Icons.Outlined.SkipPrevious, contentDescription = "Previous")
                }
                Spacer(modifier = Modifier.size(32.dp))
                val playInteractionSource = remember { MutableInteractionSource() }
                val playFocused by playInteractionSource.collectIsFocusedAsState()
                val playFocusRequester = remember { FocusRequester() }

                // If a default player was already auto-selected on start,
                // there's nothing to set up via the options menu - so put
                // initial D-pad focus straight on the play button instead.
                LaunchedEffect(Unit) {
                    if (NavState.focusPlayButtonOnStart) {
                        playFocusRequester.requestFocus()
                    }
                }

                TextButton(
                    onClick = {
                        if (LMS.playerMac != "00:00:00:00:00:00") {
                            val newPlaying = !playing
                            playing = newPlaying
                            CoroutineScope(Dispatchers.IO).launch {
                                // Explicitly wake the player before resuming
                                // playback - some players (e.g. Squeezelite)
                                // ignore "play" while still flagged powered
                                // off, unlike hardware players that tend to
                                // wake implicitly on a play command.
                                if (newPlaying) {
                                    LMS.power(true)
                                }
                                LMS.playPause(newPlaying)
                            }
                        }
                    },
                    interactionSource = playInteractionSource,
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .clip(CircleShape)
                        .background(if (playFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    PlayPauseButton(playing)
                }
                Spacer(modifier = Modifier.size(32.dp))
                val nextInteractionSource = remember { MutableInteractionSource() }
                val nextFocused by nextInteractionSource.collectIsFocusedAsState()
                TextButton(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            LMS.next()
                        }
                    },
                    interactionSource = nextInteractionSource,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (nextFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Icon(modifier = Modifier.size(32.dp), tint = Color.White, imageVector = Icons.Outlined.SkipNext, contentDescription = "Next")
                }
            }

            Spacer(modifier = Modifier.size(64.dp))

        }

    }

}


// Whether the artist-photo backdrop lookup should be skipped for this
// track: either the user prefers the album cover, or the artist string
// looks like a multi-artist credit (e.g. "Pete Namlook / Tetsu Inoue",
// "Kronos Quartet & Mogwai"). Those rarely have a real photo on Lyrion or
// Jellyfin, which tend to silently fall back to the album cover anyway -
// so we skip straight to that instead of making the lookup at all.
private fun skipArtistBackdrop(artistName: String): Boolean {
    val style = AppConfig.current.backgroundStyle
    if (style == SettingsRepository.BACKGROUND_STYLE_ALBUM ||
        style == SettingsRepository.BACKGROUND_STYLE_STARFIELD) {
        return true
    }
    return artistName.contains("/") || artistName.contains("&")
}

fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%d:%02d".format(minutes, secs)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GetAlbumArt(url: String){

    var _url by remember { mutableStateOf(url) }
    _url = url

    // No coverid to work with at all (e.g. a stale pre-rescan queue entry,
    // or a remote stream) - show a placeholder instead of an empty box so
    // it's clear nothing is broken, there's just no art for this track.
    if (_url.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = "Album Art",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(64.dp)
            )
        }
        return
    }

    val imageLoaderLMS = ImageLoader.Builder(LocalContext.current)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                .build()
        }
        .logger(DebugLogger())
        .build()

    val imageRequestLMS = ImageRequest.Builder(LocalContext.current)
        .data(_url)
        .crossfade(500)
        .build()

    AsyncImage(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        contentScale = ContentScale.Crop,
        imageLoader = imageLoaderLMS,
        model = imageRequestLMS,
        contentDescription = "Album Art",
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GetArtistArt(
    jellyfinUrl: String,
    lmsUrl: String,
    defaultUrl: String,
    content: @Composable () -> Unit
) {

    var _jellyfinUrl by remember { mutableStateOf(jellyfinUrl) }
    var _lmsUrl by remember { mutableStateOf(lmsUrl) }
    var _defaultUrl by remember { mutableStateOf(defaultUrl) }
    var _pn by remember { mutableStateOf(LMS.playerName) }
    _pn = LMS.playerName
    _jellyfinUrl = jellyfinUrl
    _lmsUrl = lmsUrl
    _defaultUrl = defaultUrl

    // Decorative "screensaver" style backdrop, chosen instead of any real
    // artist/album artwork - skip all the image-loading logic below entirely.
    if (AppConfig.current.backgroundStyle == SettingsRepository.BACKGROUND_STYLE_STARFIELD) {
        Box(modifier = Modifier.fillMaxSize()) {
            Starfield(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayerSelector(_pn)
                Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
                content()
            }
        }
        return
    }

    val contrast = 0.40f
    val brightness = -10f
    val grayscale = AppConfig.current.backgroundStyle == SettingsRepository.BACKGROUND_STYLE_ARTIST_GRAYSCALE
    val colorMatrix = if (grayscale) {
        // Luminance-weighted grayscale, with the same contrast/brightness
        // dimming folded in so it matches the colored version's look.
        val lr = 0.2126f * contrast
        val lg = 0.7152f * contrast
        val lb = 0.0722f * contrast
        floatArrayOf(
            lr, lg, lb, 0f, brightness,
            lr, lg, lb, 0f, brightness,
            lr, lg, lb, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
    } else floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )

    val imageLoaderLMS = ImageLoader.Builder(LocalContext.current)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                .build()
        }
        .logger(DebugLogger())
        .build()

    val imageLoaderJellyfin = ImageLoader.Builder(LocalContext.current)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor{chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                        .header("Authorization", "MediaBrowser Client=\"LMS Jellyfin\", Device=\"my-script\", DeviceId=\"0.0.0\", Version=\"0.0.0\", Token=\"${JellyfinApi.APITOKEN}\"")
                    val request = requestBuilder.build()
                    chain.proceed(request)
                }
                .build()
        }
        .logger(DebugLogger())
        .build()

    val hasJellyfinToken = !JellyfinApi.APITOKEN.isNullOrEmpty()

    // Three-tier fallback: try Jellyfin first (Lyrion's own portraitId isn't a
    // reliable "has a real photo" signal - it can point at Lyrion's own
    // generic/no-photo artwork), then Lyrion's portrait, then finally the
    // blurred album cover. Each tier is skipped automatically if its URL
    // isn't available, and advances on a real load failure (via listener).
    val jellyfinAttempt = _jellyfinUrl.takeIf { it.isNotEmpty() && hasJellyfinToken }
    val lmsAttempt = _lmsUrl.takeIf { it.isNotEmpty() }

    // Nothing at all to show for the backdrop (e.g. a stale pre-rescan
    // queue entry, or a remote stream with no art) - render a plain dark
    // background with a faint icon instead of trying to paint an empty URL.
    if (jellyfinAttempt == null && lmsAttempt == null && _defaultUrl.isBlank()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp)
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayerSelector(_pn)
                Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
                content()
            }
        }
        return
    }

    var tier by remember { mutableStateOf(0) }
    LaunchedEffect(_jellyfinUrl, _lmsUrl) { tier = 0 }

    val effectiveTier = when {
        tier <= 0 && jellyfinAttempt != null -> 0
        tier <= 1 && lmsAttempt != null -> 1
        else -> 2
    }

    val effectiveUrl = when (effectiveTier) {
        0 -> jellyfinAttempt!!
        1 -> lmsAttempt!!
        else -> _defaultUrl
    }
    val effectiveLoader = if (effectiveTier == 0) imageLoaderJellyfin else imageLoaderLMS
    val useBlur = effectiveTier == 2

    val painter = rememberAsyncImagePainter(
        imageLoader = effectiveLoader,
        model = ImageRequest.Builder(LocalContext.current)
            .data(effectiveUrl)
            .crossfade(500)
            .apply {
                if (useBlur) {
                    transformations(BlurTransformation(LocalContext.current, 25f, 1f))
                }
            }
            .listener(onError = { _, _ ->
                if (effectiveTier < 2) tier = effectiveTier + 1
            })
            .build(),
        contentScale = ContentScale.FillBounds,
    )


    Column(modifier = Modifier
        .paint(
            painter,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        ),horizontalAlignment = Alignment.CenterHorizontally){

        PlayerSelector(_pn)

        Spacer(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        )
        content()
    }

}