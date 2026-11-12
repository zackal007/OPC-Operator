package com.androidkit.app.opc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

/**
 * 操作台的设置 —— 一份 JSON 文件（app 内部存储），三端通用（okio）。
 *
 * GitHub token 存在这里，**不进仓库、不进 BuildConfig**（BuildConfig 里的 key 能被从 APK 提取）。
 */
@Serializable
data class OpcSettings(
    /** GitHub PAT：拉仓库 / 提交 / 采集快照。留空 = 只看本地缓存。 */
    val githubToken: String = "",
    /** GitHub API 地址；企业版自建可以改。 */
    val githubApiBase: String = "https://api.github.com",
    /** 关注的仓库（`owner/repo`）；留空 = 用账号下全部仓库。 */
    val repos: List<String> = emptyList(),
    /** 当前周期（`2026Q4` 这样的字符串）。 */
    val period: String = "",
    /** 自媒体快照所在的仓库（`owner/repo`），采集脚本 push 到这里。 */
    val mediaSyncRepo: String = "",
    /** 快照文件在仓库里的路径。 */
    val mediaSyncPath: String = "media_snapshot.json",
    /** 快照文件所在分支。 */
    val mediaSyncBranch: String = "main",
    /** 上次同步时间（毫秒）。 */
    val lastSyncAt: Long = 0,
    /** 上次同步失败的原因；空 = 上次是成功的。 */
    val lastSyncError: String = "",
)

/**
 * 设置持久化 —— 照 kit 的 `SettingsStore` 写：就一个文件、一份设置。
 *
 * 不是通用 KV 库；真要存一堆东西，用 kit 的 EntityStore。
 */
class OpcSettingsStore(private val rootDir: Path) {

    private val fs = FileSystem.SYSTEM
    private val file = rootDir / FILE_NAME

    // 读不懂就用默认值：设置坏了不该让 app 起不来
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 读设置；文件不存在或读坏了都返回默认值。 */
    suspend fun load(): OpcSettings = withContext(Dispatchers.IO) {
        if (!fs.exists(file)) return@withContext OpcSettings()
        runCatching {
            json.decodeFromString(OpcSettings.serializer(), fs.read(file) { readUtf8() })
        }.getOrDefault(OpcSettings())
    }

    /** 写设置（覆盖整个文件）。 */
    suspend fun save(settings: OpcSettings): Unit = withContext(Dispatchers.IO) {
        file.parent?.let { fs.createDirectories(it) }
        fs.write(file) {
            writeUtf8(json.encodeToString(OpcSettings.serializer(), settings))
        }
    }

    /** 改一项再存 —— 免得每次都把整份设置读出来改回去。 */
    suspend fun update(transform: (OpcSettings) -> OpcSettings): OpcSettings {
        val updated = transform(load())
        save(updated)
        return updated
    }

    private companion object {
        const val FILE_NAME = "opc-settings.json"
    }
}

// ── 周期（季度）─────────────────────────────────────────────

/** 某个日期属于哪个周期：`2026-11-12` → `2026Q4`。 */
fun periodOf(date: LocalDate): String {
    val quarter = (date.monthNumber - 1) / 3 + 1
    return "${date.year}Q$quarter"
}

/** 当前周期。 */
fun currentPeriod(): String = periodOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))

/** 最近 n 个周期，从新到旧：`["2026Q4", "2026Q3", …]`。 */
fun recentPeriods(count: Int, today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): List<String> {
    val first = (today.monthNumber - 1) / 3
    return (0 until count).map { i ->
        val monthsAgo = first - i * 3
        val year = today.year + monthsAgo / 12
        val monthIndex = (monthsAgo % 12 + 12) % 12
        val quarter = monthIndex / 3 + 1
        "${year}Q$quarter"
    }
}
