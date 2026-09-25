package com.wuzhufolio.data.catalog

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **DEF-51** 统一候选检索（2026-09-24 人工拍板 C1）。
 *
 * 人工反馈第 4/8/9/10 条：交易表单 / 资金四窗 / 行情搜索 三处「输入币种出候选」表现不一致，
 * 且默认币（USD→USDT）只有资金页置顶。本测试钉住统一口径：
 * ① 命中查询时默认币置顶；② 不命中则不置顶；③ 置顶不挤掉正常候选（先放大再裁剪）；④ 空查询/无结果为空。
 */
class PinnedCoinSearchTest {

    private lateinit var db: WzDatabase
    private lateinit var gate: DbGate
    private lateinit var catalog: SqlCoinCatalog

    @BeforeTest
    fun setUp() {
        db = WzDatabase(Files.createTempDirectory("wuzhufolio-pinned").resolve("pinned.db"), randomDbKey())
        gate = DbGate(db)
        db.migrateToLatest()
        catalog = SqlCoinCatalog(gate)
        runBlocking {
            catalog.refreshDirectory(
                listOf(
                    CoinDirectoryEntry("tether", "usdt", "Tether"),
                    CoinDirectoryEntry("bridged-usdt-a", "usdt", "AAA Bridged USDT"),
                    CoinDirectoryEntry("bridged-usdt-b", "usdt", "BBB Bridged USDT"),
                    CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
                    CoinDirectoryEntry("usd-coin", "usdc", "USD Coin"),
                ),
            )
        }
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun search(fiat: String) = PinnedCoinSearch(catalog) { fiat }

    @Test
    fun `default coin is pinned first when it matches the query`() = runBlocking {
        val hits = search("USD").search("usdt", limit = 5)
        assertEquals("tether", hits.first().cgId, "USD 基础法币 → USDT(tether) 置顶")
    }

    @Test
    fun `default coin is not pinned when it does not match the query`() = runBlocking {
        val hits = search("USD").search("btc", limit = 5)
        assertEquals("bitcoin", hits.first().cgId)
        assertTrue(hits.none { it.cgId == "tether" }, "不命中查询的默认币不得插入候选")
    }

    @Test
    fun `non-usd fiat pins usdc instead`() = runBlocking {
        val hits = search("EUR").search("usdc", limit = 5)
        assertEquals("usd-coin", hits.first().cgId, "非 USD 基础法币 → USDC 置顶（PRD §9.8）")
    }

    @Test
    fun `pinning does not drop other candidates within the limit`() = runBlocking {
        val hits = search("USD").search("usdt", limit = 3)
        assertEquals(3, hits.size, "置顶后仍应填满 limit（先放大检索再裁剪）")
        assertEquals(3, hits.map { it.cgId }.distinct().size, "置顶币不得重复出现")
    }

    @Test
    fun `blank query and no hits return empty`() = runBlocking {
        assertEquals(emptyList(), search("USD").search("   ", limit = 5))
        assertEquals(emptyList(), search("USD").search("zzz-not-a-coin", limit = 5))
    }

    @Test
    fun `default coin cg id follows the base fiat`() {
        assertEquals("tether", PinnedCoinSearch.defaultCoinCgId("USD"))
        assertEquals("tether", PinnedCoinSearch.defaultCoinCgId(null))
        assertEquals("usd-coin", PinnedCoinSearch.defaultCoinCgId("EUR"))
        assertEquals("usd-coin", PinnedCoinSearch.defaultCoinCgId("CNY"))
    }
}
