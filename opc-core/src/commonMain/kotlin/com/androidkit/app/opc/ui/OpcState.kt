package com.androidkit.app.opc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.androidkit.app.opc.data.OpcHub
import com.androidkit.app.opc.data.OpcSettings

/**
 * 页面之间共享的状态 —— 就是一个普通类，不引 ViewModel（KMP 下没有那套东西）。
 *
 * [refreshTick] 是「数据变了，重新拉」的信号：页面在 `LaunchedEffect(state.refreshTick)`
 * 里重新读数据。写操作只要调一下 [refresh]，所有页面自动跟着更新。
 */
class OpcState(val hub: OpcHub) {

    var settings by mutableStateOf(OpcSettings())
        private set

    /** 一句状态文案（刚做完某个动作的结果）—— 页面顶栏弹一下。 */
    var status by mutableStateOf("")

    /** 数据版本号：任何写操作后 +1，页面据此重新读数据。 */
    var refreshTick by mutableIntStateOf(0)

    /** 正在同步（同步要联网，别让用户以为点了没反应）。 */
    var syncing by mutableStateOf(false)

    suspend fun reloadSettings() {
        settings = hub.currentSettings()
    }

    fun refresh() {
        refreshTick++
    }

    fun say(text: String) {
        status = text
    }

    /** 同步一次：拉仓库、提交、自媒体快照。 */
    suspend fun sync() {
        syncing = true
        try {
            say(hub.sync())
        } finally {
            syncing = false
            refresh()
            reloadSettings()
        }
    }
}
