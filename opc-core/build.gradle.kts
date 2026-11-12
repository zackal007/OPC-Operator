// OPC 操作台的业务库：数据 + 页面 + agent skill，三端同源（android / desktop / ios）。
//
// 结构照 kit 的规矩：data 层不写 Compose，ui 层只调 data 层，skill 层是薄包装。
// 平台差异（HTTP 引擎、存储根目录）由各端壳工程注入 —— 这里不写 expect/actual。
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("io.gitlab.arturbosch.detekt")
}

val hostIsMac = System.getProperty("os.name").startsWith("Mac")

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.androidkit.app.opc"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
        androidResources {
            enable = true
        }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    if (hostIsMac) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "OpcCore"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 通用组件 + agent 运行时：直接用，不重复实现
            api(project(":kit"))
            api(project(":kit-agent-koog"))

            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            // HTTP 客户端类型出现在公开签名里（GithubClient 的构造参数），下游要看得见
            api(libs.ktor.client.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
            }
        }

        if (hostIsMac) {
            val iosMain by getting {
                dependencies {
                    implementation(libs.ktor.client.darwin)
                }
            }
        }
    }
}

// 生成的资源访问器：com.androidkit.app.opc.res.Res.string.xxx
compose.resources {
    packageOfResClass = "com.androidkit.app.opc.res"
    publicResClass = true
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        fileTree("src") { include("**/kotlin/**/*.kt") },
    )
}
