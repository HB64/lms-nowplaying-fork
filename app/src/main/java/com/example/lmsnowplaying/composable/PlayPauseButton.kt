package com.example.lmsnowplaying.composable

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayCircleFilled
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayPauseButton(playing: Boolean){
    Icon(
        modifier = Modifier.size(32.dp),
        tint = Color.White,
        imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayCircleFilled,
        contentDescription = "Play Pause Button",
    )
}
