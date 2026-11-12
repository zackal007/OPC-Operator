package com.androidkit.app.opc.ui

import com.androidkit.app.data.agent.ToolApproval
import com.androidkit.app.data.agent.ToolApprover
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * UI 侧的审批闸门：agent 想干危险动作时，弹卡等用户点头。
 *
 * 危险动作 = 删数据（删账号 / 删目标）。其他工具直接放行。
 *
 * agent 的工具执行在后台协程里，所以这里用 [CompletableDeferred] 把那次调用挂起，
 * 等用户在卡上给个答案。
 */
class OpcApprover(
    private val needsApproval: Set<String> = DEFAULT_NEEDS_APPROVAL,
) : ToolApprover {

    /** 一次待确认的请求。 */
    data class Request(val tool: String, val args: String, val answer: CompletableDeferred<ToolApproval>)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    override suspend fun approve(name: String, input: JsonObject): ToolApproval {
        if (name !in needsApproval) return ToolApproval.Allow
        val answer = CompletableDeferred<ToolApproval>()
        _pending.value = Request(name, input.toString(), answer)
        return try {
            answer.await()
        } finally {
            _pending.value = null
        }
    }

    /** 用户给了答案。 */
    fun resolve(decision: ToolApproval) {
        _pending.value?.answer?.complete(decision)
        _pending.value = null
    }

    companion object {
        /** 需要用户点头的工具。 */
        val DEFAULT_NEEDS_APPROVAL = setOf("delete_media_account", "delete_goal")
    }
}
