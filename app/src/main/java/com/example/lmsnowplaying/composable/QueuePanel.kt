package com.example.lmsnowplaying.composable

import com.example.lmsnowplaying.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import com.example.lmsnowplaying.network.logitechmediaserver.QueueList
import com.example.lmsnowplaying.network.logitechmediaserver.QueueTrack
import com.example.lmsnowplaying.settings.NavState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The play queue as a panel on top of Now Playing: choose a track to play it
// or remove it. Back / D-pad left goes one step back, and finally closes it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QueuePanel() {
    var queue by remember { mutableStateOf<QueueList?>(null) }
    var selected by remember { mutableStateOf<QueueTrack?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }

    // Read the queue now and keep it fresh while no track is selected
    LaunchedEffect(Unit) {
        while (true) {
            if (selected == null) queue = withContext(Dispatchers.IO) { LMS.getQueue() }
            delay(3000)
        }
    }

    // First time the list is there: scroll to the current track and focus it
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(queue != null) {
        val q = queue ?: return@LaunchedEffect
        if (positioned) return@LaunchedEffect
        positioned = true
        val cur = q.tracks.indexOfFirst { it.absoluteIndex == q.currentIndex }.coerceAtLeast(0)
        listState.scrollToItem((cur - 1).coerceAtLeast(0))
        delay(60)
        try { currentFocus.requestFocus() } catch (_: Exception) { }
    }

    // Back from the action menu to the list: focus the current track again
    LaunchedEffect(selected) {
        if (selected == null) {
            delay(60)
            try { currentFocus.requestFocus() } catch (_: Exception) { }
        } else {
            delay(60)
            try { actionFocus.requestFocus() } catch (_: Exception) { }
        }
    }

    val goBack: () -> Unit = {
        if (selected != null) selected = null else NavState.showQueue = false
    }
    BackHandler(enabled = true) { goBack() }

    Box(
        modifier = Modifier
            .width(320.dp)
            .heightIn(max = 300.dp)
            // Keep D-pad focus inside the panel while it is open
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
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
            val q = queue
            val sel = selected
            Text(
                text = sel?.title ?: if (q == null) stringResource(R.string.queue_title) else stringResource(R.string.queue_title_count, q.tracks.size),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            if (sel != null) {
                QueueRow(stringResource(R.string.action_play_now), false, Modifier.focusRequester(actionFocus)) {
                    scope.launch {
                        withContext(Dispatchers.IO) { LMS.jumpTo(LMS.playerMac, sel.absoluteIndex) }
                        selected = null
                        queue = withContext(Dispatchers.IO) { LMS.getQueue() }
                    }
                }
                QueueRow(stringResource(R.string.queue_remove), false, Modifier) {
                    scope.launch {
                        withContext(Dispatchers.IO) { LMS.removeFromQueue(sel.absoluteIndex) }
                        selected = null
                        queue = withContext(Dispatchers.IO) { LMS.getQueue() }
                    }
                }
            } else if (q == null) {
                Text(stringResource(R.string.common_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            } else if (q.tracks.isEmpty()) {
                Text(stringResource(R.string.queue_empty), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            } else {
                val imageLoader = rememberLmsImageLoader()
                LazyColumn(state = listState) {
                    itemsIndexed(q.tracks) { _, track ->
                        val isCurrent = track.absoluteIndex == q.currentIndex
                        val label = if (track.artist.isNotBlank()) "${track.title} – ${track.artist}" else track.title
                        // Streams have a made-up numeric cover id with no picture behind it
                        val realCover = track.coverId.isNotBlank() && track.coverId.toLongOrNull() == null
                        ThumbRow(
                            text = (if (isCurrent) "▶ " else "") + label,
                            imageUrl = if (realCover) coverUrl(track.coverId) else null,
                            placeholder = Icons.Outlined.MusicNote,
                            round = false,
                            imageLoader = imageLoader,
                            onClick = { selected = track },
                            modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier,
                            bold = isCurrent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(text: String, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = text,
        color = if (current) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 14.sp,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
