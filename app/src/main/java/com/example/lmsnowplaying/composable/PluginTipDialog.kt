package com.example.lmsnowplaying.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lmsnowplaying.R

// One-time onboarding tip shown on the very first launch, pointing new users
// at SugarCube/RandomFlow and Don't Stop The Music - the "replace next
// track" button works best with one of those installed, but is fully usable
// without them too.
@Composable
fun PluginTipDialog(onDismiss: () -> Unit) {

    val okInteractionSource = remember { MutableInteractionSource() }
    val okFocused by okInteractionSource.collectIsFocusedAsState()
    val okFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        okFocusRequester.requestFocus()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (event.key == Key.Back || event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xEE222222))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.plugin_tip_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.plugin_tip_body),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(
                onClick = onDismiss,
                interactionSource = okInteractionSource,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .focusRequester(okFocusRequester)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (okFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
            ) {
                Text(
                    text = stringResource(R.string.plugin_tip_ok),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
