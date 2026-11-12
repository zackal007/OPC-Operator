package com.androidkit.app.opc.data

import com.androidkit.app.data.storage.EntityStore
import com.androidkit.app.data.storage.StoredEntity
import com.androidkit.app.data.storage.boolOr
import com.androidkit.app.data.storage.doubleOr
import com.androidkit.app.data.storage.longOr
import com.androidkit.app.data.storage.str
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 数据来源：手动录入 / 采集脚本同步。 */
const val SOURCE_MANUAL = "manual"
const val SOURCE_SYNC = "sync"

/** 一个自媒体账号（公众号、小红书、B 站……）。 */
data class MediaAccount(
    val id: String,
    val platform: String,
    val name: String,
    val handle: String = "",
    val active: Boolean = true,
)

/**
 * 一天一条的账号快照。
 *
 * 「累计粉丝」存绝对值，「本周涨粉」从相邻快照算 —— 采集脚本给的是绝对值，
 * 手动录入给的是当天涨粉，字段 [newFollowers] 两边都能填。
 */
data class MediaMetric(
    val id: String,
    val accountId: String,
    val date: String,
    val followers: Long = 0,
    val views: Long = 0,
    val interactions: Long = 0,
    val newFollowers: Long = 0,
    val revenue: Double = 0.0,
    val source: String = SOURCE_MANUAL,
)

/** 一条内容（计划中或已发布）。 */
data class ContentItem(
    val id: String,
    val accountId: String,
    val title: String,
    val topic: String = "",
    val plannedDate: String = "",
    val publishedAt: String = "",
    val url: String = "",
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
    val status: String = STATUS_PLANNED,
) {
    companion object {
        const val STATUS_PLANNED = "planned"
        const val STATUS_PUBLISHED = "published"
    }
}

/** 一段窗口内的汇总数字 —— 页面和 agent 看的是同一份。 */
data class MediaStats(
    val followers: Long = 0,
    val followersDelta: Long = 0,
    val views: Long = 0,
    val interactions: Long = 0,
    val revenue: Double = 0.0,
    val published: Int = 0,
    val planned: Int = 0,
)

/**
 * 自媒体数据层：**业务数据 + 业务动作**。
 *
 * UI 的按钮和 agent 的工具调的是同一批方法 —— 结果文案也只有一份
 * （见 rules/CLAUDE.template.md「逻辑 api 化」）。
 *
 * 存储：kit 的 [EntityStore]（okio 文件），三个子目录各存一类实体。
 */
class MediaStore(private val store: EntityStore) {

    // ── 账号 ────────────────────────────────────────────────

    suspend fun listAccounts(): List<MediaAccount> =
        store.loadAll(SUBDIR_ACCOUNTS).mapNotNull { it.toAccount() }.sortedBy { it.name }

    suspend fun findAccount(id: String): MediaAccount? =
        store.read(SUBDIR_ACCOUNTS, safeId(id))?.toAccount()

    /** 新增或更新一个账号。返回一句结果文案。 */
    suspend fun upsertAccount(platform: String, name: String, handle: String): String {
        val p = platform.trim()
        val n = name.trim()
        if (p.isEmpty()) return "没保存：平台不能为空"
        if (n.isEmpty()) return "没保存：账号名不能为空"
        val id = accountId(p, handle.trim())
        val account = MediaAccount(id = id, platform = p, name = n, handle = handle.trim())
        store.write(SUBDIR_ACCOUNTS, account.toEntity())
        return "已保存账号：$n（$p）"
    }

    /** 删除账号（连同它的指标和内容）。危险动作 —— agent 调用要走审批闸门。 */
    suspend fun deleteAccount(id: String): String {
        val account = findAccount(id) ?: return "没找到账号 $id"
        listMetrics(id).forEach { store.delete(SUBDIR_METRICS, it.id) }
        listContents(id).forEach { store.delete(SUBDIR_CONTENTS, it.id) }
        store.delete(SUBDIR_ACCOUNTS, safeId(id))
        return "已删除账号：${account.name}"
    }

    // ── 指标 ────────────────────────────────────────────────

    suspend fun listMetrics(accountId: String? = null): List<MediaMetric> =
        store.loadAll(SUBDIR_METRICS)
            .mapNotNull { it.toMetric() }
            .filter { accountId == null || it.accountId == safeId(accountId) }
            .sortedBy { it.date }

    /**
     * 记一条指标。同账号同一天 = 覆盖（一天一条，不是追加）。
     *
     * 采集脚本同步来的数据 [source] = sync，会覆盖手动录的同一天；
     * 手动再录一次又把手动的写回去 —— 谁后写谁赢。
     */
    suspend fun recordMetric(
        accountId: String,
        date: String,
        followers: Long = 0,
        views: Long = 0,
        interactions: Long = 0,
        newFollowers: Long = 0,
        revenue: Double = 0.0,
        source: String = SOURCE_MANUAL,
    ): String {
        val account = findAccount(accountId) ?: return "没记：找不到账号 $accountId"
        if (date.isBlank()) return "没记：日期不能为空"
        val metric = MediaMetric(
            id = metricId(accountId, date),
            accountId = safeId(accountId),
            date = date.trim(),
            followers = followers,
            views = views,
            interactions = interactions,
            newFollowers = newFollowers,
            revenue = revenue,
            source = source,
        )
        store.write(SUBDIR_METRICS, metric.toEntity())
        return "已记录 ${account.name} $date：粉丝 ${metric.followers}，曝光 ${metric.views}，涨粉 ${metric.newFollowers}"
    }

    // ── 内容 ────────────────────────────────────────────────

    suspend fun listContents(accountId: String? = null): List<ContentItem> =
        store.loadAll(SUBDIR_CONTENTS)
            .mapNotNull { it.toContent() }
            .filter { accountId == null || it.accountId == safeId(accountId) }
            .sortedByDescending { it.publishedAt.ifBlank { it.plannedDate } }

    /** 新增或更新一条内容。 */
    suspend fun upsertContent(
        accountId: String,
        title: String,
        topic: String = "",
        plannedDate: String = "",
        publishedAt: String = "",
        url: String = "",
        views: Long = 0,
        likes: Long = 0,
        comments: Long = 0,
        shares: Long = 0,
        status: String = ContentItem.STATUS_PLANNED,
    ): String {
        val t = title.trim()
        if (t.isEmpty()) return "没保存：内容标题不能为空"
        if (findAccount(accountId) == null) return "没保存：找不到账号 $accountId"
        val id = contentId(accountId, publishedAt.ifBlank { plannedDate }, t)
        val item = ContentItem(
            id = id,
            accountId = safeId(accountId),
            title = t,
            topic = topic.trim(),
            plannedDate = plannedDate.trim(),
            publishedAt = publishedAt.trim(),
            url = url.trim(),
            views = views,
            likes = likes,
            comments = comments,
            shares = shares,
            status = status,
        )
        store.write(SUBDIR_CONTENTS, item.toEntity())
        return "已保存内容：$t"
    }

    // ── 派生统计 ────────────────────────────────────────────

    /** 窗口内的汇总：粉丝、涨粉、曝光、互动、收入、发布数。 */
    fun stats(metrics: List<MediaMetric>, contents: List<ContentItem>, days: Int): MediaStats {
        val window = metrics.takeLast(days.coerceAtLeast(1))
        val followers = window.lastOrNull()?.followers ?: 0
        val declaredDelta = window.sumOf { it.newFollowers }
        val computedDelta = if (window.size >= 2) window.last().followers - window.first().followers else 0
        return MediaStats(
            followers = followers,
            followersDelta = if (declaredDelta != 0L) declaredDelta else computedDelta,
            views = window.sumOf { it.views },
            interactions = window.sumOf { it.interactions },
            revenue = window.sumOf { it.revenue },
            published = contents.count { it.status == ContentItem.STATUS_PUBLISHED },
            planned = contents.count { it.status == ContentItem.STATUS_PLANNED },
        )
    }

    /**
     * 一段给人和模型看的摘要 —— **只有这一处格式**。
     *
     * 页面上的数字块和 agent 的回答用同一个函数，模型不会报出另一个格式的数字。
     */
    fun summary(stats: MediaStats, accounts: List<MediaAccount>, days: Int): String {
        if (accounts.isEmpty()) return "（还没有自媒体账号）"
        val names = accounts.joinToString("、") { it.name }
        return buildString {
            append("近 $days 天：")
            append("粉丝 ${stats.followers}（${if (stats.followersDelta >= 0) "+" else ""}${stats.followersDelta}）；")
            append("曝光 ${stats.views}；")
            append("互动 ${stats.interactions}；")
            append("已发布 ${stats.published} 条，待发布 ${stats.planned} 条；")
            append("账号：$names")
        }
    }

    /** 一条内容的单行描述 —— 用户看到的和模型看到的是同一个格式。 */
    fun format(content: ContentItem): String {
        val when_ = content.publishedAt.ifBlank { content.plannedDate }
        val stat = if (content.status == ContentItem.STATUS_PUBLISHED) {
            "曝光 ${content.views}，互动 ${content.likes + content.comments + content.shares}"
        } else {
            "待发布"
        }
        return "${content.title}（$when_，$stat）"
    }

    companion object {
        const val SUBDIR_ACCOUNTS = "media/accounts"
        const val SUBDIR_METRICS = "media/metrics"
        const val SUBDIR_CONTENTS = "media/contents"

        /** 账号 id = 平台 + handle —— 同一个平台同一个号只有一条记录。 */
        fun accountId(platform: String, handle: String): String =
            safeId("$platform:${handle.ifBlank { platform }}")

        /** 指标 id = 账号 + 日期 —— 一天一条，同天覆盖。 */
        fun metricId(accountId: String, date: String): String = safeId("$accountId@$date")

        /** 内容 id = 账号 + 日期 + 标题 —— 同一条内容重复同步不会变成两条。 */
        fun contentId(accountId: String, date: String, title: String): String =
            safeId("$accountId@${date.ifBlank { "none" }}#$title")
    }
}

// ── 实体 ↔ 文件字段 ─────────────────────────────────────────

private fun MediaAccount.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("platform", JsonPrimitive(platform))
        put("name", JsonPrimitive(name))
        put("handle", JsonPrimitive(handle))
        put("active", JsonPrimitive(active))
    },
)

private fun StoredEntity.toAccount(): MediaAccount? {
    val platform = fields.str("platform") ?: return null
    val name = fields.str("name") ?: return null
    return MediaAccount(
        id = id,
        platform = platform,
        name = name,
        handle = fields.str("handle").orEmpty(),
        active = fields.boolOr("active", true),
    )
}

private fun MediaMetric.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("accountId", JsonPrimitive(accountId))
        put("date", JsonPrimitive(date))
        put("followers", JsonPrimitive(followers))
        put("views", JsonPrimitive(views))
        put("interactions", JsonPrimitive(interactions))
        put("newFollowers", JsonPrimitive(newFollowers))
        put("revenue", JsonPrimitive(revenue))
        put("source", JsonPrimitive(source))
    },
)

private fun StoredEntity.toMetric(): MediaMetric? {
    val accountId = fields.str("accountId") ?: return null
    val date = fields.str("date") ?: return null
    return MediaMetric(
        id = id,
        accountId = accountId,
        date = date,
        followers = fields.longOr("followers", 0),
        views = fields.longOr("views", 0),
        interactions = fields.longOr("interactions", 0),
        newFollowers = fields.longOr("newFollowers", 0),
        revenue = fields.doubleOr("revenue", 0.0),
        source = fields.str("source") ?: SOURCE_MANUAL,
    )
}

private fun ContentItem.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("accountId", JsonPrimitive(accountId))
        put("title", JsonPrimitive(title))
        put("topic", JsonPrimitive(topic))
        put("plannedDate", JsonPrimitive(plannedDate))
        put("publishedAt", JsonPrimitive(publishedAt))
        put("url", JsonPrimitive(url))
        put("views", JsonPrimitive(views))
        put("likes", JsonPrimitive(likes))
        put("comments", JsonPrimitive(comments))
        put("shares", JsonPrimitive(shares))
        put("status", JsonPrimitive(status))
    },
)

private fun StoredEntity.toContent(): ContentItem? {
    val accountId = fields.str("accountId") ?: return null
    val title = fields.str("title") ?: return null
    return ContentItem(
        id = id,
        accountId = accountId,
        title = title,
        topic = fields.str("topic").orEmpty(),
        plannedDate = fields.str("plannedDate").orEmpty(),
        publishedAt = fields.str("publishedAt").orEmpty(),
        url = fields.str("url").orEmpty(),
        views = fields.longOr("views", 0),
        likes = fields.longOr("likes", 0),
        comments = fields.longOr("comments", 0),
        shares = fields.longOr("shares", 0),
        status = fields.str("status") ?: ContentItem.STATUS_PLANNED,
    )
}
