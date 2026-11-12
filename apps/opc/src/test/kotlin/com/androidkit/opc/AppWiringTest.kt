package com.androidkit.opc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * app 壳的自检 —— 模板生成的东西别自己跟自己打架。
 *
 * 照抄这个文件写你自己的测试；fork 的第一个 PR 就该有单测。
 * （CI 的 unit-test job 会自动跑 apps 目录下 app 的 testDebugUnitTest，不用改 workflow。）
 */
class AppWiringTest {

    @Test
    fun `app_name 在 strings 文件里且非空`() {
        val strings = read("src/main/res/values/strings.xml")
        assertTrue("strings.xml 里没有 app_name", strings.contains("name=\"app_name\""))
        assertTrue("app_name 是空的", !strings.contains("<string name=\"app_name\"></string>"))
    }

    @Test
    fun `manifest 的 label 指向 app_name`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertEquals("manifest 的 android:label 应该是 @string/app_name", 1, Regex("android:label=\"@string/app_name\"").findAll(manifest).count())
    }

    @Test
    fun `入口 Activity 挂上了操作台页面`() {
        val main = read("src/main/kotlin/com/androidkit/opc/MainActivity.kt")
        assertTrue("MainActivity 里没看到 OpcRoot —— 壳没接上业务", main.contains("OpcRoot"))
    }

    /** 单元测试的工作目录是模块目录；从仓库根跑也认一次，省得换 IDE 就红。 */
    private fun read(path: String): String {
        val file = listOf(File(path), File("apps/opc/$path"))
            .firstOrNull { it.isFile } ?: error("找不到 $path，工作目录=${File("").absolutePath}")
        return file.readText()
    }
}
