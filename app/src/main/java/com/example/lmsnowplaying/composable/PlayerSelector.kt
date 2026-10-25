package com.example.lmsnowplaying.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.network.logitechmediaserver.LMS

@Composable
fun PlayerSelector(pn: String){

    val _playerName by LMS.playerNameFlow.collectAsState()

    //println("LMS PLAYER NAME: " + LMS.playerName)

    //modifier = Modifier.width(800.dp).padding(start = 80.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End
    // zIndex: the floating ⋮ menu must be drawn above the rest of the screen
    Row(modifier = Modifier.zIndex(10f).width(800.dp).padding(start = 80.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End)
    {

        Surface(color = Color.Transparent) {
            Text(text = _playerName, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        MyUI()
    }

}



