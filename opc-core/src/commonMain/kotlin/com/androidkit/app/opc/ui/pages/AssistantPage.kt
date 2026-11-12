package com.androidkit.app.opc.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidkit.app.data.agent.AgentSession
import com.androidkit.app.data.agent.ChatLog
import com.androidkit.app.data.agent.KoogAgentRuntime
import com.androidkit.app.data.agent.ToolApproval
import com.androidkit.app.opc.opc.OpcAgentConfig
import com.androidkit.app.opc.opc.opcSkill
import com.androidkit.app.opc.res.*
import com.androidkit.app.opc.ui.OpcApprover
import com.androidkit.app.opc.ui.OpcState
import com.androidkit.app.ui.agent.ChatMessageList
import com.androidkit.app.ui.agent.ToolApprovalCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 助手页 —— 问一句，操作台答一句。
 *
 * agent 跑在**本机**：模型走云端，工具摸本机数据（见 rules/CLAUDE.template.md）。
 * 危险动作（删账号 / 删目标）会先弹确认卡。
 */
@Composable
fun AssistantPage(
    state: OpcState,
    agentConfig: OpcAgentConfig?,
) {
    val approver = remember { OpcApprover() }
    val session = remember(state.hub, agentConfig) { createSession(state.hub, agentConfig, approver) }
    val log = remember { ChatLog() }

    val messages by log.messages.collectAsState()
    val pending by approver.pending.collectAsState()

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ChatMessageList(
            messages = messages,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (session == null) {
            Text(
                text = stringResource(Res.string.opc_no_api_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        pending?.let { request ->
            ToolApprovalCard(
                tool = request.tool,
                args = request.args,
                risk = "删除后无法恢复",
                onAllowOnce = { approver.resolve(ToolApproval.Allow) },
                onAllowAlways = { approver.resolve(ToolApproval.AllowAlways) },
                onDeny = { approver.resolve(ToolApproval.Deny) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Row {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(Res.string.opc_ask_placeholder)) },
                enabled = !busy,
                maxLines = 4,
            )
            Button(
                enabled = !busy && input.isNotBlank() && session != null,
                onClick = {
                    val activeSession = session ?: return@Button
                    val text = input.trim()
                    input = ""
                    log.user(text)
                    busy = true
                    scope.launch {
                        activeSession.send(text).collect { event -> log.onEvent(event) }
                        busy = false
                        state.refresh()
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(Res.string.opc_send)) }
        }
    }
}

/** 从配置建会话。没 key 就返回 null，UI 会提示怎么配。 */
private fun createSession(
    hub: com.androidkit.app.opc.data.OpcHub,
    config: OpcAgentConfig?,
    approver: OpcApprover,
): AgentSession? {
    val key = config?.apiKey.orEmpty()
    if (key.isBlank()) return null
    val runtime = KoogAgentRuntime(key, config?.baseUrl ?: "https://api.anthropic.com")
    return runtime.session(opcSkill(hub, config?.model ?: "claude-opus-5-5"), approver)
}
