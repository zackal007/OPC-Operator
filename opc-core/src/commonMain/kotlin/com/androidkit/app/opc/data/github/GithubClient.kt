package com.androidkit.app.opc.data.github

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** GitHub 调不通了（网络、鉴权、限流）。 */
class GithubException(message: String) : Exception(message)

/** 仓库（GitHub 的一个 repo）。 */
@Serializable
data class GithubRepo(
    @SerialName("full_name") val fullName: String = "",
    val name: String = "",
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "",
    val fork: Boolean = false,
    val archived: Boolean = false,
)

/** 一条提交。 */
@Serializable
data class GithubCommit(
    val sha: String = "",
    val commit: GithubCommitDetail = GithubCommitDetail(),
)

/** 提交详情。 */
@Serializable
data class GithubCommitDetail(
    val message: String = "",
    val author: GithubCommitAuthor? = null,
)

/** 提交作者。 */
@Serializable
data class GithubCommitAuthor(
    val name: String = "",
    val date: String = "",
)

/**
 * GitHub API 客户端 —— 只读，不写。
 *
 * 拉的东西就三样：**仓库**（= 项目）、**提交**、**未合 PR / 未关 issue**。
 * 自媒体快照也走这里（读仓库里的一个 json 文件）。
 *
 * 限流：PAT 5000 次/小时，私有仓库不限速；不配 token 也能用（60 次/小时/IP）。
 */
class GithubClient(
    private val client: HttpClient,
    private val token: String = "",
    private val apiBase: String = "https://api.github.com",
) {

    /** 账号下的仓库（不含 fork —— fork 出来的东西一般不是"我的项目"）。 */
    suspend fun listRepos(): List<GithubRepo> {
        val body = getJson("/user/repos?per_page=100&sort=pushed")
        val repos = decode<List<GithubRepo>>(body)
        return repos.filterNot { it.fork }
    }

    /** 单个仓库（设置里指定了 `owner/repo` 时用；也能读别人的公开仓库）。 */
    suspend fun repo(owner: String, name: String): GithubRepo? =
        runCatching { decode<GithubRepo>(getJson("/repos/$owner/$name")) }.getOrNull()

    /** 某个仓库的提交（`since` 是 ISO 时间串；返回 0 就别传）。 */
    suspend fun listCommits(owner: String, repo: String, since: String? = null): List<GithubCommit> {
        val query = buildString {
            append("/repos/$owner/$repo/commits?per_page=100")
            if (!since.isNullOrBlank()) append("&since=$since")
        }
        return decode(getJson(query))
    }

    /** 未合的 PR 数。 */
    suspend fun openPulls(owner: String, repo: String): Int =
        decode<List<GithubRepo>>(getJson("/repos/$owner/$repo/pulls?state=open&per_page=100")).size

    /**
     * 读仓库里的一个文本文件（自媒体快照就走这条路）。
     *
     * 走 raw.githubusercontent.com 而不是 contents 接口 —— 少一层 base64 解码。
     * 私有仓库带 token 也能读。
     */
    suspend fun fileContent(owner: String, repo: String, path: String, ref: String = "main"): String {
        val url = "${rawBase(owner, repo, ref)}/$path"
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw GithubException("读 $owner/$repo 的 $path 失败：HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    private suspend fun getJson(path: String): String {
        val response = client.get("$apiBase$path") {
            header(HttpHeaders.Accept, ACCEPT)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header("X-GitHub-Api-Version", API_VERSION)
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (response.status.value == 403 || response.status.value == 429) {
            throw GithubException("GitHub 限流了（HTTP ${response.status.value}），等一会儿再同步")
        }
        if (!response.status.isSuccess()) {
            throw GithubException("GitHub 请求失败：HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    /** raw 文件的地址：github.com 走 raw.githubusercontent.com，自建企业版就用 apiBase 本身。 */
    private fun rawBase(owner: String, repo: String, ref: String): String {
        val host = if (apiBase.contains("github.com")) "https://raw.githubusercontent.com" else apiBase
        return "$host/$owner/$repo/$ref"
    }

    private inline fun <reified T> decode(body: String): T =
        json.decodeFromString(body)

    companion object {
        const val ACCEPT = "application/vnd.github+json"
        const val API_VERSION = "2022-11-28"
        const val USER_AGENT = "OPC-Operator"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
