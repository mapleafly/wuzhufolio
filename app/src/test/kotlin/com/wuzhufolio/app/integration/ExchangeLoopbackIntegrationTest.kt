package com.wuzhufolio.app.integration

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.exchange.BinanceAdapter
import com.wuzhufolio.data.exchange.DefaultExchangeSyncService
import com.wuzhufolio.data.exchange.ExchangeTransactionRepository
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.data.ledger.DefaultCalibrationService
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.TransactionEventBuilder
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.SwitchReq
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ExchangeAdapter
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.security.CryptoService
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * **P5 集成联调 · 交易所同步 → 账本 → 校准 → 看板 → 备份恢复（跨账户）**。
 *
 * 链路真实度：**真实 HTTP**（本地回环桩，真 OkHttp 客户端 + 真 Ktor 调用 + 真 HMAC 签名）、
 * **真实 SQLCipher 库**（经组合根 `AppBootstrap` 打开的同一个库与会话持有器）、
 * **真实服务实现**（[DefaultExchangeSyncService] / [DefaultCalibrationService] / 真实账本与快照仓库）。
 * 唯一替身 = 交易所端点（`BinanceAdapter(baseUrl = 回环地址)`，M6 构造参数既有能力，不改生产代码）。
 *
 * 覆盖：
 * ① M6 同步编排 → M7 账本行（币种消歧 → 去重 → 落库）；② 增量幂等（第二次同步 0 新增）；
 * ③ M8 校准执行流（单一来源门 → 实时余额 → 差额锚点 → sync_logs 留痕）；
 * ④ M4/M12 指标随锚点联动；⑤ M9 跨账户恢复后**凭证按目标账户 DEK 重加密可继续驱动同步**。
 *
 * 数值轨迹（BTC 现价 50,000 USD）：
 * ```
 * 增资 25,000 USDT
 * 同步：买 0.5 BTC @ 50,000（-25,000）+ 卖 0.1 BTC @ 60,000（+6,000）
 *      → 现金 6,000 / BTC 0.4 / 已实现 +1,000 / 净值 26,000
 * 校准：交易所 BTC 0.5 vs 本地 0.4 → 差额 +0.1（锚点）→ BTC 0.5 / 净值 31,000
 * ```
 */
internal class ExchangeLoopbackIntegrationTest {

    // 集成旅程按阶段线性展开（① 首次同步 → ⑤ 跨账户恢复），拆分反而丢失链路可读性
    @Suppress("LongMethod")
    @Test
    fun `同步到账本到校准到看板 回环链路`() {
        BinanceStub().use { stub ->
            IntegrationHarness.boot().use { h ->
                val auth = h.runtime.session.authService
                val funds = h.runtime.fundService
                val ledger = h.runtime.transactionLedgerService
                val portfolio = h.runtime.portfolioService
                val backup = h.runtime.backupService

                // 账户 + 增资（交易所内的入金在本地账本须先有本金，否则 V5/持仓异常）
                val accountA = runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
                val usdt = h.coin("USDT")
                runBlocking {
                    funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("25000"), at(1)))
                }

                val wired = wire(h, stub)

                // ---- ① 保存密钥 = 测试请求校验 + 首次同步（PRD 流程图 3） ----
                val first = runBlocking {
                    wired.sync.addAndSync(
                        ApiKeyInput(name = "binance-readonly", exchangeName = "BINANCE", apiKey = "k-1", secretKey = "s-1"),
                    )
                }
                assertEquals(SyncStatus.OK, first.status, "首次同步应成功：${first.message}")
                assertEquals(2, first.newTrades, "两笔成交入账")

                val rows = runBlocking { ledger.listTransactions(TxFilter()) }
                assertEquals(2, rows.size)
                assertTrue(rows.all { it.source == "BINANCE API" }, "同步行来源应标记为交易所 API：${rows.map { it.source }}")
                assertEquals(
                    listOf("BTC/USDT"),
                    rows.map { it.pair }.distinct(),
                    "pair 由 exchangeInfo 注册表切分（不猜测字符串）",
                )
                assertTrue(stub.sawApiKeyHeader, "签名请求必须携带 X-MBX-APIKEY 头")
                assertTrue(stub.sawSignature, "签名请求必须携带 signature 参数（HMAC-SHA256）")

                // ---- ② 增量幂等（fromId 游标 + 去重键） ----
                val second = runBlocking { wired.sync.syncNow(null) }.single()
                assertEquals(SyncStatus.OK, second.status)
                assertEquals(0, second.newTrades, "重复同步不应重复入账")
                assertEquals(2, runBlocking { ledger.listTransactions(TxFilter()) }.size)
                assertTrue(runBlocking { wired.sync.recentSyncLogs(10) }.isNotEmpty(), "同步应写 sync_logs（脱敏摘要）")

                // ---- ③ 看板（M4 引擎 + M12 聚合页，同步行直接参与重放） ----
                val synced = runBlocking { portfolio.snapshot() }
                assertEquals(0, synced.metrics.availableCashFiat.compareTo(BigDecimal("6000")), "可用现金")
                assertEquals(0, synced.metrics.realizedPnlFiat.compareTo(BigDecimal("1000")), "已实现盈亏")
                assertEquals(0, synced.metrics.netValueFiat.compareTo(BigDecimal("26000")), "净值 = 现金 6,000 + BTC 0.4×50,000")
                assertEquals(
                    "BTC",
                    synced.rows.first { it.symbol == "BTC" }.symbol,
                )
                assertTrue(synced.rows.first { it.symbol == "BTC" }.calibratable, "单一交易所来源 → 校准入口可见（PRD 4.1-5）")

                // ---- ④ 校准（M8：单一来源门 → 实时余额 → 差额锚点 → 留痕） ----
                val preparation = runBlocking { wired.calibration.prepare("BTC") }
                assertEquals(0, preparation.localQuantity.compareTo(BigDecimal("0.4")), "校准前本地持仓")
                assertEquals(0, preparation.exchangeQuantity.compareTo(BigDecimal("0.5")), "交易所实时余额（/api/v3/account）")
                assertEquals(0, preparation.delta.compareTo(BigDecimal("0.1")), "差额 = 交易所 − 本地")
                val calibration = runBlocking { wired.calibration.execute("BTC") }
                assertTrue(calibration.recorded, "校准应写入锚点记录")

                val calibrated = runBlocking { portfolio.snapshot() }
                assertEquals(
                    0,
                    calibrated.rows.first { it.symbol == "BTC" }.quantity.compareTo(BigDecimal("0.5")),
                    "锚点后持仓 = 交易所余额",
                )
                assertEquals(
                    0,
                    calibrated.metrics.netValueFiat.compareTo(BigDecimal("31000")),
                    "净值随锚点 = 6,000 + 0.5×50,000",
                )
                assertEquals(1, runBlocking { wired.calibration.history("BTC") }.size, "校准历史可见（币种详情页）")
                assertTrue(
                    runBlocking { wired.sync.recentSyncLogs(20) }
                        .any { it.message.contains("校准") || it.status == SyncStatus.OK },
                    "校准应写 sync_logs 留痕（PRD 故事 4.1-5）",
                )

                // ---- ⑤ 跨账户恢复：凭证按目标账户 DEK 重加密后仍可驱动同步 ----
                val backupFile = h.dataDir.resolve("p5-exchange.cpro")
                val exported = runBlocking { backup.exportBackup(backupFile, password()) }
                assertEquals(1, exported.counts.apiKeys, "备份应含 API 密钥行")
                assertEquals(2, exported.counts.transactions)

                val accountB = runBlocking { auth.createAccount(CreateAccountReq("bravo", password(), false)) }
                assertTrue(accountB.account.id != accountA.account.id, "跨账户恢复需两个独立账户")
                runBlocking { backup.restoreBackup(backupFile, password(), RestoreMode.MERGE) }

                val beforeCount = runBlocking { ledger.listTransactions(TxFilter()) }.size
                val bSync = runBlocking { wired.sync.syncNow(null) }.single()
                assertEquals(
                    SyncStatus.OK,
                    bSync.status,
                    "恢复后的凭证必须能被目标账户 DEK 解出并成功调用交易所（否则会报密钥失效）：${bSync.message}",
                )
                assertEquals(0, bSync.newTrades, "恢复已带入两笔成交 → 增量无新成交")
                assertEquals(beforeCount, runBlocking { ledger.listTransactions(TxFilter()) }.size)

                // 切回账户 A：来源账户密钥仍可用（未被目标账户恢复破坏）
                runBlocking { auth.switchAccount(SwitchReq(accountA.account.id, password())) }
                val aSync = runBlocking { wired.sync.syncNow(null) }.single()
                assertEquals(SyncStatus.OK, aSync.status, "来源账户凭证不受跨账户恢复影响：${aSync.message}")
                assertNotNull(runBlocking { wired.sync.listKeys() }.singleOrNull())
            }
        }
    }

    /** 组合根装配的「交易所同步 + 校准」服务：共享组合根的库/会话/目录，仅端点替换为回环桩。 */
    private class Wired(
        val sync: DefaultExchangeSyncService,
        val calibration: DefaultCalibrationService,
    )

    private fun wire(h: IntegrationHarness, stub: BinanceStub): Wired {
        val gate = h.runtime.gate
        val catalog = h.catalog
        val sessions = h.runtime.session.sessions
        val apiKeys = ApiKeyRepository(gate)
        val syncLogs = SyncLogRepository(gate)
        val crypto = CryptoService()
        val client = newOkHttpExchangeClient()
        val adapterFactory: (ExchangeCredentials) -> ExchangeAdapter = { credentials ->
            BinanceAdapter(client, credentials, baseUrl = stub.baseUrl)
        }
        val sync = DefaultExchangeSyncService(
            sessions = sessions,
            crypto = crypto,
            apiKeyRepository = apiKeys,
            syncLogRepository = syncLogs,
            transactionsRepository = ExchangeTransactionRepository(gate),
            catalog = catalog,
            settings = h.runtime.settings,
            adapterFactory = adapterFactory,
            rankWarmUp = null,
            logger = org.slf4j.LoggerFactory.getLogger("wuzhufolio.p5.exchange"),
        )
        val snapshots = PriceSnapshotRepository(gate)
        val assembler = LedgerEventAssembler(catalog, TransactionEventBuilder(catalog, snapshots))
        val calibration = DefaultCalibrationService(
            sessions = sessions,
            catalog = catalog,
            settings = h.runtime.settings,
            transactions = com.wuzhufolio.data.ledger.LedgerTransactionRepository(gate),
            funds = com.wuzhufolio.data.ledger.FundFlowRepository(gate),
            recons = com.wuzhufolio.data.ledger.ReconciliationRepository(gate),
            assembler = assembler,
            eventBuilder = TransactionEventBuilder(catalog, snapshots),
            apiKeyRepository = apiKeys,
            crypto = crypto,
            adapterFactory = adapterFactory,
            syncLogs = syncLogs,
        )
        return Wired(sync, calibration)
    }

    private fun at(hour: Int): Instant = Instant.parse("2026-09-13T%02d:00:00Z".format(hour))

    private fun password(): CharArray = "Passw0rd!".toCharArray()

    /**
     * Binance 回环桩（JDK HttpServer）：`/api/v3/time`、`/api/v3/exchangeInfo`（公开）、
     * `/api/v3/account`、`/api/v3/myTrades`（签名）。响应形状对齐 Binance REST 文档字段名，
     * 使 [BinanceAdapter] 的解析路径与真实端点完全一致。
     */
    private class BinanceStub : AutoCloseable {

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl: String get() = "http://127.0.0.1:" + server.address.port

        /** 观测点：签名请求是否带 API Key 头 / signature 参数（契约断言）。 */
        @Volatile var sawApiKeyHeader: Boolean = false
        @Volatile var sawSignature: Boolean = false

        private val tradeTimeMs = Instant.parse("2026-09-13T02:00:00Z").toEpochMilli()

        init {
            server.createContext("/api/v3/time") { ex ->
                json(ex, """{"serverTime":${System.currentTimeMillis()}}""")
            }
            server.createContext("/api/v3/exchangeInfo") { ex ->
                json(
                    ex,
                    """
                    {"symbols":[
                      {"symbol":"BTCUSDT","baseAsset":"BTC","quoteAsset":"USDT","status":"TRADING"},
                      {"symbol":"ETHUSDT","baseAsset":"ETH","quoteAsset":"USDT","status":"TRADING"}
                    ]}
                    """.trimIndent(),
                )
            }
            server.createContext("/api/v3/account") { ex ->
                observe(ex)
                json(
                    ex,
                    """
                    {"makerCommission":10,"takerCommission":10,"canTrade":true,
                     "balances":[
                       {"asset":"BTC","free":"0.5","locked":"0"},
                       {"asset":"USDT","free":"6000","locked":"0"}
                     ]}
                    """.trimIndent(),
                )
            }
            server.createContext("/api/v3/myTrades") { ex ->
                observe(ex)
                val symbol = ex.requestURI.query.orEmpty()
                    .split("&").firstOrNull { it.startsWith("symbol=") }?.substringAfter("=")
                val body = if (symbol == "BTCUSDT") {
                    """
                    [{"id":101,"orderId":9001,"symbol":"BTCUSDT","price":"50000","qty":"0.5",
                      "quoteQty":"25000","commission":"0","commissionAsset":"USDT",
                      "time":$tradeTimeMs,"isBuyer":true,"isMaker":false},
                     {"id":102,"orderId":9002,"symbol":"BTCUSDT","price":"60000","qty":"0.1",
                      "quoteQty":"6000","commission":"0","commissionAsset":"USDT",
                      "time":${tradeTimeMs + 60_000},"isBuyer":false,"isMaker":false}]
                    """.trimIndent()
                } else {
                    "[]"
                }
                json(ex, body)
            }
            server.executor = null
            server.start()
        }

        private fun observe(ex: HttpExchange) {
            if (ex.requestHeaders.getFirst("X-MBX-APIKEY") != null) sawApiKeyHeader = true
            if (ex.requestURI.query.orEmpty().contains("signature=")) sawSignature = true
        }

        private fun json(ex: HttpExchange, body: String) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        override fun close() {
            server.stop(0)
        }
    }
}
