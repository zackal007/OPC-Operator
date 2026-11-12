package com.androidkit.app.opc.data

import com.androidkit.app.opc.data.github.GithubClient
import com.androidkit.app.opc.data.github.GithubException
import com.androidkit.app.opc.data.media.MediaSnapshotParser
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 操作台的中枢 —— 把三个 store、设置和 GitHub 串起来。
 *
 * 规则：
 * - **拉取失败不清空本地缓存** —— 只更新「上次同步时间」和错误原因
 * - 同步来的数据覆盖手动数据；手动再录一次又赢回去
 * - 摘要文本只在这里生成一份（页面和 agent 共用）
 */
class OpcHub(
    private val httpClient: HttpClient,
    val media: MediaStore,
    val projects: ProjectStore,
    val goals: GoalStore,
    val settings: OpcSettingsStore,
) {

    /** 读设置。 */
    suspend fun currentSettings(): OpcSettings = settings.load()

    /** 改设置。 */
    suspend fun updateSettings(transform: (OpcSettings) -> OpcSettings): OpcSettings = settings.update(transform)

    /**
     * 同步一次：仓库 + 提交 + 自媒体快照。
     *
     * 返回一句结果文案（页面显示、agent 也拿同一句）。
     */
    suspend fun sync(): String {
        val current = settings.load()
        if (current.githubToken.isBlank()) {
            return "没同步：还没配 GitHub token（设置里填一下）"
        }
        val client = GithubClient(httpClient, current.githubToken, current.githubApiBase)
        return try {
            val saved = fetchProjects(client, current)
            val commitCount = fetchCommits(client, current, saved)
            val mediaMessage = fetchMediaSnapshot(client, current)
            projects.trimCommits()
            settings.update { it.copy(lastSyncAt = Clock.System.now().toEpochMilliseconds(), lastSyncError = "") }
            "同步完成：${saved.size} 个项目，$commitCount 条新提交；$mediaMessage"
        } catch (e: GithubException) {
            settings.update { it.copy(lastSyncError = e.message.orEmpty()) }
            "同步失败：${e.message}"
        } catch (e: Exception) {
            settings.update { it.copy(lastSyncError = e.message.orEmpty()) }
            "同步失败：${e.message ?: e::class.simpleName}"
        }
    }

    /** 拉仓库列表 → 写成本地项目。 */
    private suspend fun fetchProjects(client: GithubClient, current: OpcSettings): List<Project> {
        val repos = if (current.repos.isEmpty()) {
            client.listRepos()
        } else {
            current.repos.mapNotNull { repo ->
                val parts = repo.split('/')
                if (parts.size != 2) null else client.repo(parts[0], parts[1])
            }
        }
        val saved = repos.map { repo ->
            val project = Project(
                id = ProjectStore.projectId(repo.fullName),
                owner = repo.fullName.substringBefore('/'),
                repo = repo.fullName.substringAfter('/'),
                name = repo.name,
                description = repo.description.orEmpty(),
                language = repo.language.orEmpty(),
                stars = repo.stars,
                openIssues = repo.openIssues,
                status = if (repo.archived) Project.STATUS_PAUSED else Project.STATUS_ACTIVE,
                lastPushAt = repo.pushedAt.orEmpty(),
                defaultBranch = repo.defaultBranch,
            )
            projects.upsertProject(project)
            project
        }
        return saved
    }

    /** 拉提交 → 写成本地提交缓存。返回新写入的条数。 */
    private suspend fun fetchCommits(
        client: GithubClient,
        current: OpcSettings,
        projectList: List<Project>,
    ): Int {
        val since = sinceTimestamp(current.lastSyncAt)
        var count = 0
        for (project in projectList) {
            if (project.owner.isBlank() || project.repo.isBlank()) continue
            val commits = client.listCommits(project.owner, project.repo, since)
            for (commit in commits) {
                val authoredAt = commit.commit.author?.date.orEmpty()
                if (authoredAt.isBlank()) continue
                projects.upsertCommit(
                    Commit(
                        id = commit.sha,
                        projectId = project.id,
                        message = commit.commit.message,
                        author = commit.commit.author?.name.orEmpty(),
                        authoredAt = authoredAt,
                    )
                )
                count++
            }
        }
        return count
    }

    /** 拉自媒体快照 → 写进本地。 */
    private suspend fun fetchMediaSnapshot(client: GithubClient, current: OpcSettings): String {
        if (current.mediaSyncRepo.isBlank()) return "自媒体快照没配仓库，跳过"
        val parts = current.mediaSyncRepo.split('/')
        if (parts.size != 2) return "自媒体快照仓库写法不对（要 owner/repo），跳过"
        val raw = client.fileContent(parts[0], parts[1], current.mediaSyncPath, current.mediaSyncBranch)
        val snapshot = MediaSnapshotParser.parse(raw) ?: return "自媒体快照解析失败，跳过"
        return MediaSnapshotParser.apply(snapshot, media)
    }

    /** 拉取提交的起始时间：上次同步时间，没有就回看 30 天。 */
    private fun sinceTimestamp(lastSyncAt: Long): String {
        val now = Clock.System.now()
        val since = if (lastSyncAt > 0) {
            Instant.fromEpochMilliseconds(lastSyncAt)
        } else {
            now - 30.days
        }
        return since.toString()
    }

    /**
     * 一段总览 —— 首页和 agent 的「我现在什么状况」共用这一份文本。
     */
    suspend fun overview(windowDays: Int = 7): String {
        val accounts = media.listAccounts()
        val metrics = media.listMetrics()
        val contents = media.listContents()
        val stats = media.stats(metrics, contents, windowDays)
        val projectList = projects.listProjects()
        val commits = projects.listCommits()
        val since = sinceIso(windowDays)
        val period = currentPeriod()
        val goalList = goals.listGoals(period)
        return buildString {
            append(media.summary(stats, accounts, windowDays))
            append("；")
            append(projects.summary(projectList, commits, since))
            append("；")
            append(goals.summary(goalList, period))
        }
    }

    /** 窗口起始的 ISO 时间串（提交按它筛）。 */
    fun sinceIso(windowDays: Int): String =
        (Clock.System.now() - windowDays.days).toString()

    /** 今天的日期串。 */
    fun today(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
