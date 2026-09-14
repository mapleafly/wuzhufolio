package com.wuzhufolio.app.integration

import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.LoginReq
import com.wuzhufolio.domain.accounts.SwitchReq
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * **P5 集成联调 · 核心用户旅程**（AGENTS.md P5：登录 → 增资 → 交易 → 看板/ROI → 备份恢复）。
 *
 * 与 P4 各模块测试的区别：本类**不 new 任何用例服务**——全部经 [IntegrationHarness] 的**真实组合根**
 * （[com.wuzhufolio.app.AppBootstrap.run]）取用，跨模块边界（M2 账户会话 · M3 币目录 · M4 引擎 ·
 * M5 行情快照 · M7 账本 · M8 资金 · M9 备份 · M10 设置 · M12 聚合页）由真实装配链连接，
 * 库 = 真实 SQLCipher 加密库，备份 = 真实 `.cpro` 文件（真实 Argon2id + AES-256-GCM 编解码）。
 *
 * 数值轨迹（手算可核；基础法币 USD、稳定币 1:1 锚定）：
 * ```
 * 增资 100,000 USDT                             → 现金 100,000 / 投入本金(净) 100,000
 * 买入 1 BTC @ 50,000 USDT（0 手续费）           → 现金 50,000 / BTC 1（成本 50,000）
 * 卖出 0.1 BTC @ 60,000 USDT（0 手续费）         → 现金 56,000 / BTC 0.9 / 已实现 +1,000
 * CSV 导入 买入 0.5 ETH @ 3,000 USDT（0 手续费） → 现金 54,500 / ETH 0.5（成本 1,500）
 * 看板：净值 = 54,500 + 45,000 + 1,500 = 101,000 → 总收益 +1,000 / ROI +1.00%
 * ```
 */
internal class CoreJourneyIntegrationTest {

    // 集成旅程按阶段线性展开（① 账户 → ⑧ 导出），拆分反而丢失「一条主流程」的可读性
    @Suppress("LongMethod")
    @Test
    fun `核心旅程 登录 增资 交易 看板ROI 备份恢复`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            val ledger = h.runtime.transactionLedgerService
            val portfolio = h.runtime.portfolioService
            val backup = h.runtime.backupService

            // ---- ① 账户创建与登录（M2：创建即登录；记住我令牌入会话存储） ----
            val created = runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), true)) }
            assertEquals("alpha", created.account.username)
            assertTrue(auth.hasRememberMe(), "勾选记住我后应存在可恢复会话")
            assertEquals(
                created.account.id,
                runBlocking { auth.restoreSession() }?.account?.id,
                "记住我应支持免密恢复会话（重启免密路径）",
            )

            // ---- ② 增资（M8 资金事件 → M4 重放事件流） ----
            val usdt = h.coin("USDT")
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("100000"), at(1)))
            }
            val overview = runBlocking { funds.fundsOverview() }
            assertEquals(0, overview.availableCashFiat.compareTo(BigDecimal("100000")), "可用现金")
            assertEquals(0, overview.investedNetFiat.compareTo(BigDecimal("100000")), "投入本金（净）")

            // ---- ③ 交易（M7 事件构造层 → M4 相对校验 → 账本落库） ----
            saveTrade(ledger, Side.BUY, "50000", "1", at(2))
            saveTrade(ledger, Side.SELL, "60000", "0.1", at(3))

            // ---- ④ CSV 导入（M7 CSV 半边：模板形状 → 预览 → 确认落库） ----
            val csv = buildString {
                appendLine("exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes")
                appendLine("BINANCE,,ETH/USDT,买入,3000,0.5,0,USDT,2026-09-13 04:00:00,p5-csv-row")
            }
            val preview = runBlocking { ledger.parseCsv(csv.toByteArray()) }
            assertEquals(1, preview.newRows, "CSV 预览应识别 1 条新行（错误行：${preview.errorRows}）")
            assertEquals(0, preview.errorRows.size)
            assertEquals(1, runBlocking { ledger.confirmCsvImport(preview.sessionId) }.imported)
            assertEquals(3, runBlocking { ledger.listTransactions(TxFilter()) }.size)

            // ---- ⑤ 看板 / ROI（M12 聚合页数据源：全量重放 → PortfolioCalculator → 补目录与现价） ----
            val snapshot = runBlocking { portfolio.snapshot() }
            assertEquals("USD", snapshot.fiat)
            assertEquals(0, snapshot.metrics.netValueFiat.compareTo(BigDecimal("101000")), "净值")
            assertEquals(0, snapshot.metrics.availableCashFiat.compareTo(BigDecimal("54500")), "可用现金")
            assertEquals(0, snapshot.metrics.investedNetFiat.compareTo(BigDecimal("100000")), "投入本金（净）")
            assertEquals(0, snapshot.metrics.realizedPnlFiat.compareTo(BigDecimal("1000")), "累计已实现盈亏")
            assertEquals(0, snapshot.metrics.unrealizedPnlFiat.compareTo(BigDecimal("0")), "未实现盈亏")
            assertEquals(0, assertNotNull(snapshot.metrics.totalReturnFiat).compareTo(BigDecimal("1000")), "总收益")
            assertEquals(0, assertNotNull(snapshot.metrics.roiPercent).compareTo(BigDecimal("1.00")), "ROI %")
            assertEquals(
                listOf("BTC", "ETH", "USDT"),
                snapshot.rows.filter { it.quantity.signum() > 0 }.map { it.symbol }.sorted(),
                "资产列表应覆盖三币持仓",
            )
            assertEquals(emptyList(), snapshot.anomalousCoins, "主流程不应产生持仓异常")
            assertNotNull(snapshot.priceAsOf, "看板应展示行情快照时刻")
            assertEquals(
                0,
                assertNotNull(snapshot.rows.single { it.symbol == "BTC" }.marketValueFiat)
                    .compareTo(BigDecimal("45000")),
                "BTC 市值 = 0.9 × 50,000",
            )
            // 币种详情（资产列表行点击 → 详情页数据源，卖出行含逐笔已实现盈亏）
            val detail = runBlocking { portfolio.coinDetail("bitcoin") }
            assertEquals("bitcoin", detail.cgId)
            val btcRows = runBlocking { ledger.listTransactions(TxFilter(coinSymbol = "BTC")) }
            assertEquals(2, btcRows.size)
            assertEquals(0, assertNotNull(btcRows.first { it.side == Side.SELL }.realizedPnlFiat).compareTo(BigDecimal("1000")))

            // ---- ⑥ 备份导出（M9：真实 .cpro = 明文头部 + Argon2id + AES-256-GCM） ----
            val backupFile = h.dataDir.resolve("p5-journey.cpro")
            val exported = runBlocking { backup.exportBackup(backupFile, password()) }
            assertTrue(Files.size(backupFile) > 0)
            assertEquals(3, exported.counts.transactions)
            assertEquals(1, exported.counts.capitalFlows)

            // ---- ⑦ 恢复（同账户增量合并：幂等 + 保留恢复后的增量） ----
            saveTrade(ledger, Side.BUY, "40000", "0.1", at(6))
            assertEquals(4, runBlocking { ledger.listTransactions(TxFilter()) }.size)
            val beforeRestore = runBlocking { portfolio.snapshot() }

            val restorePreview = runBlocking { backup.prepareRestore(backupFile, password()) }
            assertEquals(0, restorePreview.plan.transactions.size, "合并预览：备份内 3 笔均已存在（无待插入行）")
            assertTrue(
                restorePreview.plan.duplicateSkipped >= 3,
                "备份内 3 笔交易均命中本地去重键（其余去重来自资金行/快照桶）",
            )

            val restored = runBlocking { backup.restoreBackup(backupFile, password(), RestoreMode.MERGE) }
            assertEquals(0, restored.imported.transactions, "同账户回导不应重复写入（黄金用例 10 幂等）")
            assertEquals(0, restored.imported.capitalFlows)
            assertTrue(restored.duplicateSkipped >= 3, "去重跳过计数应覆盖备份内全部交易")
            assertEquals(4, runBlocking { ledger.listTransactions(TxFilter()) }.size, "恢复后增量保留")

            // 恢复后看板与恢复前一致（重放口径无漂移）
            val after = runBlocking { portfolio.snapshot() }
            assertEquals(
                0,
                after.metrics.netValueFiat.compareTo(beforeRestore.metrics.netValueFiat),
                "恢复不改变净值（重放口径无漂移）",
            )
            assertEquals(
                0,
                assertNotNull(after.metrics.totalReturnFiat)
                    .compareTo(assertNotNull(beforeRestore.metrics.totalReturnFiat)),
                "恢复不改变总收益",
            )
            assertEquals(
                0,
                after.metrics.realizedPnlFiat.compareTo(beforeRestore.metrics.realizedPnlFiat),
                "恢复不改变累计已实现盈亏",
            )

            // ---- ⑧ 备份元数据 / CSV 明文导出（PRD §9.9 数据主权：不含任何密钥） ----
            assertNotNull(runBlocking { backup.backupMetadata() }.lastBackupAt, "导出后应记录上次备份时刻")
            val csvOut = h.dataDir.resolve("p5-transactions.csv")
            runBlocking { backup.exportCsv(CsvExportKind.TRANSACTIONS, csvOut) }
            val text = Files.readString(csvOut)
            assertTrue(text.contains("BTC"), "CSV 应含交易行")
            assertFalse(text.contains("p5-api-key"), "CSV 明文导出不得含任何密钥")
        }
    }

    // 跨账户恢复用例按「建账 → 备份 → 新账户 → 恢复 → 校验」线性展开（P5 人工验收回归场景）
    @Suppress("LongMethod")
    @Test
    fun `多账户隔离与跨账户恢复`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            val ledger = h.runtime.transactionLedgerService
            val backup = h.runtime.backupService

            // 账户 A：增资 + 三笔交易（手动买入 / 手动卖出 / CSV 导入——**均无交易所订单号**）
            // P5 人工验收回归：此前合并规划器把手动行规约为同一 (exchange|order) 键，跨账户恢复只进第一笔
            val a = runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
            val usdt = h.coin("USDT")
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("10000"), at(1)))
                ledger.saveTransaction(
                    TransactionInput(
                        "BINANCE", "BTC", "USDT", Side.BUY,
                        BigDecimal("50000"), BigDecimal("0.1"), BigDecimal.ZERO, "USDT", at(2),
                    ),
                )
                ledger.saveTransaction(
                    TransactionInput(
                        "BINANCE", "BTC", "USDT", Side.SELL,
                        BigDecimal("60000"), BigDecimal("0.05"), BigDecimal.ZERO, "USDT", at(3),
                    ),
                )
            }
            val csv = buildString {
                appendLine("exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes")
                appendLine("BINANCE,,ETH/USDT,买入,3000,0.5,0,USDT,2026-09-13 04:00:00,p5-cross-account-csv")
            }
            val preview = runBlocking { ledger.parseCsv(csv.toByteArray()) }
            assertEquals(1, runBlocking { ledger.confirmCsvImport(preview.sessionId) }.imported)

            val backupFile = h.dataDir.resolve("p5-alpha.cpro")
            val exported = runBlocking { backup.exportBackup(backupFile, password()) }
            assertEquals(1, exported.counts.capitalFlows)
            assertEquals(3, exported.counts.transactions, "备份应含三笔交易（买入/卖出/CSV）")

            // 账户 B：独立空间（零业务数据 = 多账户隔离）
            val b = runBlocking { auth.createAccount(CreateAccountReq("bravo", password(), false)) }
            assertTrue(b.account.id != a.account.id)
            assertEquals(
                emptyList(),
                runBlocking { ledger.listTransactions(TxFilter()) }.map { it.id },
                "新账户不得看到他人账本（多账户隔离）",
            )
            assertEquals(
                0,
                runBlocking { funds.fundsOverview() }.investedNetFiat.compareTo(BigDecimal.ZERO),
                "新账户投入本金为 0",
            )

            // 账户 B 恢复账户 A 的备份（跨账户恢复）
            val restored = runBlocking { backup.restoreBackup(backupFile, password(), RestoreMode.MERGE) }
            assertEquals(3, restored.imported.transactions, "跨账户恢复必须导入全部三笔（不得按订单号误判重复）")
            assertEquals(1, restored.imported.capitalFlows)
            assertEquals(0, restored.missingCoinSkipped)
            val importedRows = runBlocking { ledger.listTransactions(TxFilter()) }
            assertEquals(3, importedRows.size, "跨账户恢复后 B 看到导入账本")
            assertEquals(
                listOf(Side.BUY, Side.SELL, Side.BUY),
                importedRows.sortedBy { it.time }.map { it.side },
                "买入 / 卖出 / CSV 买入三笔齐全（顺序按时间）",
            )
            assertEquals(
                0,
                runBlocking { funds.fundsOverview() }.investedNetFiat.compareTo(BigDecimal("10000")),
                "跨账户恢复后 B 的投入本金随导入重建",
            )

            // 切回账户 A：A 的数据不受目标账户恢复影响（账户隔离双向成立）
            runBlocking { auth.switchAccount(SwitchReq(a.account.id, password())) }
            assertEquals(3, runBlocking { ledger.listTransactions(TxFilter()) }.size)
            assertEquals(
                0,
                runBlocking { funds.fundsOverview() }.investedNetFiat.compareTo(BigDecimal("10000")),
            )
        }
    }

    @Test
    fun `登出清除记住我与会话 重新登录回到同一账本`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), true)) }
            val usdt = h.coin("USDT")
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("1000"), at(1)))
            }

            runBlocking { auth.logout() }
            assertFalse(auth.hasRememberMe(), "登出后记住我令牌应清除（PRD 5.1）")
            assertEquals(null, runBlocking { auth.restoreSession() }, "登出后不可免密恢复")

            runBlocking { auth.login(LoginReq("alpha", password(), rememberMe = false)) }
            val rows = runBlocking { funds.listFunds(FundFilter()) }.rows
            assertEquals(1, rows.size, "重新登录应回到同一账户账本（增资行仍在）")
            assertEquals(FundEntryType.DEPOSIT, rows.single().entryType)
            assertEquals(0, rows.single().quantity.compareTo(BigDecimal("1000")))
        }
    }

    private fun saveTrade(
        ledger: com.wuzhufolio.domain.ledger.TransactionLedgerService,
        side: Side,
        price: String,
        quantity: String,
        time: Instant,
    ) = runBlocking {
        ledger.saveTransaction(
            TransactionInput(
                exchange = "BINANCE",
                baseSymbol = "BTC",
                quoteSymbol = "USDT",
                side = side,
                price = BigDecimal(price),
                quantity = BigDecimal(quantity),
                fee = BigDecimal.ZERO,
                feeCoinSymbol = "USDT",
                time = time,
            ),
        )
        Unit
    }

    private fun at(hour: Int): Instant = Instant.parse("2026-09-13T%02d:00:00Z".format(hour))

    /** 账户口令（每次新建数组：实现会擦除传入口令，复用同一数组会失效）。 */
    private fun password(): CharArray = "Passw0rd!".toCharArray()
}
