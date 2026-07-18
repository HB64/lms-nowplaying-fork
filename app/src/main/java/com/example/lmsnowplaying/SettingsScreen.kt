package com.example.lmsnowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lmsnowplaying.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds "http://" if the user left the scheme off (e.g. typed just an IP or
 * hostname), and strips a trailing slash so URL concatenation elsewhere
 * (e.g. "$lmsUrl/music/...") doesn't end up with a double slash.
 */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return trimmed
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    return withScheme.trimEnd('/')
}

@Preview(heightDp = 540, widthDp = 960)
@Composable
fun SettingsScreen(
    initial: SettingsRepository.Settings = SettingsRepository.Settings(),
    onSaved: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lmsUrl by remember { mutableStateOf(initial.lmsUrl) }
    var lmsUsername by remember { mutableStateOf(initial.lmsUsername) }
    var lmsPassword by remember { mutableStateOf(initial.lmsPassword) }
    var jellyfinUrl by remember { mutableStateOf(initial.jellyfinUrl) }
    var jellyfinUsername by remember { mutableStateOf(initial.jellyfinUsername) }
    var jellyfinPassword by remember { mutableStateOf(initial.jellyfinPassword) }
    var jellyfinApiKey by remember { mutableStateOf(initial.jellyfinApiKey) }

    MaterialTheme(colorScheme = darkColorScheme()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 80.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = stringResource(R.string.settings_lms_section),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = lmsUrl,
            onValueChange = { lmsUrl = it },
            label = { Text(stringResource(R.string.settings_lms_url_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = lmsUsername,
            onValueChange = { lmsUsername = it },
            label = { Text(stringResource(R.string.settings_lms_username_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = lmsPassword,
            onValueChange = { lmsPassword = it },
            label = { Text(stringResource(R.string.settings_lms_password_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.settings_jellyfin_section),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = jellyfinUrl,
            onValueChange = { jellyfinUrl = it },
            label = { Text(stringResource(R.string.settings_jellyfin_url_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = jellyfinUsername,
            onValueChange = { jellyfinUsername = it },
            label = { Text(stringResource(R.string.settings_jellyfin_username_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = jellyfinPassword,
            onValueChange = { jellyfinPassword = it },
            label = { Text(stringResource(R.string.settings_jellyfin_password_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = jellyfinApiKey,
            onValueChange = { jellyfinApiKey = it },
            label = { Text(stringResource(R.string.settings_jellyfin_apikey_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Button(
                onClick = {
                    val toSave = SettingsRepository.Settings(
                        lmsUrl = normalizeUrl(lmsUrl),
                        lmsUsername = lmsUsername.trim(),
                        lmsPassword = lmsPassword,
                        jellyfinUrl = normalizeUrl(jellyfinUrl),
                        jellyfinUsername = jellyfinUsername.trim(),
                        jellyfinPassword = jellyfinPassword,
                        jellyfinApiKey = jellyfinApiKey.trim(),
                    )
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            SettingsRepository.save(context, toSave)
                        }
                        onSaved()
                    }
                }
            ) {
                Text(
                    if (onCancel != null) stringResource(R.string.settings_save)
                    else stringResource(R.string.settings_save_and_start)
                )
            }

            if (onCancel != null) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        }
    }
    }
}
