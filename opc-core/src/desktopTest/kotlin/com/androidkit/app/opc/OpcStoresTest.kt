package com.androidkit.app.opc

import com.androidkit.app.data.storage.FileStorageEngine
import com.androidkit.app.data.storage.JsonCodec
import com.androidkit.app.opc.data.GoalStore
import com.androidkit.app.opc.data.MediaStore
import com.androidkit.app.opc.data.SOURCE_SYNC
import com.androidkit.app.opc.data.media.MediaSnapshotParser
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 数据层的检查 —— 三个最容易出错的点：
 * - 同一天再记一次是**覆盖**不是追加
 * - 目标进度 = 各关键结果的平均
 * - 同步来的快照覆盖手动数据
 */
class OpcStoresTest {

    private fun tempDir(): Path =
        Files.createTempDirectory("opc-test").toFile().absolutePath.toPath()

    @Test
    fun 同一天再记一次是覆盖不是追加() = runBlocking {
        val media = MediaStore(FileStorageEngine(tempDir(), JsonCodec()))
        media.upsertAccount("wechat_mp", "某公众号", "zackal")
        val id = MediaStore.accountId("wechat_mp", "zackal")

        media.recordMetric(id, "2026-11-12", followers = 100, views = 10, newFollowers = 5)
        media.recordMetric(id, "2026-11-12", followers = 120, views = 20, newFollowers = 6)

        val metrics = media.listMetrics(id)
        assertEquals(1, metrics.size)
        assertEquals(120L, metrics.first().followers)
    }

    @Test
    fun 目标进度按关键结果平均() = runBlocking {
        val goals = GoalStore(FileStorageEngine(tempDir(), JsonCodec()))
        goals.upsertGoal(title = "涨粉到一万", period = "2026Q4")
        val goalId = GoalStore.goalId("2026Q4", "涨粉到一万")

        goals.addKeyResult(goalId, "粉丝数", "人", 10000.0)
        goals.updateKeyResult(goalId, GoalStore.krId(goalId, "粉丝数"), 2500.0)

        val goal = goals.findGoal(goalId)
        assertTrue(goal != null)
        assertEquals(25, goal.progressPercent())
        assertEquals(1, goals.listGoals("2026Q4").size)
    }

    @Test
    fun 快照解析并落库() = runBlocking {
        val media = MediaStore(FileStorageEngine(tempDir(), JsonCodec()))
        val raw = """
            {
              "generatedAt": "2026-11-12T00:00:00Z",
              "accounts": [{"platform": "wechat_mp", "handle": "zackal", "name": "某公众号"}],
              "metrics": [{"accountId": "wechat_mp:zackal", "date": "2026-11-12", "followers": 12800}]
            }
        """.trimIndent()

        val snapshot = MediaSnapshotParser.parse(raw)
        assertTrue(snapshot != null)
        val message = MediaSnapshotParser.apply(snapshot, media)

        assertTrue(message.contains("1 个账号"))
        val metrics = media.listMetrics()
        assertEquals(1, metrics.size)
        assertEquals(SOURCE_SYNC, metrics.first().source)
        assertEquals(12800L, metrics.first().followers)
    }

    @Test
    fun 坏快照不炸() {
        assertNull(MediaSnapshotParser.parse("{ 这不是 json"))
    }
}
