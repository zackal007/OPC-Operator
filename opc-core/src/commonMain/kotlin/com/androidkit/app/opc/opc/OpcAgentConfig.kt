package com.androidkit.app.opc.opc

/**
 * agent 的连接配置 —— 由壳工程从 BuildConfig 传进来（opc-core 是库，没有 BuildConfig）。
 *
 * key 不进仓库、不进版本库；正式上架把 baseUrl 指向自己的转发代理。
 */
data class OpcAgentConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.anthropic.com",
    val model: String = "claude-opus-5-5",
) {
    /** 没配 key 就别建会话 —— UI 会提示怎么配。 */
    fun isUsable(): Boolean = apiKey.isNotBlank()
}
