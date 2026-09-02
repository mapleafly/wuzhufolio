package com.wuzhufolio.data

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.settings.SettingsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T1.4 验收：单写队列（Mutex 串行化）+ 读写连接分离（PRD §10 注 / ADR-002 §4 / 共享规范 §7）。
 * ① 并发写（多协程交错 upsert）无 SQLITE_BUSY/locked，计数逐笔落库；
 * ② 同一密钥下 读-改-写 在队列事务内原子，最终值精确；
 * ③ 读事务与写事务并行不冲突（WAL 多读单写）。
 */
class DbGateTest {

    private val db: WzDatabase = WzDatabase(
        Files.createTempDirectory("wuzhufolio-gate").resolve("gate.db"),
        randomDbKey(),
    )
    private val gate = DbGate(db)

    init {
        db.migrateToLatest()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    /** 单条原子 upsert：读当前值 -> 加 delta -> 写回（在同一个被队列串行的写事务内）。 */
    private fun upsertCounter(keyName: String, delta: Int) {
        gate.writeBlocking {
            val existing = SettingsTable.selectAll()
                .where { (SettingsTable.key eq keyName) and SettingsTable.accountId.isNull() }
                .singleOrNull()
            val current = existing?.get(SettingsTable.value)?.toIntOrNull() ?: 0
            val next = current + delta
            if (existing == null) {
                SettingsTable.insert {
                    it[SettingsTable.key] = keyName
                    it[accountId] = null
                    it[SettingsTable.value] = next.toString()
                    it[updatedAt] = Instant.now().toString()
                }
            } else {
                SettingsTable.update({ (SettingsTable.key eq keyName) and SettingsTable.accountId.isNull() }) {
                    it[SettingsTable.value] = next.toString()
                    it[updatedAt] = Instant.now().toString()
                }
            }
        }
    }

    private fun readCounter(keyName: String): Int? = gate.readBlocking {
        SettingsTable.selectAll()
            .where { (SettingsTable.key eq keyName) and SettingsTable.accountId.isNull() }
            .singleOrNull()
            ?.get(SettingsTable.value)?.toIntOrNull()
    }

    @Test
    fun `concurrent queued writes never conflict and every increment lands`() = runBlocking {
        val writers = 6
        val iterations = 12
        coroutineScope {
            (1..writers).map { writer ->
                async(Dispatchers.Default) {
                    repeat(iterations) {
                        upsertCounter("gate.writer.$writer", 1)
                    }
                }
            }.awaitAll()
        }
        for (writer in 1..writers) {
            assertEquals(iterations, readCounter("gate.writer.$writer"), "writer $writer lost increments")
        }
    }

    @Test
    fun `read-modify-write under the queue is atomic for one shared key`() = runBlocking {
        val tasks = 60
        coroutineScope {
            (1..tasks).map {
                async(Dispatchers.Default) { upsertCounter("gate.counter", 1) }
            }.awaitAll()
        }
        assertEquals(tasks, readCounter("gate.counter"), "counter must equal total queued increments")
    }

    @Test
    fun `readers interleave with queued writes without lock errors`() = runBlocking {
        repeat(8) { i ->
            upsertCounter("gate.phase", 1)
            coroutineScope {
                val reading = async(Dispatchers.Default) {
                    gate.readBlocking {
                        SettingsTable.selectAll()
                            .where { SettingsTable.accountId.isNull() }
                            .map { it[SettingsTable.key] }
                    }
                }
                val writing = async(Dispatchers.Default) {
                    upsertCounter("gate.phase.$i", 1)
                }
                listOf(reading, writing).awaitAll()
            }
        }
        val phase = readCounter("gate.phase")
        assertNotNull(phase)
        assertEquals(8, phase)
        assertNull(readCounter("gate.phase.missing"))
    }
}
