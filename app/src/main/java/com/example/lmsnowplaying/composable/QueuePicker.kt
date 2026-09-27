package com.example.lmsnowplaying.composable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lmsnowplaying.network.logitechmediaserver.LMS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Button next to the "next track" preview that replaces it - mirrors
// SugarCube's/RandomFlow's own "Replace This Track" button (whichever is
// picked in Settings): tries that plugin's music-similarity pick first, and
// silently falls back to a random library track if it isn't installed/
// enabled or didn't change anything.
@Composable
fun QueueButton() {

    val contextForToast = LocalContext.current.applicationContext

    val buttonInteractionSource = remember { MutableInteractionSource() }
    val buttonFocused by buttonInteractionSource.collectIsFocusedAsState()
    val buttonFocusRequester = remember { FocusRequester() }

    IconButton(
        onClick = {
            val mac = LMS.playerMac
            CoroutineScope(Dispatchers.IO).launch {
                val result = LMS.replaceNextSmart(mac)
                val message = when (result) {
                    LMS.ReplaceResult.SUGARCUBE -> " Volgende vervangen via SugarCube"
                    LMS.ReplaceResult.RANDOMFLOW -> " Volgende vervangen via RandomFlow"
                    LMS.ReplaceResult.DSTM -> " Volgende vervangen via Don't Stop The Music"
                    LMS.ReplaceResult.RANDOM -> " Volgende track vervangen"
                    LMS.ReplaceResult.SKIPPED_QUEUE -> " Er staat een album/batch in de wachtrij, niet vervangen"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(contextForToast, message, Toast.LENGTH_SHORT).show()
                }
            }
        },
        interactionSource = buttonInteractionSource,
        modifier = Modifier
            .focusRequester(buttonFocusRequester)
            .clip(CircleShape)
            .background(if (buttonFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Vervang volgende track",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}
