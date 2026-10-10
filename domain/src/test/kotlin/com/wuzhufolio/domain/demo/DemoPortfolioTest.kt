package com.wuzhufolio.domain.demo

import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.LedgerEvent
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import com.wuzhufolio.domain.engine.assertMoney
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 示例数据集自洽性与标记（B1 · `D46` · T15.3）——**纯函数**用例（无库、无网络）：
 *
 * 1. **STRICT 重放自洽**：手动保存路径走 `ReplayEngine.validateMutation` 相对校验，拦截「本笔新制造」
 *    的负边界 ⇒ 数据集必须「先增资、再买入、卖出量不超过持仓」。本用例把数据集映射为事件后按
 *    [NegativePolicy.STRICT] 全量重放，**不得抛负持仓异常**，并锁定期末持仓与已实现盈亏；
 * 2. **标记**：全部备注带 [DemoData.MARKER] 前缀（幂等与清除的唯一依据）；
 * 3. **确定性**：给定 `now` ⇒ 输入完全确定且时间升序（落库顺序 = 重放顺序）。
 */
class DemoPortfolioTest {

    /** 币种符号 -> cg id（引擎键；运行期由事件构造层做 FK→cg_id 解析，此处直接给出）。 */
    private val cgId = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "USDT" to "tether",
    )

    private val now: Instant = Instant.parse("2026-10-10T12:00:00Z")

    /** 数据集 -> 引擎事件（legFiat = 数量×价格×1、feeFiat = 手续费×1：计价腿 USDT 按 1:1 锚定）。 */
    private fun events(): List<LedgerEvent> {
        val funds = DemoPortfolio.fundInputs(now).mapIndexed { i, f ->
            FundEvent(
                id = "demo-fund-$i",
                at = f.time,
                coin = cgId.getValue(f.coinSymbol),
                kind = f.kind,
                quantity = f.quantity,
                fiatValue = f.quantity,
                seq = i.toLong(),
            )
        }
        val trades = DemoPortfolio.transactionInputs(now).mapIndexed { i, t ->
            TradeEvent(
                id = "demo-tx-$i",
                at = t.time,
                baseCoin = cgId.getValue(t.baseSymbol),
                quoteCoin = cgId.getValue(t.quoteSymbol),
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                fee = t.fee,
                feeCoin = cgId.getValue(t.feeCoinSymbol),
                legFiat = t.price.multiply(t.quantity),
                feeFiat = t.fee,
                source = RecordSource.Manual,
                seq = (100 + i).toLong(),
            )
        }
        return funds + trades
    }

    @Test
    fun `dataset replays strictly with expected holdings and realized pnl`() {
        val outcome = ReplayEngine.replay(events(), NegativePolicy.STRICT)

        // 自洽：无任何负持仓边界
        assertTrue(outcome.violations.isEmpty(), "示例数据集不得制造负持仓边界")
        assertTrue(outcome.anomalousCoins.isEmpty(), "示例数据集不得出现持仓异常币种")

        // 期末持仓（USDT 1:1 锚定口径）
        assertMoney("40952.5", outcome.holdings.getValue("tether").quantity, "USDT")
        assertMoney("0.8", outcome.holdings.getValue("bitcoin").quantity, "BTC")
        assertMoney("5", outcome.holdings.getValue("ethereum").quantity, "ETH")
        assertMoney("40", outcome.holdings.getValue("solana").quantity, "SOL")

        // 累计增资 / 撤资 / 已实现盈亏
        assertMoney("100000", outcome.cumulativeDepositsFiat, "deposits")
        assertMoney("0", outcome.cumulativeWithdrawalsFiat, "withdrawals")
        // 卖出 0.2 BTC @60,000（费 12）− 0.2 × 均价 50,025 = 11,988 − 10,005
        assertMoney("1983", outcome.realizedPnlFiat, "realized pnl")
    }

    @Test
    fun `dataset shape is one deposit plus three buys and one sell over three coins`() {
        assertEquals(1, DemoPortfolio.funds.size, "示例资金流水 = 1 笔增资")
        assertEquals(4, DemoPortfolio.transactions.size, "示例交易 = 3 买 + 1 卖")
        assertEquals(1, DemoPortfolio.transactions.count { it.side == Side.SELL }, "仅 1 笔卖出")
        assertEquals(3, DemoPortfolio.transactions.count { it.side == Side.BUY }, "3 笔买入")
        assertEquals(
            setOf("BTC", "ETH", "SOL"),
            DemoPortfolio.transactions.map { it.baseSymbol }.toSet(),
            "覆盖 2–3 个主流币",
        )
        assertEquals(
            listOf("USDT", "BTC", "ETH", "SOL"),
            DemoPortfolio.coinSymbols,
            "涉及币种（增资币 + 交易两腿）",
        )
    }

    @Test
    fun `all amounts are positive big decimals and sell never exceeds bought quantity`() {
        val funds = DemoPortfolio.funds
        val txs = DemoPortfolio.transactions
        assertTrue(funds.all { it.quantity.signum() > 0 }, "增资数量必须为正")
        assertTrue(txs.all { it.price.signum() > 0 && it.quantity.signum() > 0 }, "价格与数量必须为正")
        assertTrue(txs.all { it.fee.signum() >= 0 }, "手续费必须非负")
        // 金额为 BigDecimal 十进制精确值（若走 Double，7.5 会变成 7.500000000000000444…）
        assertEquals("100000", funds.single().quantity.toPlainString())
        assertEquals("7.5", txs.first { it.baseSymbol == "ETH" }.fee.toPlainString())

        val boughtBtc = txs.filter { it.baseSymbol == "BTC" && it.side == Side.BUY }
            .fold(BigDecimal.ZERO) { sum, t -> sum + t.quantity }
        val soldBtc = txs.filter { it.baseSymbol == "BTC" && it.side == Side.SELL }
            .fold(BigDecimal.ZERO) { sum, t -> sum + t.quantity }
        assertTrue(soldBtc <= boughtBtc, "卖出量不得超过累计买入量（相对校验自洽前提）")
    }

    @Test
    fun `every note carries the demo marker and times are strictly ascending`() {
        val notes = DemoPortfolio.fundInputs(now).map { it.notes } +
            DemoPortfolio.transactionInputs(now).map { it.notes }
        assertTrue(notes.all { DemoData.isDemoNote(it) }, "示例数据的备注必须带统一标记")

        val times = DemoPortfolio.fundInputs(now).map { it.time } +
            DemoPortfolio.transactionInputs(now).map { it.time }
        assertEquals(times.sorted(), times, "资金先于全部交易，且交易按时间升序（落库顺序 = 重放顺序）")
    }

    @Test
    fun `inputs are deterministic for a given now and marker helpers classify notes`() {
        assertEquals(DemoPortfolio.fundInputs(now), DemoPortfolio.fundInputs(now))
        assertEquals(DemoPortfolio.transactionInputs(now), DemoPortfolio.transactionInputs(now))
        assertEquals(
            DemoPortfolio.at(now, 60),
            Instant.parse("2026-08-11T12:00:00Z"),
            "相对天数时间轴",
        )

        assertTrue(DemoData.note("x").startsWith(DemoData.MARKER))
        assertTrue(DemoData.isDemoNote(DemoData.note("建仓买入")))
        assertFalse(DemoData.isDemoNote(null), "无备注 = 真实数据")
        assertFalse(DemoData.isDemoNote("我的真实交易"), "无前缀 = 真实数据")
        assertFalse(DemoData.isDemoNote("备注里的 [示例] 不在行首"), "仅行首前缀算示例标记")
    }
}
