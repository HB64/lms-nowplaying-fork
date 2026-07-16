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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import com.example.lmsnowplaying.helpers.HandleButton
import com.example.lmsnowplaying.network.logitechmediaserver.BasicAuthInterceptor
import com.example.lmsnowplaying.network.jellyfin.JellyfinApi
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
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
    var artistArtUrl by remember { mutableStateOf("")}
    var artistDefaultArtUrl by remember { mutableStateOf("")}
    var albumArtUrl by remember { mutableStateOf("")}
    var artistArtIsLms by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(LMS.isPlaying) }
    var elapsedTime by remember { mutableStateOf(LMS.elapsedTime) }
    var trackDuration by remember { mutableStateOf(LMS.trackDuration) }
    var currentSong = ""

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
                    if(_songName == "" || _songName == currentSong) return@launch
                    currentSong = _songName
                    songName = _songName
                    artistName = _artistName
                    println("Refreshing")
                    if (_coverId == ""){
                        albumArtUrl = ""
                    }else{
                        albumArtUrl = "${BuildConfig.LMS_URL}/music/$_coverId/cover.jpg"
                        artistDefaultArtUrl = "${BuildConfig.LMS_URL}/music/$_coverId/cover.jpg"

                        if (_portraitId != "") {
                            artistArtUrl = "${BuildConfig.LMS_URL}/contributor/$_portraitId/image"
                            artistArtIsLms = true
                        } else {
                            var tempName = _artistName
                            tempName = tempName.replace(" ", "%20").replace("&", "%26")

                            if(!_artistName.contains("[!\"#$%'()*+,-./:;\\\\<=>?@\\[\\]^_`{|}~]".toRegex())){
                                artistArtUrl = "${BuildConfig.JELLYFIN_URL}/Artists/$tempName/Images/Backdrop/0"
                            }else{
                                val jfUrl = JellyfinApi.GetArtistUrl(_artistName)
                                if (jfUrl != null) {
                                    artistArtUrl = jfUrl
                                }
                            }
                            artistArtIsLms = false
                        }
                    }
                }
            }
            delay(2000)
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier
        .background(Color.Black)
        .fillMaxSize()) {


        GetArtistArt(artistArtUrl, artistDefaultArtUrl, artistArtIsLms){


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

                    Text(artistName, color = Color.White, maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
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
                TextButton(
                    onClick = {
                        if (LMS.playerMac != "00:00:00:00:00:00") {
                            val newPlaying = !playing
                            playing = newPlaying
                            CoroutineScope(Dispatchers.IO).launch {
                                LMS.playPause(newPlaying)
                            }
                        }
                    },
                    interactionSource = playInteractionSource,
                    modifier = Modifier
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


fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%d:%02d".format(minutes, secs)
}

@Composable
fun GetAlbumArt(url: String){

    var _url by remember { mutableStateOf(url) }
    _url = url

    val imageLoaderLMS = ImageLoader.Builder(LocalContext.current)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(BuildConfig.LMS_USERNAME,BuildConfig.LMS_PASSWORD))
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

@Composable
fun GetArtistArt(
    url: String,
    defaultUrl: String,
    isLmsArt: Boolean,
    content: @Composable () -> Unit
) {

    var _url by remember { mutableStateOf(url) }
    var _defaultUrl by remember { mutableStateOf(defaultUrl) }
    var _pn by remember { mutableStateOf(LMS.playerName) }
    _pn = LMS.playerName
    _url = url
    _defaultUrl = defaultUrl

    val contrast = 0.40f
    val brightness = -10f
    val colorMatrix = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )

    val imageLoaderLMS = ImageLoader.Builder(LocalContext.current)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(BuildConfig.LMS_USERNAME,BuildConfig.LMS_PASSWORD))
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

    val defaultPainter = rememberAsyncImagePainter(
        imageLoader = imageLoaderLMS,
        model = ImageRequest.Builder(LocalContext.current)
            .data(_defaultUrl)
            .crossfade(500)
            .transformations(BlurTransformation(LocalContext.current, 25f, 1f))
            .build(),
        contentScale = ContentScale.FillBounds,
    )

    val hasJellyfinToken = !JellyfinApi.APITOKEN.isNullOrEmpty()
    val canUseUrl = _url != "" && (isLmsArt || hasJellyfinToken)

    val painter = if (canUseUrl) {
        val loaderToUse = if (isLmsArt) imageLoaderLMS else imageLoaderJellyfin
        rememberAsyncImagePainter(
            imageLoader = loaderToUse,
            model = ImageRequest.Builder(LocalContext.current)
                .data(_url)
                .crossfade(500)
                .build(),
            contentScale = ContentScale.FillBounds,
            error = defaultPainter
        )
    } else {
        defaultPainter
    }


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