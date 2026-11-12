package com.androidkit.app.opc.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidkit.app.opc.data.ContentItem
import com.androidkit.app.opc.data.MediaAccount
import com.androidkit.app.opc.data.MediaMetric
import com.androidkit.app.opc.data.MediaStats
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.opc.ui.components.OpcField
import com.androidkit.app.opc.ui.components.OpcFormDialog
import com.androidkit.app.opc.ui.components.Sparkline
import com.androidkit.app.ui.components.AppCard
import com.androidkit.app.ui.components.AppListItem
import com.androidkit.app.ui.components.CardTitle
import com.androidkit.app.ui.components.FilterChip
import com.androidkit.app.ui.components.IllustratedEmptyState
import com.androidkit.app.ui.components.SecondaryButton
import com.androidkit.app.ui.components.StatItemData
import com.androidkit.app.ui.components.StatsCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 自媒体页的统计窗口：近 30 天。 */
private const val WINDOW_DAYS = 30

/**
 * 自媒体页 —— 账号、数字、趋势、内容计划。
 *
 * 数据有两个来源：手动录的和采集脚本同步的，写进同一张表（见 MediaStore）。
 */
@Composable
fun MediaPage(state: OpcState) {
    var accounts by remember { mutableStateOf<List<MediaAccount>>(emptyList()) }
    var metrics by remember { mutableStateOf<List<MediaMetric>>(emptyList()) }
    var contents by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var stats by remember { mutableStateOf(MediaStats()) }
    var accountIndex by remember { mutableIntStateOf(0) }
    var showAccount by remember { mutableStateOf(false) }
    var showMetric by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.refreshTick) {
        val hub = state.hub
        accounts = hub.media.listAccounts()
        metrics = hub.media.listMetrics()
        contents = hub.media.listContents()
        stats = hub.media.stats(metrics, contents, WINDOW_DAYS)
    }

    val selectedAccount = accounts.getOrNull(accountIndex - 1)
    val scopedMetrics = if (selectedAccount == null) metrics else metrics.filter { it.accountId == selectedAccount.id }
    val scopedContents = if (selectedAccount == null) contents else contents.filter { it.accountId == selectedAccount.id }
    val scopedStats = state.hub.media.stats(scopedMetrics, scopedContents, WINDOW_DAYS)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                title = stringResource(Res.string.opc_all_accounts),
                isSelected = accountIndex == 0,
                onClick = { accountIndex = 0 },
            )
            accounts.forEachIndexed { index, account ->
                FilterChip(
                    title = account.name,
                    isSelected = accountIndex == index + 1,
                    onClick = { accountIndex = index + 1 },
                )
            }
        }

        if (accounts.isEmpty()) {
            IllustratedEmptyState(
                icon = Icons.Outlined.Campaign,
                title = stringResource(Res.string.opc_empty_media_title),
                subtitle = stringResource(Res.string.opc_empty_media_sub),
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StatsCard(
                        stats = listOf(
                            StatItemData(
                                title = stringResource(Res.string.opc_followers),
                                value = scopedStats.followers.toString(),
                                icon = Icons.Outlined.Campaign,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                            StatItemData(
                                title = stringResource(Res.string.opc_views),
                                value = scopedStats.views.toString(),
                                icon = Icons.Outlined.Campaign,
                                color = MaterialTheme.colorScheme.secondary,
                            ),
                            StatItemData(
                                title = stringResource(Res.string.opc_interactions),
                                value = scopedStats.interactions.toString(),
                                icon = Icons.Outlined.Campaign,
                                color = MaterialTheme.colorScheme.tertiary,
                            ),
                            StatItemData(
                                title = stringResource(Res.string.opc_revenue),
                                value = scopedStats.revenue.toString(),
                                icon = Icons.Outlined.Campaign,
                                color = MaterialTheme.colorScheme.error,
                            ),
                        ),
                    )
                }

                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        CardTitle(
                            icon = Icons.Outlined.Campaign,
                            title = "${stringResource(Res.string.opc_followers)} · ${stringResource(Res.string.opc_week)}",
                        )
                        Sparkline(
                            values = scopedMetrics.takeLast(WINDOW_DAYS).map { it.followers.toFloat() },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        CardTitle(icon = Icons.Outlined.Campaign, title = stringResource(Res.string.opc_content_plan))
                        if (scopedContents.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.opc_no_data),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            scopedContents.take(20).forEach { content ->
                                AppListItem(
                                    headlineText = content.title,
                                    supportingText = state.hub.media.format(content),
                                    trailingText = if (content.status == ContentItem.STATUS_PUBLISHED) {
                                        content.views.toString()
                                    } else {
                                        stringResource(Res.string.opc_planned)
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryButton(
                            text = stringResource(Res.string.opc_add_account),
                            onClick = { showAccount = true },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = stringResource(Res.string.opc_add_metric),
                            onClick = { showMetric = true },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = stringResource(Res.string.opc_add_content),
                            onClick = { showContent = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showAccount) {
        OpcFormDialog(
            title = stringResource(Res.string.opc_add_account),
            fields = listOf(
                OpcField("platform", stringResource(Res.string.opc_platform)),
                OpcField("name", stringResource(Res.string.opc_account_name)),
                OpcField("handle", stringResource(Res.string.opc_handle)),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val message = state.hub.media.upsertAccount(
                        platform = values["platform"].orEmpty(),
                        name = values["name"].orEmpty(),
                        handle = values["handle"].orEmpty(),
                    )
                    state.say(message)
                    state.refresh()
                }
                showAccount = false
            },
            onDismiss = { showAccount = false },
        )
    }

    if (showMetric) {
        OpcFormDialog(
            title = stringResource(Res.string.opc_add_metric),
            fields = listOf(
                OpcField("accountId", stringResource(Res.string.opc_account_name), selectedAccount?.id.orEmpty()),
                OpcField("date", stringResource(Res.string.opc_date), state.hub.today()),
                OpcField("followers", stringResource(Res.string.opc_followers), "0", numeric = true),
                OpcField("views", stringResource(Res.string.opc_views), "0", numeric = true),
                OpcField("interactions", stringResource(Res.string.opc_interactions), "0", numeric = true),
                OpcField("newFollowers", stringResource(Res.string.opc_new_followers), "0", numeric = true),
                OpcField("revenue", stringResource(Res.string.opc_revenue), "0", numeric = true),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val message = state.hub.media.recordMetric(
                        accountId = values["accountId"].orEmpty(),
                        date = values["date"].orEmpty(),
                        followers = values["followers"].orEmpty().toLongOrNull() ?: 0L,
                        views = values["views"].orEmpty().toLongOrNull() ?: 0L,
                        interactions = values["interactions"].orEmpty().toLongOrNull() ?: 0L,
                        newFollowers = values["newFollowers"].orEmpty().toLongOrNull() ?: 0L,
                        revenue = values["revenue"].orEmpty().toDoubleOrNull() ?: 0.0,
                    )
                    state.say(message)
                    state.refresh()
                }
                showMetric = false
            },
            onDismiss = { showMetric = false },
        )
    }

    if (showContent) {
        OpcFormDialog(
            title = stringResource(Res.string.opc_add_content),
            fields = listOf(
                OpcField("accountId", stringResource(Res.string.opc_account_name), selectedAccount?.id.orEmpty()),
                OpcField("title", stringResource(Res.string.opc_content_title)),
                OpcField("date", stringResource(Res.string.opc_date), state.hub.today()),
                OpcField("url", "URL"),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val date = values["date"].orEmpty()
                    val message = state.hub.media.upsertContent(
                        accountId = values["accountId"].orEmpty(),
                        title = values["title"].orEmpty(),
                        publishedAt = date,
                        url = values["url"].orEmpty(),
                        status = ContentItem.STATUS_PUBLISHED,
                    )
                    state.say(message)
                    state.refresh()
                }
                showContent = false
            },
            onDismiss = { showContent = false },
        )
    }
}
