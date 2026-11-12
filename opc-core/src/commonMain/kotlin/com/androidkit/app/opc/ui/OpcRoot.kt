package com.androidkit.app.opc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.androidkit.app.opc.opc.OpcAgentConfig
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.pages.AssistantPage
import com.androidkit.app.opc.ui.pages.DashboardPage
import com.androidkit.app.opc.ui.pages.GoalPage
import com.androidkit.app.opc.ui.pages.MediaPage
import com.androidkit.app.opc.ui.pages.ProjectPage
import com.androidkit.app.opc.ui.pages.SettingsPage
import com.androidkit.app.ui.components.AppBottomNavigation
import com.androidkit.app.ui.components.BottomNavItem
import com.androidkit.app.ui.components.Header
import com.androidkit.app.ui.shell.AppShell
import org.jetbrains.compose.resources.stringResource

/**
 * 操作台的根页面 —— 底栏 5 个页签 + 设置页。
 *
 * 页面骨架（系统栏 / 键盘 / 底栏避让）走 kit 的 [AppShell]，
 * 业务页面自己只管内容。
 */
@Composable
fun OpcRoot(
    state: OpcState,
    agentConfig: OpcAgentConfig?,
) {
    var selected by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 做完一个动作 → 顶栏弹一句结果（用的是 data 层返回的那句话）
    LaunchedEffect(state.status) {
        if (state.status.isNotBlank()) {
            snackbarHostState.showSnackbar(state.status)
            state.say("")
        }
    }

    // 冷启动同步一次（配了 token 才同步）；页面各自也会在 refreshTick 变化时重读
    LaunchedEffect(Unit) {
        state.reloadSettings()
        if (state.settings.githubToken.isNotBlank()) state.sync()
    }

    val navItems = listOf(
        BottomNavItem(stringResource(Res.string.opc_tab_dashboard), Icons.Outlined.Dashboard),
        BottomNavItem(stringResource(Res.string.opc_tab_media), Icons.Outlined.Campaign),
        BottomNavItem(stringResource(Res.string.opc_tab_projects), Icons.Outlined.Code),
        BottomNavItem(stringResource(Res.string.opc_tab_goals), Icons.Outlined.Flag),
        BottomNavItem(stringResource(Res.string.opc_tab_assistant), Icons.Outlined.ChatBubbleOutline),
    )

    AppShell(
        topBar = {
            Header(
                title = stringResource(
                    if (showSettings) Res.string.opc_settings_title else Res.string.opc_title,
                ),
                leading1 = if (showSettings) {
                    {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(Res.string.opc_back),
                            )
                        }
                    }
                } else {
                    null
                },
                action1 = if (showSettings) {
                    null
                } else {
                    {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(Res.string.opc_settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                AppBottomNavigation(
                    items = navItems,
                    selectedIndex = selected,
                    onItemSelected = { selected = it },
                )
            }
        },
        snackbarHostState = snackbarHostState,
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                showSettings -> SettingsPage(state)
                selected == 0 -> DashboardPage(state)
                selected == 1 -> MediaPage(state)
                selected == 2 -> ProjectPage(state)
                selected == 3 -> GoalPage(state)
                else -> AssistantPage(state, agentConfig)
            }
        }
    }
}
