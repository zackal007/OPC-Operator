package com.androidkit.app.opc.data

import com.androidkit.app.data.storage.EntityStore
import com.androidkit.app.data.storage.StoredEntity
import com.androidkit.app.data.storage.intOr
import com.androidkit.app.data.storage.str
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 一个 GitHub 仓库（= 一个项目）。 */
data class Project(
    val id: String,
    val owner: String = "",
    val repo: String = "",
    val name: String = "",
    val description: String = "",
    val language: String = "",
    val stars: Int = 0,
    val openIssues: Int = 0,
    val openPulls: Int = 0,
    val status: String = STATUS_ACTIVE,
    val lastPushAt: String = "",
    val defaultBranch: String = "",
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_PAUSED = "paused"
    }

    /** 「owner/repo」—— 显示用。 */
    fun fullName(): String =
        if (owner.isBlank() || repo.isBlank()) name.ifBlank { id } else "$owner/$repo"
}

/** 一条提交。 */
data class Commit(
    val id: String,
    val projectId: String,
    val message: String = "",
    val author: String = "",
    val authoredAt: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val filesChanged: Int = 0,
)

/**
 * 项目数据层：仓库 + 提交。
 *
 * 仓库来自 GitHub API（[com.androidkit.app.opc.data.github.GithubClient]），
 * 提交也来自 GitHub API —— 本地只缓存，不主动去扫本地 git 仓库。
 */
class ProjectStore(private val store: EntityStore) {

    suspend fun listProjects(): List<Project> =
        store.loadAll(SUBDIR_PROJECTS).mapNotNull { it.toProject() }.sortedByDescending { it.lastPushAt }

    suspend fun findProject(id: String): Project? = store.read(SUBDIR_PROJECTS, safeId(id))?.toProject()

    /** 写入（覆盖）一个仓库。返回一句结果文案。 */
    suspend fun upsertProject(project: Project): String {
        store.write(SUBDIR_PROJECTS, project.copy(id = safeId(project.id)).toEntity())
        return "已保存项目：${project.fullName()}"
    }

    /** 删除项目及其提交缓存。危险动作 —— agent 调用要走审批闸门。 */
    suspend fun deleteProject(id: String): String {
        val project = findProject(id) ?: return "没找到项目 $id"
        listCommits(id).forEach { store.delete(SUBDIR_COMMITS, it.id) }
        store.delete(SUBDIR_PROJECTS, safeId(id))
        return "已删除项目：${project.fullName()}"
    }

    suspend fun listCommits(projectId: String? = null): List<Commit> =
        store.loadAll(SUBDIR_COMMITS)
            .mapNotNull { it.toCommit() }
            .filter { projectId == null || it.projectId == safeId(projectId) }
            .sortedByDescending { it.authoredAt }

    /** 写入一条提交（sha 相同 = 同一条，覆盖）。 */
    suspend fun upsertCommit(commit: Commit): String {
        store.write(SUBDIR_COMMITS, commit.copy(id = safeId(commit.id), projectId = safeId(commit.projectId)).toEntity())
        return "已记录提交：${commit.message.take(40)}"
    }

    /** 清理超出条数的旧提交 —— 只留最近 [keep] 条。 */
    suspend fun trimCommits(keep: Int = MAX_COMMITS): String {
        val all = listCommits()
        if (all.size <= keep) return "提交缓存没超（${all.size}/$keep）"
        all.drop(keep).forEach { store.delete(SUBDIR_COMMITS, it.id) }
        return "提交缓存已清理：保留最近 $keep 条"
    }

    /** 窗口内的提交条数（按项目筛）。 */
    fun commitCount(commits: List<Commit>, projectId: String?, sinceIso: String): Int =
        commits.count { (projectId == null || it.projectId == projectId) && it.authoredAt >= sinceIso }

    /** 一段给人和模型看的摘要 —— 只有这一处格式。 */
    fun summary(projects: List<Project>, commits: List<Commit>, sinceIso: String): String {
        if (projects.isEmpty()) return "（还没有项目）"
        val ranked = projects.sortedByDescending { commitCount(commits, it.id, sinceIso) }
        val lines = ranked.take(5).joinToString("；") { p ->
            val n = commitCount(commits, p.id, sinceIso)
            "${p.fullName()}：$n 次提交"
        }
        return "共 ${projects.size} 个项目；$lines"
    }

    /** 一条提交的单行描述。 */
    fun format(commit: Commit): String {
        val time = commit.authoredAt.replace('T', ' ').take(16)
        val head = commit.message.lineSequence().firstOrNull().orEmpty()
        return "$time · ${commit.author.ifBlank { "未知" }} · $head"
    }

    companion object {
        const val SUBDIR_PROJECTS = "projects/list"
        const val SUBDIR_COMMITS = "projects/commits"
        const val MAX_COMMITS = 200

        /** 项目 id = `owner/repo` 的安全版（会当文件名用，斜杠得换掉）。 */
        fun projectId(fullName: String): String = safeId(fullName)
    }
}

// ── 实体 ↔ 文件字段 ─────────────────────────────────────────

private fun Project.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("owner", JsonPrimitive(owner))
        put("repo", JsonPrimitive(repo))
        put("name", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        put("language", JsonPrimitive(language))
        put("stars", JsonPrimitive(stars))
        put("openIssues", JsonPrimitive(openIssues))
        put("openPulls", JsonPrimitive(openPulls))
        put("status", JsonPrimitive(status))
        put("lastPushAt", JsonPrimitive(lastPushAt))
        put("defaultBranch", JsonPrimitive(defaultBranch))
    },
)

private fun StoredEntity.toProject(): Project? {
    val owner = fields.str("owner").orEmpty()
    val repo = fields.str("repo").orEmpty()
    if (owner.isBlank() || repo.isBlank()) return null
    return Project(
        id = safeId("$owner/$repo"),
        owner = owner,
        repo = repo,
        name = fields.str("name").orEmpty(),
        description = fields.str("description").orEmpty(),
        language = fields.str("language").orEmpty(),
        stars = fields.intOr("stars", 0),
        openIssues = fields.intOr("openIssues", 0),
        openPulls = fields.intOr("openPulls", 0),
        status = fields.str("status") ?: Project.STATUS_ACTIVE,
        lastPushAt = fields.str("lastPushAt").orEmpty(),
        defaultBranch = fields.str("defaultBranch").orEmpty(),
    )
}

private fun Commit.toEntity() = StoredEntity(
    id = id,
    fields = buildJsonObject {
        put("projectId", JsonPrimitive(projectId))
        put("message", JsonPrimitive(message))
        put("author", JsonPrimitive(author))
        put("authoredAt", JsonPrimitive(authoredAt))
        put("additions", JsonPrimitive(additions))
        put("deletions", JsonPrimitive(deletions))
        put("filesChanged", JsonPrimitive(filesChanged))
    },
)

private fun StoredEntity.toCommit(): Commit? {
    val projectId = fields.str("projectId") ?: return null
    return Commit(
        id = id,
        projectId = projectId,
        message = fields.str("message").orEmpty(),
        author = fields.str("author").orEmpty(),
        authoredAt = fields.str("authoredAt").orEmpty(),
        additions = fields.intOr("additions", 0),
        deletions = fields.intOr("deletions", 0),
        filesChanged = fields.intOr("filesChanged", 0),
    )
}

/** 提交里用到的行数统计（GitHub 列表接口不返回，详情接口才有）。 */
internal fun Commit.linesTouched(): Int = additions + deletions
