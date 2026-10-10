package com.wuzhufolio.data.demo

import com.wuzhufolio.data.ledger.LedgerTestEnv
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.demo.DemoData
import com.wuzhufolio.domain.demo.DemoFailReason
import com.wuzhufolio.domain.demo.DemoLoadResult
import com.wuzhufolio.domain.demo.DemoRejectReason
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 示例数据用例实现（B1 · `D46` · T15.3）——真实 SQLCipher 库 + 既有账本用例（无 fake）：
 * 幂等 / 非空账户拒绝 / 清除只删标记行（**不误删真实数据**）/ 账户隔离 / 未登录类型化失败 /
 * 真实记录依赖示例增资时的边界行为。
 *
 * 时间一律**相对 `now` 构造**（`AGENTS.md §7.3-7` / DEF-58：不得硬编码绝对时间基准）。
 */
class DefaultDemoDataServiceTest {

    private val now: Instant = Instant.parse("2026-10-10T12:00:00Z")

    /** 临时库 + 种子目录（补 SOL——示例数据集覆盖 BTC/ETH/SOL）。 */
    private fun env(): LedgerTestEnv = LedgerTestEnv().also { e ->
        runBlocking {
            e.catalog.refreshDirectory(
                LedgerTestEnv.seedDirectory() + CoinDirectoryEntry("solana", "sol", "Solana"),
            )
        }
    }

    private fun service(e: LedgerTestEnv) = DefaultDemoDataService(
        sessions = e.sessions,
        transactions = e.service,
        funds = e.fundService,
        clock = { now },
    )

    /** 真实增资（无标记）——自给自足，不依赖示例增资。 */
    private suspend fun LedgerTestEnv.addRealDeposit(quantity: String = "50000") {
        fundService.saveFund(
            FundInput(
                kind = FlowKind.DEPOSIT,
                coinSymbol = "USDT",
                quantity = BigDecimal(quantity),
                time = now.minus(10, ChronoUnit.DAYS),
                notes = "真实增资",
            ),
        )
    }

    /** 真实买入（无标记）。 */
    private suspend fun LedgerTestEnv.addRealBuy(quantity: String = "0.5", daysAgo: Long = 5) {
        service.saveTransaction(
            TransactionInput(
                exchange = "BINANCE",
                baseSymbol = "BTC",
                quoteSymbol = "USDT",
                side = Side.BUY,
                price = BigDecimal("50000"),
                quantity = BigDecimal(quantity),
                fee = BigDecimal.ZERO,
                feeCoinSymbol = "USDT",
                time = now.minus(daysAgo, ChronoUnit.DAYS),
                notes = "真实买入",
            ),
        )
    }

    private suspend fun LedgerTestEnv.transactions() = service.listTransactions(TxFilter())

    private suspend fun LedgerTestEnv.funds() = fundService.listFunds(FundFilter()).rows

    @Test
    fun loadWritesSelfConsistentDatasetAndIsIdempotent() = runBlocking {
        env().use { e ->
            e.login()
            val svc = service(e)

            assertEquals(DemoLoadResult.Loaded(transactions = 4, funds = 1), svc.load())

            val rows = e.transactions()
            assertEquals(4, rows.size)
            assertEquals(setOf("BTC/USDT", "ETH/USDT", "SOL/USDT"), rows.map { it.pair }.toSet())
            assertTrue(rows.all { DemoData.isDemoNote(it.notes) }, "示例交易备注必须带统一标记")

            val funds = e.funds()
            assertEquals(1, funds.size)
            assertEquals("USDT", funds.single().coinSymbol)
            assertTrue(DemoData.isDemoNote(funds.single().notes))

            assertTrue(svc.hasDemoData())

            // 幂等：重复加载不写入、不改动既有行
            assertEquals(DemoLoadResult.AlreadyLoaded, svc.load())
            assertEquals(4, e.transactions().size)
            assertEquals(1, e.funds().size)
        }
    }

    @Test
    fun loadIsRejectedOnAccountWithRealRecordsAndWritesNothing() = runBlocking {
        env().use { e ->
            e.login()
            e.addRealDeposit()
            val svc = service(e)

            assertEquals(DemoLoadResult.Rejected(DemoRejectReason.ACCOUNT_NOT_EMPTY), svc.load())
            assertTrue(e.transactions().isEmpty(), "拒绝时不得写入任何示例交易")
            assertEquals(1, e.funds().size, "拒绝时不得写入示例增资")
            assertFalse(svc.hasDemoData())
        }
    }

    @Test
    fun clearRemovesOnlyMarkedRowsAndKeepsRealRecords() = runBlocking {
        env().use { e ->
            e.login()
            val svc = service(e)
            assertEquals(DemoLoadResult.Loaded(transactions = 4, funds = 1), svc.load())

            // 真实记录（自给自足：真实增资 50,000 + 真实买入 0.5 BTC）
            e.addRealDeposit()
            e.addRealBuy()

            assertEquals(5, svc.clear(), "清除 = 4 笔示例交易 + 1 笔示例增资")

            val rows = e.transactions()
            assertEquals(1, rows.size, "真实交易必须保留")
            assertEquals("真实买入", rows.single().notes)
            val funds = e.funds()
            assertEquals(1, funds.size, "真实增资必须保留")
            assertEquals("真实增资", funds.single().notes)

            assertFalse(svc.hasDemoData())
            assertEquals(0, svc.clear(), "无示例数据时清除为 0（幂等）")
        }
    }

    @Test
    fun accountsAreIsolated() = runBlocking {
        env().use { e ->
            val svc = service(e)
            val accountA = e.login("account-a")
            assertEquals(DemoLoadResult.Loaded(transactions = 4, funds = 1), svc.load())

            e.login("account-b")
            assertFalse(svc.hasDemoData(), "示例数据不得跨账户可见")
            assertTrue(e.transactions().isEmpty())
            assertEquals(0, svc.clear(), "B 账户清除不得影响 A 账户")

            e.sessions.set(accountA)
            assertTrue(svc.hasDemoData(), "切回 A 账户示例数据仍在")
            assertEquals(4, e.transactions().size)
        }
    }

    @Test
    fun loadWithoutSessionIsTypedFailureAndReadsAreSafe() = runBlocking {
        env().use { e ->
            val svc = service(e)
            assertEquals(DemoLoadResult.Failed(DemoFailReason.NO_SESSION), svc.load())
            assertFalse(svc.hasDemoData())
            assertEquals(0, svc.clear())
        }
    }

    /**
     * 边界（`D46 §2`）：真实记录**依赖**示例增资时，示例交易可删、示例增资被既有相对校验拦住 ⇒
     * 返回已删条数并**保留**该行（`hasDemoData()` 仍为 true），**任何真实记录都不会被删除**。
     */
    @Test
    fun clearKeepsDemoDepositWhenRealRecordsDependOnIt() = runBlocking {
        env().use { e ->
            e.login()
            val svc = service(e)
            assertEquals(DemoLoadResult.Loaded(transactions = 4, funds = 1), svc.load())

            // 无真实增资，直接用示例增资买入 ⇒ 真实记录依赖示例增资
            e.addRealBuy(quantity = "0.3")

            assertEquals(4, svc.clear(), "示例交易可清除")
            assertTrue(svc.hasDemoData(), "被依赖的示例增资保留 ⇒ 仍视为存在示例数据")
            assertEquals(1, e.transactions().size, "真实买入必须保留")
            assertEquals(1, e.funds().size, "被依赖的示例增资保留")
            assertTrue(DemoData.isDemoNote(e.funds().single().notes))
            assertEquals(0, svc.clear(), "再次清除仍被拦住（不误删真实数据）")
        }
    }
}
