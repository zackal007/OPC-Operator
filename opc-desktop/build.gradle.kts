// OPC 操作台的桌面壳：只负责开窗口 + 拼环境，业务全在 :opc-core。
// 跑：gradlew :opc-desktop:run
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("io.gitlab.arturbosch.detekt")
}

kotlin { jvmToolchain(17) }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(fileTree("src") { include("**/kotlin/**/*.kt") })
}

dependencies {
    implementation(project(":kit"))
    implementation(project(":opc-core"))
    implementation(project(":kit-agent-koog"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "com.androidkit.app.opc.desktop.MainKt"
    }
}
