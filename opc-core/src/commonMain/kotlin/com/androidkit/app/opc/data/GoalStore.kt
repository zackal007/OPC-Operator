package com.androidkit.app.opc.data

import com.androidkit.app.data.storage.EntityStore
import com.androidkit.app.data.storage.StoredEntity
import com.androidkit.app.data.storage.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 一个关键结果（KR）。 */
@Serializable
data class KeyResult(
    val id: String,
    val title: String,
    val unit: String = "",
    val target: Double = 0.0,
    val current: Double = 0.0,
) {
    /** 完成度，封顶 1.0；没写目标就按 0/1 二值算。 */
    fun progress(): Double = when {
        target <= 0.0 -> if (current > 0.0) 1.0 else 0.0
        else -> (current / target).coerceIn(0.0, 1.0)
    }

    /** 「12 / 30 万」这种写法 —— 只有这一处。 */
    fun format(): String {
        val t = trimNumber(target)
        val c = trimNumber(current)
        return if (unit.isBlank()) "$c / $t" else "$c / $t $unit"
    }
}

/** 一个目标（一个周期内的 OKR）。 */
data class Goal(
    val id: String,
    val title: String,
    val period: String = "",
    val owner: String = "",
    val note: String = "",
    val linkedProject: String = "",
    val keyResults: List<KeyResult> = emptyList(),
) {
    /** 目标进度 = 各 KR 完成度的平均。 */
    fun progress(): Double {
        if (keyResults.isEmpty()) return 0.0
        return keyResults.map { it.progress() }.average()
    }

    /** 进度百分比（0-100 的整数）。 */
    fun progressPercent(): Int = (progress() * 100).toInt()
}

/**
 * 目标数据层。
 *
 * KR 是嵌套结构，塞进 kit 的扁平字段里 —— 用一段 JSON 字符串存（`keyResults` 字段）。
 * 以后真要按 KR 查询，再上 Room；现在几千条目标，文件够了。
 */
class GoalStore(private val store: EntityStore) {

    suspend fun listGoals(period: String? = null): List<Goal> =
        store.loadAll(SUBDIR_GOALS)
            .mapNotNull { it.toGoal() }
            .filter { period == null || it.period == period }
            .sortedByDescending { it.period }

    suspend fun findGoal(id: String): Goal? = store.read(SUBDIR_GOALS, id)?.toGoal()

    /** 新增或更新一个目标。 */
    suspend fun upsertGoal(
        title: String,
        period: String,
        owner: String = "",
        note: String = "",
        linkedProject: String = "",
        keyResults: List<KeyResult> = emptyList(),
        id: String = "",
    ): String {
        val t = title.trim()
        if (t.isEmpty()) return "没保存：目标标题不能为空"
        val goalId = id.ifBlank { goalId(period, t) }
        val goal = Goal(
            id = goalId,
            title = t,
            period = period.trim(),
            owner = owner.trim(),
            note = note.trim(),
            linkedProject = linkedProject.trim(),
            keyResults = keyResults,
        )
        store.write(SUBDIR_GOALS, goal.toEntity())
        return "已保存目标：$t（${goal.progressPercent()}%）"
    }

    /** 删除目标。危险动作 —— agent 调用要走审批闸门。 */
    suspend fun deleteGoal(id: String): String {
        val goal = findGoal(id) ?: return "没找到目标 $id"
        store.delete(SUBDIR_GOALS, id)
        return "已删除目标：${goal.title}"
    }

    /** 更新某个 KR 的当前值 —— 「目标板块」的进度就是这么推进的。 */
    suspend fun updateKeyResult(goalId: String, krId: String, current: Double): String {
        val goal = findGoal(goalId) ?: return "没更新：找不到目标 $goalId"
        val kr = goal.keyResults.firstOrNull { it.id == krId } ?: return "没更新：目标里没有关键结果 $krId"
        val updated = goal.copy(keyResults = goal.keyResults.map { if (it.id == krId) it.copy(current = current) else it })
        store.write(SUBDIR_GOALS, updated.toEntity())
        return "已更新 ${kr.title}：${kr.format()} → ${updated.keyResults.first { it.id == krId }.format()}（${updated.progressPercent()}%）"
    }

    /** 给目标加一个关键结果。 */
    suspend fun addKeyResult(goalId: String, title: String, unit: String = "", target: Double = 0.0): String {
        val goal = findGoal(goalId) ?: return "没添加：找不到目标 $goalId"
        val t = title.trim()
        if (t.isEmpty()) return "没添加：关键结果标题不能为空"
        val kr = KeyResult(id = krId(goalId, t), title = t, unit = unit.trim(), target = target, current = 0.0)
        val updated = goal.copy(keyResults = goal.keyResults + kr)
        store.write(SUBDIR_GOALS, updated.toEntity())
        return "已添加关键结果：${kr.title}（目标 ${kr.format()}）"
    }

    /** 一个周期的总进度（各目标进度的平均）。 */
    fun periodProgress(goals: List<Goal>): Int {
        if (goals.isEmpty()) return 0
        return (goals.map { it.progress() }.average() * 100).toInt()
    }

    /** 一段给人和模型看的摘要 —— 只有这一处格式。 */
    fun summary(goals: List<Goal>, period: String): String {
        if (goals.isEmpty()) return "（$period 还没有目标）"
        val lines = goals.joinToString("；") { "${it.title}（${it.progressPercent()}%）" }
        return "$period 共 ${goals.size} 个目标，整体 ${periodProgress(goals)}%：$lines"
    }

    companion object {
        const val SUBDIR_GOALS = "goals/list"

        /** 目标 id = 周期 + 标题 —— 同一个周期里同名目标只有一条。 */
        fun goalId(period: String, title: String): String {
            val safe = title.filter { it.isLetterOrDigit() || it == '-' }.take(24)
            return "${period.ifBlank { "free" }}#$safe"
        }

        /** 关键结果 id = 目标 id + KR 名。 */
        fun krId(goalId: String, title: String): String {
            val safe = title.filter { it.isLetterOrDigit() || it == '-' }.take(24)
            return "$goalId#kr#$safe"
        }
    }
}

// ── 实体 ↔ 文件字段 ─────────────────────────────────────────

private val krJson = Json { ignoreUnknownKeys = true }

private fun Goal.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("period", JsonPrimitive(period))
        put("owner", JsonPrimitive(owner))
        put("note", JsonPrimitive(note))
        put("linkedProject", JsonPrimitive(linkedProject))
        put("keyResults", JsonPrimitive(krJson.encodeToString(ListSerializer(KeyResult.serializer()), keyResults)))
    },
)

private fun StoredEntity.toGoal(): Goal? {
    val title = fields.str("title") ?: return null
    val krs = fields.str("keyResults")
        ?.let { runCatching { krJson.decodeFromString(ListSerializer(KeyResult.serializer()), it) }.getOrNull() }
        ?: emptyList()
    return Goal(
        id = id,
        title = title,
        period = fields.str("period").orEmpty(),
        owner = fields.str("owner").orEmpty(),
        note = fields.str("note").orEmpty(),
        linkedProject = fields.str("linkedProject").orEmpty(),
        keyResults = krs,
    )
}

/** 数字显示：整数不带小数点，小数最多两位。 */
internal fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
