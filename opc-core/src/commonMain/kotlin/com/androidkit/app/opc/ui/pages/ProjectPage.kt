package com.androidkit.app.opc.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidkit.app.opc.data.Commit
import com.androidkit.app.opc.data.Project
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.ui.components.AppBadge
import com.androidkit.app.ui.components.AppCard
import com.androidkit.app.ui.components.CardTitle
import com.androidkit.app.ui.components.FilterChip
import com.androidkit.app.ui.components.IllustratedEmptyState
import org.jetbrains.compose.resources.stringResource

/**
 * 项目页 —— 哪些项目、最近提交怎么样。
 *
 * 数据来自 GitHub 同步后缓存在本地的仓库和提交。
 */
@Composable
fun ProjectPage(state: OpcState) {
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var commits by remember { mutableStateOf<List<Commit>>(emptyList()) }
    var sortIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.refreshTick) {
        projects = state.hub.projects.listProjects()
        commits = state.hub.projects.listCommits()
    }

    if (projects.isEmpty()) {
        IllustratedEmptyState(
            icon = Icons.Outlined.Code,
            title = stringResource(Res.string.opc_empty_projects_title),
            subtitle = stringResource(Res.string.opc_empty_projects_sub),
            modifier = Modifier.fillMaxSize().padding(24.dp),
        )
        return
    }

    val since = state.hub.sinceIso(WEEK_DAYS)
    val sortOptions = listOf(
        stringResource(Res.string.opc_sort_recent),
        stringResource(Res.string.opc_sort_commits),
        stringResource(Res.string.opc_sort_stars),
    )
    val ranked = when (sortIndex) {
        1 -> projects.sortedByDescending { state.hub.projects.commitCount(commits, it.id, since) }
        2 -> projects.sortedByDescending { it.stars }
        else -> projects.sortedByDescending { it.lastPushAt }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sortOptions.forEachIndexed { index, title ->
                FilterChip(
                    title = title,
                    isSelected = index == sortIndex,
                    onClick = { sortIndex = index },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ranked.size) { index ->
                val project = ranked[index]
                val projectCommits = commits.filter { it.projectId == project.id }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle(icon = Icons.Outlined.Code, title = project.fullName())
                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppBadge(text = "${stringResource(Res.string.opc_commits_count)} ${state.hub.projects.commitCount(commits, project.id, since)}")
                        if (project.language.isNotBlank()) AppBadge(text = project.language)
                        if (project.stars > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = stringResource(Res.string.opc_stars),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = project.stars.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                        }
                    }
                    if (project.openPulls > 0 || project.openIssues > 0) {
                        Text(
                            text = "${stringResource(Res.string.opc_open_prs)} ${project.openPulls} · " +
                                "${stringResource(Res.string.opc_open_issues)} ${project.openIssues}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    val shown = projectCommits.take(5)
                    if (shown.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.opc_recent_commits),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        shown.forEach { commit ->
                            Text(
                                text = state.hub.projects.format(commit),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 项目页的统计窗口（跟首页一致：近 7 天）。 */
private const val WEEK_DAYS = 7
