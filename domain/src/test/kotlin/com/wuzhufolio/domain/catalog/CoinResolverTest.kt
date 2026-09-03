package com.wuzhufolio.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T3.2 验收（规则层）：四级消歧 + 候选/NotFound 语义（共享规范 §6，规则纯函数单测）。 */
class CoinResolverTest {

    private fun coin(
        id: Long,
        cgId: String,
        symbol: String,
        name: String,
        contracts: Map<String, String> = emptyMap(),
    ) = CatalogCoin(
        id = id,
        cgId = cgId,
        cmcId = null,
        symbol = symbol,
        name = name,
        status = CoinStatus.ACTIVE,
        contracts = contracts,
    )

    // ---------- 基准语义 ----------

    @Test
    fun `unique symbol resolves as exact symbol`() {
        val btc = coin(1, "bitcoin", "BTC", "Bitcoin")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(btc)))
        assertIs<Resolution.Unique>(out)
        assertEquals(btc, out.coin)
        assertEquals(ResolveMethod.EXACT_SYMBOL, out.method)
    }

    @Test
    fun `single non-active candidate still resolves (legacy asset mapping)`() {
        val delisted = CatalogCoin(1, "gone-coin", null, "GONE", "Gone Coin", CoinStatus.DELISTED)
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(delisted)))
        assertIs<Resolution.Unique>(out)
        assertEquals(CoinStatus.DELISTED, out.coin.status)
    }

    @Test
    fun `no candidates is NotFound`() {
        assertEquals(Resolution.NotFound, CoinResolver.resolve(CoinResolver.RuleInput(emptyList())))
    }

    @Test
    fun `active candidates win over non-active ones`() {
        val delisted = CatalogCoin(1, "x-old", null, "X", "X Old", CoinStatus.DELISTED)
        val active = coin(2, "x-new", "X", "X New")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(delisted, active)))
        assertIs<Resolution.Unique>(out)
        assertEquals("x-new", out.coin.cgId)
    }

    // ---------- 规则①：交易对上下文 ----------

    @Test
    fun `quote context excludes same-coin candidate and resolves context`() {
        val one = coin(1, "aaa-token-one", "AAA", "AAA Token One")
        val two = coin(2, "aaa-token-two", "AAA", "AAA Token Two")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(one, two), quoteCoinId = one.id))
        assertIs<Resolution.Unique>(out)
        assertEquals("aaa-token-two", out.coin.cgId)
        assertEquals(ResolveMethod.CONTEXT, out.method)
    }

    @Test
    fun `base equal to pinned quote coin is a degenerate pair NotFound`() {
        val one = coin(1, "aaa-token-one", "AAA", "AAA Token One")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(one), quoteCoinId = one.id))
        assertEquals(Resolution.NotFound, out)
    }

    @Test
    fun `context exclusion narrowing keeps ambiguity`() {
        val quote = coin(1, "aaa-token-one", "AAA", "AAA Token One")
        val a = coin(2, "aaa-token-a", "AAA", "AAA Token A")
        val b = coin(3, "aaa-token-b", "AAA", "AAA Token B")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(quote, a, b), quoteCoinId = quote.id))
        assertIs<Resolution.Ambiguous>(out)
        assertEquals(listOf("aaa-token-a", "aaa-token-b"), out.candidates.map { it.cgId })
    }

    @Test
    fun `unique candidate with unrelated pinned quote stays exact symbol`() {
        val btc = coin(1, "bitcoin", "BTC", "Bitcoin")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(btc), quoteCoinId = 99))
        assertIs<Resolution.Unique>(out)
        assertEquals(ResolveMethod.EXACT_SYMBOL, out.method)
    }

    // ---------- 规则②：合约地址精确匹配 ----------

    private val wethA = coin(
        10, "weth-a", "WETH", "WETH A",
        mapOf("ethereum" to "0xabc123", "bsc" to "0xabc123"),
    )
    private val wethB = coin(11, "weth-b", "WETH", "WETH B", mapOf("bsc" to "0xdef456"))

    @Test
    fun `contract address exact match resolves ignoring case`() {
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(wethA, wethB), contractAddress = "0xABC123"))
        assertIs<Resolution.Unique>(out)
        assertEquals("weth-a", out.coin.cgId)
        assertEquals(ResolveMethod.CONTRACT, out.method)
    }

    @Test
    fun `contract chain filter excludes other chains`() {
        val out = CoinResolver.resolve(
            CoinResolver.RuleInput(listOf(wethA, wethB), contractAddress = "0xdef456", contractChain = "ethereum"),
        )
        assertEquals(Resolution.NotFound, out) // 0xdef456 仅存在于 bsc 链
    }

    @Test
    fun `address matching any chain when chain unknown`() {
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(wethA, wethB), contractAddress = "0xdef456"))
        assertIs<Resolution.Unique>(out)
        assertEquals("weth-b", out.coin.cgId)
    }

    @Test
    fun `contract address without any hit is NotFound - no guessing fallback`() {
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(wethA, wethB), contractAddress = "0xzzzz99"))
        assertEquals(Resolution.NotFound, out)
    }

    @Test
    fun `multiple contract hits narrow the pool for later levels`() {
        val c1 = coin(20, "dup-a", "DUP", "Dup A", mapOf("ethereum" to "0x777"))
        val c2 = coin(21, "dup-b", "DUP", "Dup B", mapOf("ethereum" to "0x777"))
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(c1, c2), contractAddress = "0x777"))
        assertIs<Resolution.Ambiguous>(out)
        assertEquals(listOf("dup-a", "dup-b"), out.candidates.map { it.cgId })
    }

    // ---------- 规则③：市值排名 ----------

    @Test
    fun `ranked unique top candidate wins by rank`() {
        val one = coin(1, "rank-one", "RANKX", "Rank One")
        val two = coin(2, "rank-two", "RANKX", "Rank Two")
        val out = CoinResolver.resolve(
            CoinResolver.RuleInput(listOf(one, two), rankOf = { if (it.cgId == "rank-two") 200 else null }),
        )
        assertIs<Resolution.Unique>(out)
        assertEquals("rank-two", out.coin.cgId)
        assertEquals(ResolveMethod.RANK, out.method)
    }

    @Test
    fun `smallest rank number wins`() {
        val one = coin(1, "rank-one", "RANKX", "Rank One")
        val two = coin(2, "rank-two", "RANKX", "Rank Two")
        val out = CoinResolver.resolve(
            CoinResolver.RuleInput(listOf(one, two), rankOf = { mapOf("rank-one" to 40, "rank-two" to 700)[it.cgId] }),
        )
        assertIs<Resolution.Unique>(out)
        assertEquals("rank-one", out.coin.cgId)
    }

    @Test
    fun `no ranked candidate falls through to ambiguous`() {
        val one = coin(1, "rank-one", "RANKX", "Rank One")
        val two = coin(2, "rank-two", "RANKX", "Rank Two")
        val out = CoinResolver.resolve(CoinResolver.RuleInput(listOf(one, two)))
        assertIs<Resolution.Ambiguous>(out)
        assertEquals(2, out.candidates.size)
    }

    @Test
    fun `tied ranks cannot discriminate and stay ambiguous`() {
        val one = coin(1, "rank-one", "RANKX", "Rank One")
        val two = coin(2, "rank-two", "RANKX", "Rank Two")
        val out = CoinResolver.resolve(
            CoinResolver.RuleInput(listOf(one, two), rankOf = { 5 }),
        )
        assertIs<Resolution.Ambiguous>(out)
        assertEquals(2, out.candidates.size)
    }

    // ---------- 规则④：候选呈现 ----------

    @Test
    fun `ambiguous candidates ordered by rank then name`() {
        val zed = coin(1, "aaa-zed", "AAA", "Zed Coin")
        val alpha = coin(2, "aaa-alpha", "AAA", "Alpha Coin")
        val beta = coin(3, "aaa-beta", "AAA", "Beta Coin")
        // 唯一最小排名会直接命中规则③（RANK），故此处构造并列最小排名 + 一个无排名 -> 第四级候选
        val out = CoinResolver.resolve(
            CoinResolver.RuleInput(
                listOf(zed, alpha, beta),
                rankOf = { coin -> mapOf("aaa-zed" to 5, "aaa-alpha" to 5)[coin.cgId] },
            ),
        )
        assertIs<Resolution.Ambiguous>(out)
        // 并列排名者在前（按名称稳定排序），无排名者殿后
        assertEquals(listOf("aaa-alpha", "aaa-zed", "aaa-beta"), out.candidates.map { it.cgId })
    }

    @Test
    fun `market-rank stub provider and default noop provider stay consistent`() {
        assertTrue(NoopMarketRankProvider.rankOf("bitcoin") == null)
        val ranked = MarketRankProvider { if (it == "bitcoin") 1 else null }
        assertEquals(1, ranked.rankOf("bitcoin"))
    }
}
