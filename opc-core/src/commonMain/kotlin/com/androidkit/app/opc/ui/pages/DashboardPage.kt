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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Sync
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
import com.androidkit.app.opc.data.Commit
import com.androidkit.app.opc.data.Goal
import com.androidkit.app.opc.data.MediaAccount
import com.androidkit.app.opc.data.MediaMetric
import com.androidkit.app.opc.data.MediaStats
import com.androidkit.app.opc.data.Project
import com.androidkit.app.opc.data.currentPeriod
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.opc.ui.components.Sparkline
import com.androidkit.app.ui.components.AppCard
import com.androidkit.app.ui.components.CardTitle
import com.androidkit.app.ui.components.LinearProgressBar
import com.androidkit.app.ui.components.PrimaryButton
import com.androidkit.app.ui.components.StatItemData
import com.androidkit.app.ui.components.StatsCard
import com.androidkit.app.ui.components.formatRelativeTime
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 首页统计窗口：近 7 天。 */
private const val WEEK_DAYS = 7

/**
 * 操作台首页 —— 「今天该看什么」。
 *
 * 三个 stat + 三块摘要 + 同步状态。数字全部来自 data 层，
 * 摘要文案也用 data 层那一份（页面和 agent 看到的说法一致）。
 */
@Composable
fun DashboardPage(state: OpcState) {
    var accounts by remember { mutableStateOf<List<MediaAccount>>(emptyList()) }
    var metrics by remember { mutableStateOf<List<MediaMetric>>(emptyList()) }
    var stats by remember { mutableStateOf(MediaStats()) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var commits by remember { mutableStateOf<List<Commit>>(emptyList()) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    val period = currentPeriod()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.refreshTick) {
        val hub = state.hub
        accounts = hub.media.listAccounts()
        metrics = hub.media.listMetrics()
        stats = hub.media.stats(metrics, hub.media.listContents(), WEEK_DAYS)
        projects = hub.projects.listProjects()
        commits = hub.projects.listCommits()
        goals = hub.goals.listGoals(period)
    }

    val commitCount = state.hub.projects.commitCount(commits, null, state.hub.sinceIso(WEEK_DAYS))
    val goalProgress = state.hub.goals.periodProgress(goals)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatsCard(
                stats = listOf(
                    StatItemData(
                        title = stringResource(Res.string.opc_stat_followers),
                        value = stats.followers.toString(),
                        icon = Icons.Outlined.People,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    StatItemData(
                        title = stringResource(Res.string.opc_stat_commits),
                        value = commitCount.toString(),
                        icon = Icons.Outlined.Commit,
                        color = MaterialTheme.colorScheme.secondary,
                    ),
                    StatItemData(
                        title = stringResource(Res.string.opc_stat_goals),
                        value = "$goalProgress%",
                        icon = Icons.Outlined.Flag,
                        color = MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            )
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(icon = Icons.Outlined.Campaign, title = stringResource(Res.string.opc_section_media))
                Text(
                    text = state.hub.media.summary(stats, accounts, WEEK_DAYS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Sparkline(
                    values = metrics.takeLast(WEEK_DAYS).map { it.followers.toFloat() },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(icon = Icons.Outlined.Code, title = stringResource(Res.string.opc_section_projects))
                Text(
                    text = state.hub.projects.summary(projects, commits, state.hub.sinceIso(WEEK_DAYS)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val recent = commits.take(3)
                if (recent.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.opc_recent_commits),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    recent.forEach { commit ->
                        Text(
                            text = state.hub.projects.format(commit),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(icon = Icons.Outlined.Flag, title = stringResource(Res.string.opc_active_goals))
                if (goals.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.opc_empty_goals_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    goals.forEach { goal ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = goal.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${goal.progressPercent()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressBar(
                                progress = goal.progress().toFloat(),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(icon = Icons.Outlined.Sync, title = stringResource(Res.string.opc_last_sync_at))
                val lastSync = state.settings.lastSyncAt
                Text(
                    text = if (lastSync == 0L) {
                        stringResource(Res.string.opc_never_synced)
                    } else {
                        formatRelativeTime(lastSync)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val error = state.settings.lastSyncError
                if (error.isNotBlank()) {
                    Text(
                        text = "${stringResource(Res.string.opc_sync_failed)}：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                PrimaryButton(
                    text = if (state.syncing) {
                        stringResource(Res.string.opc_syncing)
                    } else {
                        stringResource(Res.string.opc_sync_now)
                    },
                    enabled = !state.syncing,
                    onClick = { scope.launch { state.sync() } },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
