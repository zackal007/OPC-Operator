package com.androidkit.app.opc

import com.androidkit.app.data.storage.FileStorageEngine
import com.androidkit.app.data.storage.JsonCodec
import com.androidkit.app.opc.data.GoalStore
import com.androidkit.app.opc.data.MediaStore
import com.androidkit.app.opc.data.OpcHub
import com.androidkit.app.opc.data.OpcSettingsStore
import com.androidkit.app.opc.data.ProjectStore
import com.androidkit.app.opc.ui.OpcState
import io.ktor.client.HttpClient
import okio.Path

/**
 * 操作台的运行环境 —— 各端壳工程只要给出**存储根目录**和 **HTTP 客户端**，
 * 剩下的组装在这里做一次。
 *
 * 壳工程（Android / Desktop / iOS）各自决定：
 * - 根目录：Android 传 `filesDir`，桌面传用户目录
 * - HTTP 引擎：android/desktop 用 okhttp，iOS 用 darwin
 */
class OpcEnvironment(
    rootDir: Path,
    httpClient: HttpClient,
) {
    /** 业务数据落盘的地方（kit 的文件存储引擎 + JSON 格式）。 */
    val entityStore = FileStorageEngine(rootDir, JsonCodec())

    val media = MediaStore(entityStore)
    val projects = ProjectStore(entityStore)
    val goals = GoalStore(entityStore)
    val settings = OpcSettingsStore(rootDir)

    val hub = OpcHub(httpClient, media, projects, goals, settings)

    /** 建页面用的状态（每个 app 一个）。 */
    fun state(): OpcState = OpcState(hub)
}
