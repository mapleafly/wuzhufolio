package com.wuzhufolio.domain.demo

import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * 示例交易条目（符号口径——落库经既有 [com.wuzhufolio.domain.ledger.TransactionLedgerService]
 * 做目录解析与归一，故此处只写用户输入形态的符号）。
 */
data class DemoTransaction(
    val baseSymbol: String,
    val quoteSymbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val fee: BigDecimal,
    val feeCoinSymbol: String,
    /** 相对加载时刻的天数偏移（数据集内容确定，时间轴随加载时刻平移）。 */
    val daysAgo: Long,
    /** 带 [DemoData.MARKER] 前缀的备注。 */
    val note: String,
)

/** 示例资金流水条目（同上；符号口径 + 相对天数）。 */
data class DemoFund(
    val kind: FlowKind,
    val coinSymbol: String,
    val quantity: BigDecimal,
    val daysAgo: Long,
    val sourceDest: String,
    /** 带 [DemoData.MARKER] 前缀的备注。 */
    val note: String,
)

/**
 * 示例数据集（B1 · `D46`）——**确定性、自洽**，用于首次启动的「加载示例组合」引导。
 *
 * 设计约束（任何改动都必须保持）：
 * 1. **自洽**：手动保存路径走 `ReplayEngine.validateMutation` **STRICT 相对校验**，拦截「本笔新制造」的
 *    负边界 ⇒ 序列必须满足「先增资、再买入、卖出量不得超过当时持仓」。本数据集为
 *    1 笔增资（USDT 100,000）→ 3 笔买入（BTC / ETH / SOL）→ 1 笔卖出（BTC 0.2 ≤ 1），
 *    全程无负边界（`DemoPortfolioTest` 以 STRICT 重放守护）；
 * 2. **覆盖 2–3 个主流币**：BTC / ETH / SOL（计价腿统一 USDT——USD 锚定 1:1，折算链在无行情时也可解析）；
 * 3. **金额一律 `BigDecimal`**（不用 Double，与账本口径一致）；
 * 4. **确定性**：条目内容与相对天数固定；[fundInputs]/[transactionInputs] 传入 `now` 即完全确定
 *    （测试传固定时刻，运行期传 `Instant.now()`），且列表序 = 时间升序（落库顺序即重放顺序）。
 *
 * 加载后账本（USDT 按 1:1 锚定折算，与运行期一致）：USDT 40,952.5 · BTC 0.8 · ETH 5 · SOL 40；
 * 累计增资 100,000；该笔卖出的已实现盈亏 +1,983。
 */
object DemoPortfolio {

    /** 示例交易所（与既有同步/费率匹配键同口径的大写形态）。 */
    const val EXCHANGE: String = "BINANCE"

    /** 示例资金流水（**先于全部交易**：增资是买入的余额来源）。 */
    val funds: List<DemoFund> = listOf(
        DemoFund(
            kind = FlowKind.DEPOSIT,
            coinSymbol = "USDT",
            quantity = BigDecimal("100000"),
            daysAgo = 60,
            sourceDest = "示例：法币兑换后到账",
            note = DemoData.note("初始增资"),
        ),
    )

    /** 示例交易（3 笔买入 + 1 笔卖出；时间升序）。 */
    val transactions: List<DemoTransaction> = listOf(
        DemoTransaction(
            baseSymbol = "BTC",
            quoteSymbol = "USDT",
            side = Side.BUY,
            price = BigDecimal("50000"),
            quantity = BigDecimal("1"),
            fee = BigDecimal("25"),
            feeCoinSymbol = "USDT",
            daysAgo = 55,
            note = DemoData.note("建仓买入 BTC"),
        ),
        DemoTransaction(
            baseSymbol = "ETH",
            quoteSymbol = "USDT",
            side = Side.BUY,
            price = BigDecimal("3000"),
            quantity = BigDecimal("5"),
            fee = BigDecimal("7.5"),
            feeCoinSymbol = "USDT",
            daysAgo = 50,
            note = DemoData.note("建仓买入 ETH"),
        ),
        DemoTransaction(
            baseSymbol = "SOL",
            quoteSymbol = "USDT",
            side = Side.BUY,
            price = BigDecimal("150"),
            quantity = BigDecimal("40"),
            fee = BigDecimal("3"),
            feeCoinSymbol = "USDT",
            daysAgo = 45,
            note = DemoData.note("建仓买入 SOL"),
        ),
        DemoTransaction(
            baseSymbol = "BTC",
            quoteSymbol = "USDT",
            side = Side.SELL,
            price = BigDecimal("60000"),
            quantity = BigDecimal("0.2"),
            fee = BigDecimal("12"),
            feeCoinSymbol = "USDT",
            daysAgo = 20,
            note = DemoData.note("部分止盈 BTC"),
        ),
    )

    /** 数据集涉及的币种符号（计价腿 ∪ 基础腿，去重保序；供加载前置校验与文档引用）。 */
    val coinSymbols: List<String> =
        (funds.map { it.coinSymbol } + transactions.flatMap { listOf(it.baseSymbol, it.quoteSymbol) })
            .distinct()

    /** 加载时刻起算的时间轴（`now − daysAgo`）。 */
    fun at(now: Instant, daysAgo: Long): Instant = now.minus(Duration.ofDays(daysAgo))

    /** 资金输入（落库经 [com.wuzhufolio.domain.ledger.FundService.saveFund]）。 */
    fun fundInputs(now: Instant): List<FundInput> = funds.map { f ->
        FundInput(
            kind = f.kind,
            coinSymbol = f.coinSymbol,
            quantity = f.quantity,
            time = at(now, f.daysAgo),
            sourceDest = f.sourceDest,
            notes = f.note,
        )
    }

    /** 交易输入（落库经 [com.wuzhufolio.domain.ledger.TransactionLedgerService.saveTransaction]）。 */
    fun transactionInputs(now: Instant): List<TransactionInput> = transactions.map { t ->
        TransactionInput(
            exchange = EXCHANGE,
            baseSymbol = t.baseSymbol,
            quoteSymbol = t.quoteSymbol,
            side = t.side,
            price = t.price,
            quantity = t.quantity,
            fee = t.fee,
            feeCoinSymbol = t.feeCoinSymbol,
            time = at(now, t.daysAgo),
            notes = t.note,
        )
    }
}
