package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 费率规则设置（M7 GUI 走查修复轮 · 最小 CRUD）：全局/交易所增改删 + 与交易表单自动计算的端到端联动。
 */
class FeeRuleServiceTest {

    @Test
    fun globalAndExchangeRulesCrudAndResolverPriority() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val service = DefaultFeeRuleService(env.sessions, env.feeRules)

            // 初始为空
            assertTrue(service.listRules().isEmpty())

            // 全局默认
            service.saveGlobal(BigDecimal("0.20"), BigDecimal("0.25"))
            val afterGlobal = service.listRules()
            assertEquals(1, afterGlobal.size)
            assertEquals(null, afterGlobal[0].exchange)
            assertEquals(0, BigDecimal("0.20").compareTo(afterGlobal[0].buyPercent))

            // 交易所覆盖（同交易所重复保存 = 覆盖不新增）
            service.saveExchange("binance", BigDecimal("0.05"), BigDecimal("0.06"))
            service.saveExchange("BINANCE", BigDecimal("0.05"), BigDecimal("0.06"))
            val afterExchange = service.listRules()
            assertEquals(2, afterExchange.size)
            assertEquals("BINANCE", afterExchange.first().exchange, "交易所规则排在全局之前")

            // 端到端：交易表单自动计算按 交易所 > 全局
            val exchangeQuote = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "USDT", "BTC", "USDT"),
            )
            assertNotNull(exchangeQuote)
            assertEquals(0, BigDecimal("0.05").compareTo(exchangeQuote.ratePercent))
            val otherQuote = env.service.feeQuote(
                FeeQuoteRequest("COINBASE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "USDT", "BTC", "USDT"),
            )
            assertNotNull(otherQuote)
            assertEquals(0, BigDecimal("0.20").compareTo(otherQuote.ratePercent), "未配置交易所回落全局")

            // 删除交易所规则 -> 回落全局
            val exchangeId = afterExchange.first { it.exchange != null }.id
            service.removeRule(exchangeId)
            assertEquals(1, service.listRules().size)
            val fallback = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.BUY, BigDecimal("0.1"), BigDecimal("50000"), "USDT", "BTC", "USDT"),
            )
            assertNotNull(fallback)
            assertEquals(0, BigDecimal("0.20").compareTo(fallback.ratePercent))
        }
    }

    @Test
    fun invalidRatesRejected() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val service = DefaultFeeRuleService(env.sessions, env.feeRules)
            assertFailsWith<IllegalArgumentException> {
                service.saveGlobal(BigDecimal("-1"), BigDecimal("0.1"))
            }
            assertFailsWith<IllegalArgumentException> {
                service.saveExchange("", BigDecimal("0.1"), BigDecimal("0.1"))
            }
            assertTrue(service.listRules().isEmpty(), "非法费率不得落库")
        }
    }

    @Test
    fun sellRateIsUsedForSellSide() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val service = DefaultFeeRuleService(env.sessions, env.feeRules)
            service.saveGlobal(BigDecimal("0.10"), BigDecimal("0.30"))
            val sell = env.service.feeQuote(
                FeeQuoteRequest("BINANCE", Side.SELL, BigDecimal("1"), BigDecimal("60000"), "USDT", "BTC", "USDT"),
            )
            assertNotNull(sell)
            assertEquals(0, BigDecimal("0.30").compareTo(sell.ratePercent))
            // 费率 × 总价 = 0.003 × 60000 = 180
            assertEquals(0, BigDecimal("180").compareTo(sell.coinQty))
        }
    }
}
