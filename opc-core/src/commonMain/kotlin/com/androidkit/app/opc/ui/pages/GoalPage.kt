package com.androidkit.app.opc.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidkit.app.opc.data.Goal
import com.androidkit.app.opc.data.KeyResult
import com.androidkit.app.opc.data.currentPeriod
import com.androidkit.app.opc.data.recentPeriods
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.opc.ui.components.OpcField
import com.androidkit.app.opc.ui.components.OpcFormDialog
import com.androidkit.app.ui.components.AddButton
import com.androidkit.app.ui.components.AppCard
import com.androidkit.app.ui.components.CardTitle
import com.androidkit.app.ui.components.FilterChip
import com.androidkit.app.ui.components.IllustratedEmptyState
import com.androidkit.app.ui.components.LinearProgressBar
import com.androidkit.app.ui.components.TextActionButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 目标页 —— OKR 式里程碑 + 进度条。
 *
 * 目标 = 一条 + 若干关键结果（KR）；进度 = 各 KR 完成度的平均（data 层算）。
 */
@Composable
fun GoalPage(state: OpcState) {
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var periodIndex by remember { mutableIntStateOf(0) }
    var showNewGoal by remember { mutableStateOf(false) }
    var krTarget by remember { mutableStateOf<Pair<Goal, KeyResult>?>(null) }
    var newKrFor by remember { mutableStateOf<Goal?>(null) }
    val periods = remember(state.settings.period) {
        val recent = recentPeriods(4)
        val custom = state.settings.period.trim()
        if (custom.isNotBlank() && custom !in recent) listOf(custom) + recent else recent
    }
    val period = periods.getOrElse(periodIndex) { currentPeriod() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.refreshTick, period) {
        goals = state.hub.goals.listGoals(period)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            periods.forEachIndexed { index, value ->
                FilterChip(
                    title = value,
                    isSelected = index == periodIndex,
                    onClick = { periodIndex = index },
                )
            }
        }

        if (goals.isEmpty()) {
            IllustratedEmptyState(
                icon = Icons.Outlined.Flag,
                title = stringResource(Res.string.opc_empty_goals_title),
                subtitle = stringResource(Res.string.opc_empty_goals_sub),
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(goals.size) { index ->
                    val goal = goals[index]
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        CardTitle(icon = Icons.Outlined.Flag, title = goal.title)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearProgressBar(
                                progress = goal.progress().toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${goal.progressPercent()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        goal.keyResults.forEach { kr ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = kr.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = kr.format(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LinearProgressBar(
                                        progress = kr.progress().toFloat(),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                TextActionButton(
                                    text = stringResource(Res.string.opc_update_progress),
                                    onClick = { krTarget = goal to kr },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextActionButton(
                                text = stringResource(Res.string.opc_add_kr),
                                onClick = { newKrFor = goal },
                            )
                        }
                    }
                }
            }
        }

        AddButton(
            onClick = { showNewGoal = true },
            contentDescription = stringResource(Res.string.opc_add_goal),
            modifier = Modifier.padding(16.dp),
        )
    }

    if (showNewGoal) {
        OpcFormDialog(
            title = stringResource(Res.string.opc_add_goal),
            fields = listOf(
                OpcField("title", stringResource(Res.string.opc_goal_title)),
                OpcField("period", stringResource(Res.string.opc_period), period),
                OpcField("owner", stringResource(Res.string.opc_owner)),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val message = state.hub.goals.upsertGoal(
                        title = values["title"].orEmpty(),
                        period = values["period"].orEmpty().ifBlank { period },
                        owner = values["owner"].orEmpty(),
                    )
                    state.say(message)
                    state.refresh()
                }
                showNewGoal = false
            },
            onDismiss = { showNewGoal = false },
        )
    }

    krTarget?.let { (goal, kr) ->
        OpcFormDialog(
            title = "${stringResource(Res.string.opc_update_progress)}：${kr.title}",
            fields = listOf(
                OpcField("current", stringResource(Res.string.opc_current_value), kr.current.toString(), numeric = true),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val current = values["current"].orEmpty().toDoubleOrNull() ?: 0.0
                    state.say(state.hub.goals.updateKeyResult(goal.id, kr.id, current))
                    state.refresh()
                }
                krTarget = null
            },
            onDismiss = { krTarget = null },
        )
    }

    newKrFor?.let { goal ->
        OpcFormDialog(
            title = "${stringResource(Res.string.opc_add_kr)}：${goal.title}",
            fields = listOf(
                OpcField("title", stringResource(Res.string.opc_kr_title)),
                OpcField("unit", stringResource(Res.string.opc_unit)),
                OpcField("target", stringResource(Res.string.opc_target_value), "0", numeric = true),
            ),
            confirmText = stringResource(Res.string.opc_save),
            dismissText = stringResource(Res.string.opc_cancel),
            onConfirm = { values ->
                scope.launch {
                    val message = state.hub.goals.addKeyResult(
                        goalId = goal.id,
                        title = values["title"].orEmpty(),
                        unit = values["unit"].orEmpty(),
                        target = values["target"].orEmpty().toDoubleOrNull() ?: 0.0,
                    )
                    state.say(message)
                    state.refresh()
                }
                newKrFor = null
            },
            onDismiss = { newKrFor = null },
        )
    }
}
