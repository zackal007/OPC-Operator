package com.androidkit.app.opc

import com.androidkit.app.data.agent.SkillSample
import com.androidkit.app.data.agent.runSamples
import com.androidkit.app.opc.opc.opcSkill
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 技能黄金样例 —— 钉住**工具这一层**：
 * 工具名、入参字段名、返回文案。
 *
 * 入参字段名是对模型的公开契约，改字段名 ≈ 改 prompt，样例会红。
 */
class OpcSkillTest {

    @Test
    fun 工具样例全过() = runBlocking {
        val dir = Files.createTempDirectory("opc-skill").toFile().absolutePath.toPath()
        val hub = OpcEnvironment(dir, HttpClient(OkHttp)).hub
        val skill = opcSkill(hub, "claude-opus-5-5")

        val failures = skill.runSamples(
            listOf(
                SkillSample("list_media_accounts", "{}", "（还没有自媒体账号）"),
                SkillSample("list_goals", """{"period":"2026Q4"}""", "2026Q4"),
                SkillSample("add_goal", """{"title":"涨粉到一万","period":"2026Q4"}""", "已保存目标"),
                SkillSample("list_goals", """{"period":"2026Q4"}""", "涨粉到一万"),
                SkillSample("list_projects", "{}", "（还没有项目）"),
                SkillSample("list_recent_commits", "{}", "（最近没有提交）"),
            ),
        )
        assertEquals(emptyList(), failures)
    }
}
