package com.androidkit.app.opc.data.media

import com.androidkit.app.opc.data.MediaStore
import com.androidkit.app.opc.data.SOURCE_SYNC
import com.androidkit.app.opc.data.safeId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 自媒体快照 —— **采集脚本和 app 之间唯一的契约**。
 *
 * 采集脚本（桌面跑）把各平台的数字抓成这个格式，push 到一个 GitHub 仓库；
 * app 拉下来写进本地存储。脚本挂了、平台接口变了，手动录的数据照常用 ——
 * 同步只是「多一份数据源」，不是唯一入口。
 *
 * ```json
 * {
 *   "generatedAt": "2026-11-12T09:00:00Z",
 *   "accounts": [{"platform": "wechat_mp", "handle": "zackal", "name": "某公众号"}],
 *   "metrics": [{"accountId": "wechat_mp:zackal", "date": "2026-11-12",
 *                "followers": 12800, "views": 3200, "interactions": 96,
 *                "newFollowers": 128, "revenue": 0}],
 *   "contents": [{"accountId": "wechat_mp:zackal", "title": "…", "publishedAt": "2026-11-10"}]
 * }
 * ```
 */
@Serializable
data class MediaSnapshot(
    @SerialName("generatedAt") val generatedAt: String = "",
    val accounts: List<SnapshotAccount> = emptyList(),
    val metrics: List<SnapshotMetric> = emptyList(),
    val contents: List<SnapshotContent> = emptyList(),
)

/** 快照里的一个账号。 */
@Serializable
data class SnapshotAccount(
    val platform: String = "",
    val handle: String = "",
    val name: String = "",
)

/** 快照里的一条指标。 */
@Serializable
data class SnapshotMetric(
    val accountId: String = "",
    val date: String = "",
    val followers: Long = 0,
    val views: Long = 0,
    val interactions: Long = 0,
    val newFollowers: Long = 0,
    val revenue: Double = 0.0,
)

/** 快照里的一条内容。 */
@Serializable
data class SnapshotContent(
    val accountId: String = "",
    val title: String = "",
    val topic: String = "",
    val plannedDate: String = "",
    val publishedAt: String = "",
    val url: String = "",
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
    val status: String = "planned",
)

/**
 * 快照解析 + 落库。
 *
 * 解析不依赖网络 —— 可以离线测（见 commonTest）。
 */
object MediaSnapshotParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析快照文本；坏 JSON 返回 null（不该让同步整个失败）。 */
    fun parse(raw: String): MediaSnapshot? =
        runCatching { json.decodeFromString(MediaSnapshot.serializer(), raw) }.getOrNull()

    /**
     * 把快照写进本地存储。返回一句结果文案。
     *
     * 冲突规则：同账号同一天，**同步数据覆盖手动数据**（脚本更权威）；
     * 用户之后再手动录一次，手动的又赢回去。
     */
    suspend fun apply(snapshot: MediaSnapshot, media: MediaStore): String {
        var accounts = 0
        var metrics = 0
        var contents = 0
        for (account in snapshot.accounts) {
            media.upsertAccount(account.platform, account.name.ifBlank { account.handle }, account.handle)
            accounts++
        }
        for (metric in snapshot.metrics) {
            val accountId = safeId(metric.accountId)
            media.recordMetric(
                accountId = accountId,
                date = metric.date,
                followers = metric.followers,
                views = metric.views,
                interactions = metric.interactions,
                newFollowers = metric.newFollowers,
                revenue = metric.revenue,
                source = SOURCE_SYNC,
            )
            metrics++
        }
        for (content in snapshot.contents) {
            media.upsertContent(
                accountId = safeId(content.accountId),
                title = content.title,
                topic = content.topic,
                plannedDate = content.plannedDate,
                publishedAt = content.publishedAt,
                url = content.url,
                views = content.views,
                likes = content.likes,
                comments = content.comments,
                shares = content.shares,
                status = content.status,
            )
            contents++
        }
        return "已同步自媒体数据：$accounts 个账号，$metrics 条指标，$contents 条内容"
    }
}
