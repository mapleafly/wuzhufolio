package com.wuzhufolio.domain.exchange

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T6.2 纯规则验收（ExchangeSyncPolicy）：symbol 枚举收敛（余额推导 ∪ 历史已同步）、预算分批 ≤120、
 * 候选确定性排序。domain 无 IO，全部输入显式注入。
 */
class ExchangeSyncPolicyTest {

    private fun balance(asset: String, free: String = "10", locked: String = "0") =
        Balance(asset, BigDecimal(free), BigDecimal(locked))

    private fun pair(symbol: String, base: String, quote: String, status: String = "TRADING") =
        PairInfo(symbol, base, quote, status)

    @Test
    fun `balance derived picks trading pairs where base asset is held`() {
        val balances = listOf(balance("BTC"), balance("ETH"), balance("USDT", free = "0"))
        val pairs = listOf(
            pair("BTCUSDT", "BTC", "USDT"),
            pair("ETHUSDT", "ETH", "USDT"),
            pair("BTCBUSD", "BTC", "BUSD", status = "BREAK"), // 非 TRADING 不选
            pair("BNBUSDT", "BNB", "USDT"), // 未持有 BNB 不选
        )
        val derived = ExchangeSyncPolicy.balanceDerivedSymbols(balances, pairs)
        assertEquals(setOf("BTCUSDT", "ETHUSDT"), derived)
    }

    @Test
    fun `balance ignores zero total assets`() {
        val balances = listOf(balance("USDT", free = "0", locked = "0"))
        assertEquals(emptySet(), ExchangeSyncPolicy.balanceDerivedSymbols(balances, emptyList()))
    }

    @Test
    fun `candidate is union of balance derived and synced symbols sorted`() {
        val candidates = ExchangeSyncPolicy.candidateSymbols(
            balanceDerived = setOf("ETHUSDT", "BTCUSDT"),
            synced = ExchangeSyncPolicy.syncedSymbolsSet(listOf("btcusdt", "LTCUSDT")), // 大小写归一
        )
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "LTCUSDT"), candidates)
    }

    @Test
    fun `plan caps at myTrades call budget and queues the rest`() {
        val symbols = (1..250).map { "SYM" + it + "USDT" }
        val plan = ExchangeSyncPolicy.plan(symbols, emptyMap())
        assertEquals(ExchangeLimits.MAX_MY_TRADES_CALLS, plan.cursors.size)
        assertEquals(250 - ExchangeLimits.MAX_MY_TRADES_CALLS, plan.queuedSymbols)
        assertEquals(symbols.take(ExchangeLimits.MAX_MY_TRADES_CALLS), plan.cursors.map { it.symbol })
    }

    @Test
    fun `plan passes through known since ids and keeps order`() {
        val syncedIds = mapOf("BTCUSDT" to 88L, "ETHUSDT" to null)
        val plan = ExchangeSyncPolicy.plan(listOf("BTCUSDT", "ETHUSDT"), syncedIds)
        assertEquals(0, plan.queuedSymbols)
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), plan.cursors.map { it.symbol })
        assertEquals(88L, plan.cursors.first().sinceId)
        assertEquals(null, plan.cursors.last().sinceId)
    }

    @Test
    fun `small candidate list never exceeds budget`() {
        val plan = ExchangeSyncPolicy.plan(listOf("BTCUSDT"), emptyMap())
        assertEquals(1, plan.cursors.size)
        assertEquals(0, plan.queuedSymbols)
    }

    @Test
    fun `cadence allows 15 30 60 and defaults 30`() {
        assertEquals(listOf(15, 30, 60), ExchangeCadence.ALLOWED_MINUTES)
        assertEquals(30, ExchangeCadence.DEFAULT_MINUTES)
        assertTrue(15 in ExchangeCadence.ALLOWED_MINUTES)
    }
}
