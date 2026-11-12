package com.androidkit.app.opc.data

/** id 里允许出现的符号（其余一律换成 `_`）。 */
private val SAFE_CHARS = setOf('-', '_', '.', '@')

/**
 * 实体 id 会当**文件名**用（kit 的 FileStorageEngine 直接 `id + ".json"`）。
 *
 * 所以 id 里不能有路径分隔符和 Windows 不许的字符（`: * ? " < > |`）——
 * `owner/repo`、`wechat_mp:zackal` 这类业务键都得先过这一关。
 */
internal fun safeId(raw: String): String =
    raw.trim()
        .map { ch -> if (ch.isLetterOrDigit() || ch in SAFE_CHARS) ch else '_' }
        .joinToString("")
        .take(80)
        .ifEmpty { "id" }
