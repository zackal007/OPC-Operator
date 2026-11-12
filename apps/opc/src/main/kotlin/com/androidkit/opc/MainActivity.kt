package com.androidkit.opc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.androidkit.app.opc.OpcEnvironment
import com.androidkit.app.opc.opc.OpcAgentConfig
import com.androidkit.app.opc.ui.OpcRoot
import com.androidkit.app.ui.theme.AppTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path.Companion.toPath

/**
 * OPC 操作台的入口。
 *
 * 这一层只做三件事：
 * - 给业务库一个**存储根目录**（app 内部存储，不出设备）
 * - 给业务库一个 **HTTP 客户端**（Ktor + okhttp 引擎）
 * - 把 **agent 配置**（key / baseUrl / model）传进去
 *
 * 页面骨架走 kit 的 AppShell，业务在 :opc-core。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val root = filesDir.absolutePath.toPath().resolve("opc")
        val httpClient = HttpClient(OkHttp)

        setContent {
            AppTheme {
                val environment = remember { OpcEnvironment(root, httpClient) }
                val state = remember(environment) { environment.state() }
                OpcRoot(
                    state = state,
                    agentConfig = OpcAgentConfig(
                        apiKey = BuildConfig.ANTHROPIC_API_KEY,
                        baseUrl = BuildConfig.ANTHROPIC_BASE_URL,
                        model = BuildConfig.ANTHROPIC_MODEL,
                    ),
                )
            }
        }
    }
}
