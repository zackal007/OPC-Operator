package com.androidkit.app.opc.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.ui.components.AppCard
import com.androidkit.app.ui.components.CardTitle
import com.androidkit.app.ui.components.PrimaryButton
import com.androidkit.app.ui.components.SimpleTextField
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 设置页 —— GitHub token、关注的仓库、自媒体快照仓库。
 *
 * token 只写进**本地设置文件**，不进仓库、不进 BuildConfig。
 */
@Composable
fun SettingsPage(state: OpcState) {
    var token by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf("") }
    var mediaRepo by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val saveText = stringResource(Res.string.opc_save)
    val savedText = stringResource(Res.string.opc_saved)

    LaunchedEffect(Unit) {
        val current = state.hub.currentSettings()
        token = current.githubToken
        repos = current.repos.joinToString(", ")
        mediaRepo = current.mediaSyncRepo
        period = current.period
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(
                icon = Icons.Outlined.Settings,
                title = stringResource(Res.string.opc_settings_title),
            )
            SimpleTextField(
                value = token,
                onValueChange = { token = it },
                label = stringResource(Res.string.opc_github_token_label),
                modifier = Modifier.padding(top = 8.dp),
            )
            SimpleTextField(
                value = repos,
                onValueChange = { repos = it },
                label = stringResource(Res.string.opc_repos),
                placeholder = "owner/repo,owner/repo",
                modifier = Modifier.padding(top = 8.dp),
            )
            SimpleTextField(
                value = mediaRepo,
                onValueChange = { mediaRepo = it },
                label = stringResource(Res.string.opc_media_sync_repo),
                placeholder = "owner/repo",
                modifier = Modifier.padding(top = 8.dp),
            )
            SimpleTextField(
                value = period,
                onValueChange = { period = it },
                label = stringResource(Res.string.opc_period),
                placeholder = "2026Q4",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(Res.string.opc_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            PrimaryButton(
                text = saveText,
                onClick = {
                    scope.launch {
                        state.hub.updateSettings { current ->
                            current.copy(
                                githubToken = token.trim(),
                                repos = repos.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                mediaSyncRepo = mediaRepo.trim(),
                                period = period.trim(),
                            )
                        }
                        state.reloadSettings()
                        state.say(savedText)
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
