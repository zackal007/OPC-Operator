// OPC 操作台的 Android 壳：只管 manifest / 主题 / 启动 Activity，业务全在 :opc-core。
// SDK 版本 / 签名 / release 收缩 / detekt 由 build-logic 的 androidkit.android.app 统一管。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // skill 工具的入参用 @Serializable 数据类声明
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    id("androidkit.android.app")
}

// API key / 模型：命令行 -PanthropicApiKey=xxx，或环境变量 ANTHROPIC_API_KEY。
// 别把 key 写进仓库 —— key 会被打进 APK，提取出来就能用。
val anthropicApiKey = (providers.gradleProperty("anthropicApiKey").orNull
    ?: providers.environmentVariable("ANTHROPIC_API_KEY").orNull
    ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
val anthropicBaseUrl = providers.gradleProperty("anthropicBaseUrl").orNull
    ?: providers.environmentVariable("ANTHROPIC_BASE_URL").orNull
    ?: "https://api.anthropic.com"
val anthropicModel = providers.gradleProperty("anthropicModel").orNull
    ?: providers.environmentVariable("ANTHROPIC_MODEL").orNull
    ?: "claude-opus-5-5"

// release 包里带 key = 把 key 公开了（谁都能从 APK 里提取出来）。
// 只警告，不拦构建 —— 本地自测打 release 完全没问题；
// 真要上架，把 key 挪到服务端代理，APK 里只留 baseUrl。
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (isReleaseBuild && anthropicApiKey.isNotBlank()) {
    logger.warn(
        "\n" +
            "  ⚠️  release 包里带着 ANTHROPIC_API_KEY —— 它能被从 APK 里提取出来，等于公开了。\n" +
            "      本地自测无所谓；上架前把 key 挪到服务端代理（APK 里只留 baseUrl）。\n"
    )
}

android {
    namespace = "com.androidkit.opc"

    defaultConfig {
        applicationId = "com.androidkit.opc"

        // agent 的连接配置（值来自 gradle 属性 / 环境变量，见文件头）
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicApiKey\"")
        buildConfigField("String", "ANTHROPIC_BASE_URL", "\"$anthropicBaseUrl\"")
        buildConfigField("String", "ANTHROPIC_MODEL", "\"$anthropicModel\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }
}

dependencies {
    // 通用组件 + 操作台业务库（三端同源）
    implementation(project(":kit"))
    implementation(project(":opc-core"))
    // app 的 agent 运行时：Koog（模型走云端 API，loop 和工具都跑在手机上）
    implementation(project(":kit-agent-koog"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    // skill 工具的入参是 @Serializable 数据类
    implementation(libs.kotlinx.serialization.json)
    // GitHub API 要一个 Ktor 引擎（kit 只给 HttpClient 类型，引擎由 app 挂）
    implementation(libs.ktor.client.okhttp)

    // 单元测试：CI 的 unit-test job 会自动跑 apps/* 的 testDebugUnitTest
    testImplementation(libs.junit)
}
