package com.androidkit.app.opc.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.androidkit.app.opc.OpcEnvironment
import com.androidkit.app.opc.opc.OpcAgentConfig
import com.androidkit.app.opc.ui.OpcRoot
import com.androidkit.app.ui.theme.AppTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files

/**
 * OPC 操作台的桌面入口。
 *
 * 和 Android 壳一样只做三件事：存储根目录、HTTP 客户端、agent 配置。
 * 配置走环境变量（和 chat-demo 一致）：
 * - ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN
 * - ANTHROPIC_BASE_URL（默认官方）
 * - ANTHROPIC_MODEL（默认 claude-opus-5-5）
 *
 * `--check` 是无界面自检：不联网、不花钱，跑一遍「录数据 → 加目标 → 总览」。
 */
fun main(args: Array<String>) {
    if (args.contains("--check")) {
        check()
        return
    }

    val rootDir = File(System.getProperty("user.home"), ".opc-console").absolutePath.toPath()
    val httpClient = HttpClient(OkHttp)
    val agentConfig = OpcAgentConfig(
        apiKey = env("ANTHROPIC_API_KEY") ?: env("ANTHROPIC_AUTH_TOKEN") ?: "",
        baseUrl = env("ANTHROPIC_BASE_URL") ?: "https://api.anthropic.com",
        model = env("ANTHROPIC_MODEL") ?: "claude-opus-5-5",
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OPC 操作台",
            state = rememberWindowState(size = DpSize(420.dp, 860.dp)),
        ) {
            AppTheme {
                val environment = remember { OpcEnvironment(rootDir, httpClient) }
                val state = remember(environment) { environment.state() }
                OpcRoot(state = state, agentConfig = agentConfig)
            }
        }
    }
}

/** 无界面自检 —— 组装一遍环境，走一遍最典型的三个动作。 */
private fun check() {
    val dir = Files.createTempDirectory("opc-check").toFile().absolutePath.toPath()
    val hub = OpcEnvironment(dir, HttpClient(OkHttp)).hub
    runBlocking {
        hub.media.upsertAccount("wechat_mp", "某公众号", "zackal")
        val accountId = hub.media.listAccounts().first().id
        hub.media.recordMetric(accountId, "2026-11-12", followers = 100, views = 10, newFollowers = 5)
        hub.goals.upsertGoal(title = "涨粉到一万", period = "2026Q4")
        println(hub.overview())
    }
}

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
