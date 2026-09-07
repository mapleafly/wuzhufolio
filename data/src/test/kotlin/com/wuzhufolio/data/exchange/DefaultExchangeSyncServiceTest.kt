package com.wuzhufolio.data.exchange

import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.ExchangeTrade
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.exchange.PairInfo
import com.wuzhufolio.domain.exchange.TradeSide
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 可脚本化假适配器（记录调用次数；按 symbol 喂成交）。 */
private class FakeExchangeAdapter : com.wuzhufolio.domain.exchange.ExchangeAdapter {
    override val exchangeName: String = "BINANCE"
    var balances: List<com.wuzhufolio.domain.exchange.Balance> = emptyList()
    var pairs: List<PairInfo> = emptyList()
    val tradesBySymbol: MutableMap<String, MutableList<ExchangeTrade>> = mutableMapOf()
    var myTradesCalls = 0
    var invalid = false
    var tradeLimitReached: Boolean = false
    /** 关键级拉取错误（修复轮：余额/注册表成功但 myTrades 失败 → 应中止并上浮原因）。 */
    var tradesError: com.wuzhufolio.domain.exchange.ExchangeError? = null

    override suspend fun validateCredentials() {
        if (invalid) throw com.wuzhufolio.domain.exchange.ExchangeApiException(ExchangeError.InvalidKey)
    }

    override suspend fun fetchBalances(): List<com.wuzhufolio.domain.exchange.Balance> {
        if (invalid) throw com.wuzhufolio.domain.exchange.ExchangeApiException(ExchangeError.InvalidKey)
        return balances
    }

    override suspend fun fetchTrades(symbol: String, sinceId: Long?, limit: Int): List<ExchangeTrade> {
        myTradesCalls++
        if (invalid) throw com.wuzhufolio.domain.exchange.ExchangeApiException(ExchangeError.InvalidKey)
        tradesError?.let { throw com.wuzhufolio.domain.exchange.ExchangeApiException(it) }
        return tradesBySymbol[symbol]?.filter { sinceId == null || it.id > sinceId } ?: emptyList()
    }

    override suspend fun fetchPairs(): List<PairInfo> = pairs
}

private fun trade(id: Long, symbol: String, side: TradeSide, price: String, qty: String): ExchangeTrade =
    ExchangeTrade(
        id = id, orderId = id, symbol = symbol, side = side,
        price = BigDecimal(price), qty = BigDecimal(qty), fee = BigDecimal("0.001"),
        feeAsset = "BNB", time = Instant.parse("2026-09-01T08:00:00Z").plusSeconds(id),
    )

/**
 * M6 同步编排验收（FakeAdapter + 真实 SQLCipher）：addAndSync 保存即首次同步、
 * 重复同步不重复（增量去重）、余额推导 ∪ 已同步 pair 枚举、预算分批部分同步注明、
 * 密钥失效 → FAILED + sync_logs 脱敏、sync 间隔持久化。
 */
class DefaultExchangeSyncServiceTest {

    private val env = ExchangeTestEnv()
    private lateinit var adapter: FakeExchangeAdapter

    private fun service(): DefaultExchangeSyncService {
        adapter = FakeExchangeAdapter()
        return DefaultExchangeSyncService(
            sessions = env.sessions,
            crypto = env.crypto,
            apiKeyRepository = env.apiKeys,
            syncLogRepository = env.syncLogs,
            transactionsRepository = env.transactions,
            catalog = env.catalog,
            settings = env.settings,
            adapterFactory = { _: ExchangeCredentials -> adapter },
        )
    }

    @AfterTest
    fun tearDown() = env.close()

    @Test
    fun `add and sync imports new trades with dedupe then repeat sync does not duplicate`() =
        kotlinx.coroutines.runBlocking {
        env.loginAccount()
        val svc = service()
        // 余额 BTC/USDT → 候选 BTCUSDT；目录含 BTC/USDT/BNB
        adapter.balances = listOf(com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("1"), BigDecimal.ZERO))
        adapter.pairs = listOf(
            PairInfo("BTCUSDT", "BTC", "USDT", "TRADING"),
            PairInfo("BNBUSDT", "BNB", "USDT", "TRADING"),
        )
        adapter.tradesBySymbol["BTCUSDT"] = mutableListOf(
            trade(100, "BTCUSDT", TradeSide.BUY, "50000", "0.5"),
            trade(101, "BTCUSDT", TradeSide.BUY, "51000", "0.5"),
        )

        val first = svc.addAndSync(
            com.wuzhufolio.domain.exchange.ApiKeyInput("主号", "BINANCE", "ak", "sk"))
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.OK, first.status)
        assertEquals(2, first.newTrades)
        assertEquals(2, env.transactions.countByAccount(env.sessions.get()!!.account.id))

        // 重复同步：不再新增（同一 symbol 无新成交）；新成交只增量
        adapter.tradesBySymbol["BTCUSDT"]!!.add(trade(102, "BTCUSDT", TradeSide.SELL, "52000", "0.5"))
        val second = svc.syncNow().single()
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.OK, second.status)
        assertEquals(1, second.newTrades, "只有新增成交被导入")
        assertEquals(0, second.duplicatesSkipped)
        val rows = env.transactions.syncedPairs(env.sessions.get()!!.account.id, "BINANCE")
        assertEquals(1, rows.size)
        assertEquals(102L, rows[0].maxOrderId)
        // 第三次同步：增量游标（fromId = 最大成交 id + 1）拉不到新成交 → 新增 0 且无去重计数
        // （「去重跳过」仅在交易所返回已同步过的成交时出现——幂等证明是「新增 0」，三轮后人工问询口径）
        val third = svc.syncNow(first.apiKeyId).single()
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.OK, third.status)
        assertEquals(0, third.newTrades)
        assertTrue(third.message.contains("增量无新成交"), third.message)
        assertEquals(3, env.transactions.countByAccount(env.sessions.get()!!.account.id))
        // 同步间隔缺省 30，可改 15/60
        assertEquals(30, svc.syncIntervalMinutes())
        svc.saveSyncIntervalMinutes(15)
        assertEquals(15, svc.syncIntervalMinutes())
    }

    @Test
    fun `balance derived plus already synced symbols both enumerated`() = kotlinx.coroutines.runBlocking {
        env.loginAccount()
        val svc = service()
        val accountId = env.sessions.get()!!.account.id
        // 先手工写入一笔历史同步（代表已同步 pair：ETHUSDT）
        val eth = kotlinx.coroutines.runBlocking { env.catalog.getBySymbol("ETH").single().id }.toInt()
        val usdt = kotlinx.coroutines.runBlocking { env.catalog.getBySymbol("USDT").single().id }.toInt()
        env.transactions.insertIfAbsent(
            ImportedTradeRow(accountId, "BINANCE", "50", "ETH/USDT", eth, usdt, "BUY",
                BigDecimal("3000"), BigDecimal("1"), BigDecimal("0.001"), "BNB",
                Instant.parse("2026-09-01T06:00:00Z")))
        adapter.balances = listOf(com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("1"), BigDecimal.ZERO))
        adapter.pairs = listOf(
            PairInfo("BTCUSDT", "BTC", "USDT", "TRADING"),
            PairInfo("ETHUSDT", "ETH", "USDT", "TRADING"),
        )
        adapter.tradesBySymbol["BTCUSDT"] = mutableListOf(trade(1, "BTCUSDT", TradeSide.BUY, "50000", "1"))
        adapter.tradesBySymbol["ETHUSDT"] = mutableListOf(trade(51, "ETHUSDT", TradeSide.BUY, "3001", "0.5"))
        val key = env.apiKeys.create(accountId, "k", "BINANCE", { rowId ->
            val id = accountId.toString()
            ApiKeyCiphers(
                env.crypto.encryptField("ak", env.sessions.get()!!.dek, id, rowId.toString(), "api_key"),
                env.crypto.encryptField("sk", env.sessions.get()!!.dek, id, rowId.toString(), "secret_key"),
            )
        })
        val result = svc.syncNow(key.id.toLong()).single()
        assertEquals(2, result.newTrades, "余额推导 BTCUSDT + 已同步 ETHUSDT 都应被拉取")
        assertEquals(2, adapter.myTradesCalls)
    }

    @Test
    fun `invalid credentials marks failed and sync log message is redacted`() = kotlinx.coroutines.runBlocking {
        env.loginAccount()
        val svc = service()
        adapter.invalid = true
        val key = env.apiKeys.create(env.sessions.get()!!.account.id, "bad", "BINANCE", { rowId ->
            val id = env.sessions.get()!!.account.id.toString()
            ApiKeyCiphers(
                env.crypto.encryptField("ak", env.sessions.get()!!.dek, id, rowId.toString(), "api_key"),
                env.crypto.encryptField("sk", env.sessions.get()!!.dek, id, rowId.toString(), "secret_key"),
            )
        })
        val result = svc.syncNow(key.id.toLong()).single()
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.FAILED, result.status)
        assertTrue(result.error is ExchangeError.InvalidKey)
        val keyRow = env.apiKeys.findById(env.sessions.get()!!.account.id, key.id)
        assertNotNull(keyRow)
        assertEquals("FAILED", keyRow.status)
        val logs = env.syncLogs.recent(env.sessions.get()!!.account.id, 5)
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.FAILED, logs[0].status)
        // 脱敏：密文/密钥明文不入库
        assertTrue(!logs[0].message.contains("ak") || logs[0].message.length < 10)
        assertTrue(!logs[0].message.contains("sk"))
    }

    @Test
    fun `key level fetch error aborts run and surfaces real reason in message`() = kotlinx.coroutines.runBlocking {
        env.loginAccount()
        val svc = service()
        adapter.balances = listOf(com.wuzhufolio.domain.exchange.Balance("BTC", BigDecimal("1"), BigDecimal.ZERO))
        adapter.pairs = listOf(PairInfo("BTCUSDT", "BTC", "USDT", "TRADING"))
        // 余额/注册表成功，但 myTrades 抛限流 → 首 symbol 即中止，不再打满预算
        adapter.tradesError = ExchangeError.RateLimited
        val key = env.apiKeys.create(env.sessions.get()!!.account.id, "k", "BINANCE", { rowId ->
            val id = env.sessions.get()!!.account.id.toString()
            ApiKeyCiphers(
                env.crypto.encryptField("ak", env.sessions.get()!!.dek, id, rowId.toString(), "api_key"),
                env.crypto.encryptField("sk", env.sessions.get()!!.dek, id, rowId.toString(), "secret_key"),
            )
        })
        val result = svc.syncNow(key.id.toLong()).single()
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.FAILED, result.status)
        assertTrue(result.error is ExchangeError.RateLimited, "关键级错误应上浮：" + result.error)
        assertEquals(1, adapter.myTradesCalls, "关键级错误后应立即中止，不再继续拉取")
        assertTrue(result.message.contains("同步中止"), result.message)
        assertTrue(result.message.contains("请求失败 1"), result.message)
        val logs = env.syncLogs.recent(env.sessions.get()!!.account.id, 5)
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.FAILED, logs[0].status)
        assertTrue(logs[0].message.contains("限流"), "sync_logs 应含真实原因: " + logs[0].message)
    }

    @Test
    @Suppress("LongMethod")
    fun `ambiguous ticker resolves via rank warm-up and imports trades`() = kotlinx.coroutines.runBlocking {
        service() // 初始化共享 FakeAdapter（lateinit）
        // CG 目录里 BNB 同名资产 2 个（binancecoin + BNB48）——人工门实测样本根因
        val provider = object : com.wuzhufolio.domain.catalog.MarketRankProvider {
            var ranks: Map<String, Int> = emptyMap()
            override fun rankOf(cgId: String): Int? = ranks[cgId]
        }
        val catalog = com.wuzhufolio.data.catalog.SqlCoinCatalog(env.gate, provider)
        kotlinx.coroutines.runBlocking {
            catalog.refreshDirectory(
                listOf(
                    com.wuzhufolio.domain.catalog.CoinDirectoryEntry("binancecoin", "bnb", "BNB"),
                    com.wuzhufolio.domain.catalog.CoinDirectoryEntry("bnb48-club-token", "bnb", "BNB48 Club Token"),
                    com.wuzhufolio.domain.catalog.CoinDirectoryEntry("tether", "usdt", "Tether"),
                ),
            )
        }
        var warmUps = 0
        val svc = DefaultExchangeSyncService(
            sessions = env.sessions,
            crypto = env.crypto,
            apiKeyRepository = env.apiKeys,
            syncLogRepository = env.syncLogs,
            transactionsRepository = env.transactions,
            catalog = catalog,
            settings = env.settings,
            adapterFactory = { _: ExchangeCredentials -> adapter },
            rankWarmUp = {
                warmUps++
                provider.ranks = mapOf("binancecoin" to 4) // 预热：市值榜注入（BNB48 不在榜）
            },
        )
        env.loginAccount()
        adapter.balances = listOf(com.wuzhufolio.domain.exchange.Balance("BNB", BigDecimal("10"), BigDecimal.ZERO))
        adapter.pairs = listOf(PairInfo("BNBUSDT", "BNB", "USDT", "TRADING"))
        adapter.tradesBySymbol["BNBUSDT"] = mutableListOf(
            trade(1, "BNBUSDT", TradeSide.BUY, "600", "2"),
            trade(2, "BNBUSDT", TradeSide.SELL, "610", "1"),
        )
        val key = env.apiKeys.create(env.sessions.get()!!.account.id, "bnb", "BINANCE", { rowId ->
            val id = env.sessions.get()!!.account.id.toString()
            ApiKeyCiphers(
                env.crypto.encryptField("ak", env.sessions.get()!!.dek, id, rowId.toString(), "api_key"),
                env.crypto.encryptField("sk", env.sessions.get()!!.dek, id, rowId.toString(), "secret_key"),
            )
        })

        // 无预热回调的行为对照：歧义全部跳过
        val noWarm = DefaultExchangeSyncService(
            sessions = env.sessions,
            crypto = env.crypto,
            apiKeyRepository = env.apiKeys,
            syncLogRepository = env.syncLogs,
            transactionsRepository = env.transactions,
            catalog = catalog,
            settings = env.settings,
            adapterFactory = { _: ExchangeCredentials -> adapter },
        )
        val skipped = noWarm.syncNow(key.id.toLong()).single()
        assertEquals(0, skipped.newTrades)
        assertEquals(2, skipped.unresolvedSkipped, "无排名时歧义资产按规范跳过")

        // 带预热：规则③按市值排名裁定 binancecoin → 成交入账
        val result = svc.syncNow(key.id.toLong()).single()
        assertEquals(1, warmUps, "预热每轮至多一次")
        assertEquals(com.wuzhufolio.domain.exchange.SyncStatus.OK, result.status)
        assertEquals(2, result.newTrades, "歧义经排名消歧后成交入账")
        assertEquals(0, result.unresolvedSkipped)
    }
}
