package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.Side
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 事件构造层（M4 遗留接线 · T7.1/CSV）：FK -> cg_id、折算价解析（前向快照 / USD 锚定 1:1 /
 * 后向估算 / 无价排除）、手续费币种三态、估算标记传播。
 */
class TransactionEventBuilderTest {

    private val t0: Instant = Instant.parse("2026-08-30T08:00:00Z")

    @Suppress("LongParameterList") // 测试行构造器（默认值 + 命名参数覆盖）
    private suspend fun row(
        env: LedgerTestEnv,
        pair: String,
        base: String,
        quote: String,
        side: Side,
        price: String,
        quantity: String,
        fee: String = "0",
        feeCurrency: String? = null,
        at: Instant = t0,
        accountId: Int,
    ): LedgerTxRow {
        val b = env.coin(base)!!
        val q = env.coin(quote)!!
        return LedgerTxRow(
            id = System.nanoTime(),
            accountId = accountId,
            exchange = "BINANCE",
            exchangeOrderId = null,
            pair = pair,
            baseCoinId = b.id,
            quoteCoinId = q.id,
            side = side,
            price = BigDecimal(price),
            quantity = BigDecimal(quantity),
            fee = BigDecimal(fee),
            feeCurrency = feeCurrency,
            time = at,
            notes = null,
            source = "CSV",
            uuid = java.util.UUID.randomUUID().toString(),
        )
    }

    @Test
    fun resolvesFkAndPegsUsdStablecoinToOne() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            val r = row(env, "BTC/USDT", "BTC", "USDT", Side.BUY, "50000", "1", accountId = accountId)
            val build = env.eventBuilder.build(listOf(r), "USD")
            assertEquals(1, build.events.size)
            val e = build.events.first()
            assertEquals("bitcoin", e.baseCoin)
            assertEquals("tether", e.quoteCoin)
            // USD 锚定稳定币 1:1：legFiat = 1 × 50000 × 1
            assertEquals(BigDecimal("50000"), e.legFiat)
            assertFalse(e.estimated, "USD 锚定 1:1 非估算")
            assertFalse(build.excludedRowIds.contains(r.id))
        }
    }

    @Test
    fun usesNearestBeforeSnapshotAndMarksAfterFallbackEstimated() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            val btc = env.coin("BTC")!!
            val eth = env.coin("ETH")!!
            // 交易时刻之前 1 小时的快照（前向精确）：pair ETH/BTC，计价腿 = BTC（真币价）
            env.putSnapshot(btc.id, "USD", "48000", t0.minusSeconds(3600))
            val before = row(env, "ETH/BTC", "ETH", "BTC", Side.BUY, "40", "2", at = t0, accountId = accountId)
            val build = env.eventBuilder.build(listOf(before), "USD")
            // legFiat = 2 × 40 × 48000
            assertEquals(0, BigDecimal("3840000").compareTo(build.events.first().legFiat))
            assertFalse(build.events.first().estimated)

            // 无前向快照、只有交易时刻之后的（后向估算，estimated）：pair BTC/ETH，计价腿 = ETH
            val later = row(
                env, "BTC/ETH", "BTC", "ETH", Side.BUY, "16", "1",
                at = t0.minusSeconds(86400), accountId = accountId,
            )
            env.putSnapshot(eth.id, "USD", "3000", t0.plusSeconds(3600))
            val build2 = env.eventBuilder.build(listOf(later), "USD")
            assertEquals(1, build2.events.size)
            assertTrue(build2.events.first().estimated, "后向估算标记 estimated")
            assertEquals(0, BigDecimal("48000").compareTo(build2.events.first().legFiat))
        }
    }

    @Test
    fun thirdCoinFeeDeductsFeeCoinAndFeePriceResolution() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            val btc = env.coin("BTC")!!
            val bnb = env.coin("BNB")!!
            env.putSnapshot(bnb.id, "USD", "600", t0.minusSeconds(3600))
            env.putSnapshot(btc.id, "USD", "50000", t0.minusSeconds(3600))
            val r = row(
                env, "BTC/USDT", "BTC", "USDT", Side.BUY, "50000", "1",
                fee = "0.05", feeCurrency = "BNB", accountId = accountId,
            )
            val build = env.eventBuilder.build(listOf(r), "USD")
            val e = build.events.first()
            assertEquals("binancecoin", e.feeCoin)
            assertTrue(e.isThirdFee)
            // feeFiat = 0.05 × 600 = 30
            assertEquals(0, BigDecimal("30.0").compareTo(e.feeFiat))
            assertFalse(e.estimated)
        }
    }

    @Test
    fun unresolvablePriceFallsBackToNominalConversionAndStaysInReplay() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            // 计价腿 ETH 无任何快照、非稳定币（无 USD 锚定）-> 名义折算（quote≈1）参与重放 + 估算标记
            // （2026-09-07 GUI 走查修复轮：排除行会使其后卖出失去成本基数，已实现盈亏大面积缺失）
            val r = row(env, "BTC/ETH", "BTC", "ETH", Side.BUY, "16", "1", accountId = accountId)
            val build = env.eventBuilder.build(listOf(r), "USD")
            assertEquals(1, build.events.size)
            val event = build.events.first()
            // legFiat = 1 × 16 × 1（名义折算）
            assertEquals(0, BigDecimal("16").compareTo(event.legFiat))
            assertTrue(event.estimated)
            assertTrue(build.estimatedRowIds.contains(r.id))
            assertTrue(build.excludedRowIds.isEmpty(), "折算价缺失不再排除行")
        }
    }

    @Test
    fun missingPriceRowKeepsFollowingSellRealizedChain() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            // 买入（ETH 计价腿无价 -> 名义折算）+ 之后卖出：卖出仍应推导已实现盈亏（数量链完整）
            val buy = row(
                env, "BTC/ETH", "BTC", "ETH", Side.BUY, "16", "1",
                at = t0, accountId = accountId,
            )
            val sell = row(
                env, "BTC/ETH", "BTC", "ETH", Side.SELL, "18", "0.5",
                at = t0.plusSeconds(60), accountId = accountId,
            )
            val build = env.eventBuilder.build(listOf(buy, sell), "USD")
            assertEquals(2, build.events.size)
            val realized = SellRealizedTracer.realizedByEvent(build.events)
            assertTrue(realized.containsKey(sell.uuid), "名义折算不阻断卖出已实现盈亏推导")
        }
    }
}
