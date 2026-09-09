package com.wuzhufolio.data.catalog

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.catalog.FiatLeg
import com.wuzhufolio.domain.catalog.MarketRankProvider
import com.wuzhufolio.domain.catalog.ResolveContext
import com.wuzhufolio.domain.catalog.ResolveMethod
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.catalog.UnknownCoinException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T3.1–T3.3 验收（数据层集成，M004/M005 + SqlCoinCatalog）：
 * 目录检索/UNIQUE(cg_id)/每日缓存 upsert /四级消歧（含 AUTO 固化与 MANUAL 冻结复用）/法币归一腿。
 */
class SqlCoinCatalogTest {

    private lateinit var db: WzDatabase
    private lateinit var gate: DbGate
    private lateinit var catalog: SqlCoinCatalog

    @BeforeTest
    fun setUp() {
        db = WzDatabase(Files.createTempDirectory("wuzhufolio-catalog").resolve("catalog.db"), randomDbKey())
        gate = DbGate(db)
        db.migrateToLatest()
        catalog = SqlCoinCatalog(gate)
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun entry(cgId: String, symbol: String, name: String, contracts: Map<String, String> = emptyMap()) =
        CoinDirectoryEntry(cgId, symbol, name, contracts)

    private fun fixtures(): List<CoinDirectoryEntry> = listOf(
        entry("bitcoin", "btc", "Bitcoin"),
        entry("tether", "usdt", "Tether"),
        entry("usd-coin", "usdc", "USD Coin"),
        entry("eurc", "eurc", "EURC"),
        entry("ethereum", "eth", "Ethereum", mapOf("ethereum" to "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")),
        entry("render-token", "render", "Render"),
        entry("1inch", "1inch", "1inch Network"),
        entry("aaa-token-one", "aaa", "AAA Token One"),
        entry("aaa-token-two", "aaa", "AAA Token Two"),
        entry("weth-a", "weth", "WETH A", mapOf("ethereum" to "0xabc123", "bsc" to "0xabc123")),
        entry("weth-b", "weth", "WETH B", mapOf("bsc" to "0xdef456")),
    )

    private suspend fun refreshFixtures() {
        val summary = catalog.refreshDirectory(fixtures())
        assertEquals(11, summary.added)
        assertEquals(0, summary.updated)
    }

    private suspend fun cg(cgId: String): CatalogCoin = catalog.getByCgId(cgId)!!

    // ---------- T3.1：迁移 / 检索 / UNIQUE / 每日缓存 ----------

    @Test
    fun `migration creates coins and map tables at schema v5`() = runBlocking {
        assertEquals(12, db.schemaVersion())
        assertTrue(catalog.search("btc").isEmpty(), "空库无检索结果")
        val summary = catalog.refreshDirectory(fixtures())
        assertEquals(11, summary.added)
        assertEquals(0, summary.updated)
    }

    @Test
    fun `duplicate cg_id across batches violates UNIQUE constraint`() = runBlocking {
        refreshFixtures()
        val violated = runCatching {
            db.connection.prepareStatement(
                "INSERT INTO coins(cg_id, cmc_id, symbol, name, status, display_precision, contracts, updated_at) " +
                    "VALUES ('bitcoin', NULL, 'BTC', 'Bitcoin Clone', 'ACTIVE', 8, '{}', 'now')",
            ).use { it.executeUpdate() }
        }.isFailure
        assertTrue(violated, "UNIQUE(cg_id) 必须拒绝重复 cg_id")
    }

    @Test
    fun `search normalizes case and finds by symbol prefix and name`() = runBlocking {
        refreshFixtures()
        assertEquals(listOf("tether"), catalog.search("USDT").map { it.cgId })
        assertEquals(listOf("tether"), catalog.search("usdt").map { it.cgId })
        // USD Coin（USDC 前缀命中）在前；Tether（USDT 内含 USD）按相关度殿后
        assertEquals(listOf("usd-coin", "tether"), catalog.search("usd").map { it.cgId })
        assertEquals(listOf("render-token"), catalog.search("RENDER").map { it.cgId })
        assertEquals(listOf("1inch"), catalog.search("1inch").map { it.cgId })
        // "Tether" 名称含 eth 亦命中（相关度低于精确 symbol，按名称稳定排序）
        assertEquals(listOf("ethereum", "tether", "weth-a", "weth-b"), catalog.search("eth").map { it.cgId })
        assertEquals(
            listOf("aaa-token-one", "aaa-token-two"),
            catalog.search("aaa").map { it.cgId },
        )
        assertTrue(catalog.search("zzz-nothing").isEmpty())
        assertTrue(catalog.search("").isEmpty())
        // limit 生效
        assertEquals(1, catalog.search("a", limit = 1).size)
    }

    @Test
    fun `directory refresh upserts changes and dedupes within a batch`() = runBlocking {
        refreshFixtures()
        // 变更 + 新增
        val second = catalog.refreshDirectory(
            listOf(
                entry("bitcoin", "btc", "Bitcoin Core"),
                entry("solana", "sol", "Solana"),
            ),
        )
        assertEquals(1, second.added)
        assertEquals(1, second.updated)
        assertEquals("Bitcoin Core", cg("bitcoin").name)
        assertNotNull(cg("solana"))
        // 幂等：原样重刷无变化
        val third = catalog.refreshDirectory(fixtures() + entry("solana", "sol", "Solana"))
        assertEquals(0, third.added)
        assertEquals(1, third.updated) // bitcoin 名回退为 Bitcoin
        // 同批重复 cg_id：后者覆盖，不触发约束
        val fourth = catalog.refreshDirectory(
            listOf(
                entry("bitcoin", "btc", "BTC V2"),
                entry("bitcoin", "btc", "BTC V3"),
            ),
        )
        assertEquals(0, fourth.added)
        assertEquals(1, fourth.updated)
        assertEquals("BTC V3", cg("bitcoin").name)
    }

    @Test
    fun `contracts round trip through the directory refresh`() = runBlocking {
        refreshFixtures()
        val eth = cg("ethereum")
        assertEquals("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", eth.contracts["ethereum"])
        val wethA = cg("weth-a")
        assertEquals(mapOf("ethereum" to "0xabc123", "bsc" to "0xabc123"), wethA.contracts)
    }

    @Test
    fun `cmc id daily cache updates only known coins`() = runBlocking {
        refreshFixtures()
        assertEquals(2, catalog.refreshCmcIds(mapOf("bitcoin" to "1", "tether" to "825", "unknown-coin" to "9")))
        assertEquals("1", cg("bitcoin").cmcId)
        assertEquals("825", cg("tether").cmcId)
        // 幂等重刷不计数
        assertEquals(0, catalog.refreshCmcIds(mapOf("bitcoin" to "1")))
        // 变更计数
        assertEquals(1, catalog.refreshCmcIds(mapOf("bitcoin" to "1x")))
        assertEquals("1x", cg("bitcoin").cmcId)
    }

    // ---------- T3.2：消歧归一 + 映射冻结 ----------

    @Test
    fun `unique symbol resolves and freezes AUTO mapping for reuse`() = runBlocking {
        refreshFixtures()
        val first = catalog.resolve("BINANCE", "BTC")
        val unique = assertIs<Resolution.Unique>(first)
        assertEquals("bitcoin", unique.coin.cgId)
        assertEquals(ResolveMethod.EXACT_SYMBOL, unique.method)
        // AUTO 固化：后续同资产直接复用（FROZEN_MAP），键归一大小写不敏感
        assertEquals("bitcoin", catalog.mappingFor("binance", "btc")?.cgId)
        val second = catalog.resolve("BINANCE", "btc")
        assertEquals(ResolveMethod.FROZEN_MAP, assertIs<Resolution.Unique>(second).method)
        // 无交易所上下文（手动录入路径）不做 AUTO 固化
        assertNull(catalog.mappingFor("BINANCE", "ETH"))
        catalog.resolve(null, "ETH")
        assertNull(catalog.mappingFor("BINANCE", "ETH"))
    }

    @Test
    fun `ambiguous ticker returns candidates and manual freeze persists`() = runBlocking {
        refreshFixtures()
        val amb = assertIs<Resolution.Ambiguous>(catalog.resolve("BINANCE", "AAA"))
        assertEquals(2, amb.candidates.size)
        assertNull(catalog.mappingFor("BINANCE", "AAA"), "歧义不自动固化")
        val one = cg("aaa-token-one")
        catalog.freezeMapping("BINANCE", "AAA", one.id)
        assertEquals("aaa-token-one", catalog.mappingFor("BINANCE", "AAA")?.cgId)
        val frozen = assertIs<Resolution.Unique>(catalog.resolve("BINANCE", "AAA"))
        assertEquals("aaa-token-one", frozen.coin.cgId)
        assertEquals(ResolveMethod.FROZEN_MAP, frozen.method)
        // 用户可改绑（仍 MANUAL）
        val two = cg("aaa-token-two")
        catalog.freezeMapping("BINANCE", "AAA", two.id)
        assertEquals("aaa-token-two", catalog.mappingFor("BINANCE", "AAA")?.cgId)
    }

    @Test
    fun `quote context excludes pinned coin and resolves context`() = runBlocking {
        refreshFixtures()
        val one = cg("aaa-token-one")
        val out = catalog.resolve("BINANCE", "AAA", ResolveContext(quoteCoinId = one.id))
        val unique = assertIs<Resolution.Unique>(out)
        assertEquals("aaa-token-two", unique.coin.cgId)
        assertEquals(ResolveMethod.CONTEXT, unique.method)
        assertEquals("aaa-token-two", catalog.mappingFor("BINANCE", "AAA")?.cgId)
    }

    @Test
    fun `contract address disambiguates with chain context`() = runBlocking {
        refreshFixtures()
        // 唯一链命中
        val out = catalog.resolve(
            null,
            "WETH",
            ResolveContext(contractChain = "ethereum", contractAddress = "0xABC123"),
        )
        val unique = assertIs<Resolution.Unique>(out)
        assertEquals("weth-a", unique.coin.cgId)
        assertEquals(ResolveMethod.CONTRACT, unique.method)
        // 无命中 -> NotFound（不猜测降级）
        assertEquals(
            Resolution.NotFound,
            catalog.resolve(null, "WETH", ResolveContext(contractAddress = "0xzzzz99")),
        )
    }

    @Test
    fun `market rank provider breaks ambiguity automatically`() = runBlocking {
        refreshFixtures()
        val ranked = SqlCoinCatalog(
            gate,
            rankProvider = MarketRankProvider { cgId ->
                mapOf("aaa-token-two" to 200, "aaa-token-one" to 700)[cgId]
            },
        )
        val out = ranked.resolve(null, "AAA")
        val unique = assertIs<Resolution.Unique>(out)
        assertEquals("aaa-token-two", unique.coin.cgId)
        assertEquals(ResolveMethod.RANK, unique.method)
    }

    @Test
    fun `freezeMapping rejects unknown coin id`() = runBlocking {
        refreshFixtures()
        assertFailsWith<UnknownCoinException> {
            catalog.freezeMapping("BINANCE", "AAA", 42_000)
        }
        assertNull(catalog.mappingFor("BINANCE", "AAA"))
    }

    @Test
    fun `unknown asset is NotFound`() = runBlocking {
        refreshFixtures()
        assertEquals(Resolution.NotFound, catalog.resolve("BINANCE", "NO-SUCH-COIN"))
    }

    // ---------- T3.3：法币归一腿 ----------

    @Test
    fun `fiat quote legs map USD and EUR to twin stables`() = runBlocking {
        refreshFixtures()
        val usd = assertIs<FiatLeg.TwinStable>(catalog.fiatQuoteLeg("USD"))
        assertEquals("USD", usd.fiatCode)
        assertEquals("tether", usd.stableCoin.cgId) // BTC/USD -> quote=USDT
        val eur = assertIs<FiatLeg.TwinStable>(catalog.fiatQuoteLeg("eur"))
        assertEquals("EUR", eur.fiatCode)
        assertEquals("eurc", eur.stableCoin.cgId) // EUR -> EURC
    }

    @Test
    fun `fiat without twin or missing stable falls to third currency`() = runBlocking {
        refreshFixtures()
        assertIs<FiatLeg.ThirdCurrency>(catalog.fiatQuoteLeg("GBP")) // 无孪生映射 -> 第三币种
        // 孪生稳定币不在目录的极端场景（目录只含 BTC）：退化为第三币种语义，不抛错
        WzDatabase(Files.createTempDirectory("wuzhufolio-catalog-bare").resolve("bare.db"), randomDbKey())
            .use { bareDb ->
                bareDb.migrateToLatest()
                val bare = SqlCoinCatalog(DbGate(bareDb))
                bare.refreshDirectory(listOf(entry("bitcoin", "btc", "Bitcoin")))
                assertIs<FiatLeg.ThirdCurrency>(bare.fiatQuoteLeg("USD"))
                assertIs<FiatLeg.ThirdCurrency>(bare.fiatQuoteLeg("EUR"))
            }
    }

    @Test
    fun `crypto quotes are not fiat legs`() = runBlocking {
        refreshFixtures()
        assertNull(catalog.fiatQuoteLeg("BTC"))
        assertNull(catalog.fiatQuoteLeg("USDT"))
        assertNull(catalog.fiatQuoteLeg(""))
    }

    // ---------- 检索与解析全链路 ----------

    @Test
    fun `search returns hydrated coins usable for freeze`() = runBlocking {
        refreshFixtures()
        val hit = catalog.search("usdt").first()
        assertEquals("tether", hit.cgId)
        assertTrue(hit.id > 0)
        assertEquals("Tether", hit.name)
        assertEquals(8, hit.displayPrecision)
    }

    @Test
    fun `map and catalog are global tables without account scoping`() = runBlocking {
        refreshFixtures()
        // 全局公共表：无 account_id 列（共享规范 §6/§7）
        db.connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(coins)").use { rs ->
                val names = mutableListOf<String>()
                while (rs.next()) names += rs.getString(2)
                assertTrue(!names.contains("account_id"), "coins 不得含账户隔离列: " + names)
            }
            st.executeQuery("PRAGMA table_info(exchange_coin_map)").use { rs ->
                val names = mutableListOf<String>()
                while (rs.next()) names += rs.getString(2)
                assertTrue(!names.contains("account_id"), "exchange_coin_map 不得含账户隔离列: " + names)
            }
        }
    }

    // M5 修复轮回归：CG 目录 symbol 可超 32 字符（Exposed varchar 客户端长度校验曾致整批失败）
    @Test
    fun `long symbol entries survive directory refresh`() = runBlocking {
        val longSymbol = "x".repeat(38) + "-token"
        val summary = catalog.refreshDirectory(listOf(entry("long-symbol-token", longSymbol, "Long Symbol Token")))
        assertEquals(1, summary.added)
        val hits = catalog.getBySymbol(longSymbol)
        assertEquals(1, hits.size)
        assertEquals("long-symbol-token", hits.single().cgId)
    }
}
