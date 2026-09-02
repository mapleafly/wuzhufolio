package com.wuzhufolio.data.db

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * 数据访问闸门（T1.4 单写队列；ADR-002 §4 / PRD §10 注「数据写入串行化」/ 共享规范 §7）。
 *
 * - 写：[write]/[writeBlocking] 全部写事务经同一 [writeMutex] 串行执行（同一时刻至多一个写事务在途）；
 * - 读：[read]/[readBlocking] 独立 Exposed 事务/连接，与写并行（WAL 多读单写 + busy_timeout 防锁冲突）；
 * - 建库/迁移不经本闸门：boot 引导期单线程执行（WzDatabase.migrateToLatest）。
 *
 * 约束：写回调内禁止再调用本闸门的写入口（Mutex 不可重入）；需要嵌套事务语义时改用事务内部分段提交。
 */
class DbGate(private val database: WzDatabase) {

    private val writeMutex = Mutex()

    /** 单写队列（挂起版，协程用例/后台同步使用）。 */
    suspend fun <T> write(statement: Transaction.() -> T): T =
        writeMutex.withLock { transaction(database.exposed) { statement() } }

    /** 单写队列（阻塞版，同步仓库与测试使用；与 [write] 同一队列）。 */
    fun <T> writeBlocking(statement: Transaction.() -> T): T = runBlocking { write(statement) }

    /** 读事务（不经写队列，可并行）。 */
    suspend fun <T> read(statement: Transaction.() -> T): T =
        transaction(database.exposed) { statement() }

    /** 读事务（阻塞版）。 */
    fun <T> readBlocking(statement: Transaction.() -> T): T = runBlocking { read(statement) }
}
