package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * M4 引擎测试夹具：黄金用例（PRD 附录 A）/语义测试的事件构造与数值断言工具。
 * 事件币键按 CoinGecko id（引擎键 = cg_id，模块记录 M4.md §5-3）；fixture 默认法币折算 px = 1
 * （USD 计价基准，与黄金用例口径一致：USDT≈1:1）。
 */
internal object Ev {

    /** 常用币键（cg id）。 */
    const val USDT: String = "tether"
    const val USDC: String = "usd-coin"
    const val BTC: String = "bitcoin"
    const val BNB: String = "binancecoin"

    private val EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val idSeq = AtomicLong(0)

    fun at(second: Long): Instant = EPOCH.plusSeconds(second)

    fun freshId(): String = "e" + idSeq.incrementAndGet()

    @Suppress("LongParameterList")
    fun deposit(
        at: Long,
        coin: String,
        qty: String,
        fiat: String,
        id: String = freshId(),
        seq: Long = 0,
        estimated: Boolean = false,
    ): FundEvent = FundEvent(
        id = id,
        at = at(at),
        coin = coin,
        kind = FlowKind.DEPOSIT,
        quantity = qty.bd(),
        fiatValue = fiat.bd(),
        seq = seq,
        estimated = estimated,
    )

    @Suppress("LongParameterList")
    fun withdraw(
        at: Long,
        coin: String,
        qty: String,
        fiat: String,
        id: String = freshId(),
        seq: Long = 0,
        estimated: Boolean = false,
    ): FundEvent = FundEvent(
        id = id,
        at = at(at),
        coin = coin,
        kind = FlowKind.WITHDRAWAL,
        quantity = qty.bd(),
        fiatValue = fiat.bd(),
        seq = seq,
        estimated = estimated,
    )

    /**
     * 交易事件构造：legFiat = 数量×价格×quotePx；feeFiat = fee×feePx
     * （feePx 缺省 = quotePx；手续费币种 ≠ quote 时调用方需显式传入其法币价——base 侧 = 价格、第三币种 = 其市价）。
     */
    @Suppress("LongParameterList") // 测试夹具：可选参数语义 > 拆参数对象；默认值保证常规调用不受影响
    fun trade(
        at: Long,
        side: Side,
        base: String,
        quote: String,
        price: String,
        qty: String,
        fee: String = "0",
        feeCoin: String = quote,
        quotePx: String = "1",
        feePx: String = quotePx,
        source: RecordSource = RecordSource.Manual,
        id: String = freshId(),
        seq: Long = 0,
        estimated: Boolean = false,
    ): TradeEvent {
        val priceBd = price.bd()
        val qtyBd = qty.bd()
        return TradeEvent(
            id = id,
            at = at(at),
            baseCoin = base,
            quoteCoin = quote,
            side = side,
            price = priceBd,
            quantity = qtyBd,
            fee = fee.bd(),
            feeCoin = feeCoin,
            legFiat = qtyBd.multiply(priceBd).multiply(quotePx.bd()),
            feeFiat = fee.bd().multiply(feePx.bd()),
            source = source,
            seq = seq,
            estimated = estimated,
        )
    }

    @Suppress("LongParameterList")
    fun buy(
        at: Long,
        base: String,
        quote: String,
        price: String,
        qty: String,
        fee: String = "0",
        feeCoin: String = quote,
        quotePx: String = "1",
        feePx: String = quotePx,
        source: RecordSource = RecordSource.Manual,
        id: String = freshId(),
        seq: Long = 0,
        estimated: Boolean = false,
    ): TradeEvent = trade(
        at = at, side = Side.BUY, base = base, quote = quote, price = price, qty = qty,
        fee = fee, feeCoin = feeCoin, quotePx = quotePx, feePx = feePx,
        source = source, id = id, seq = seq, estimated = estimated,
    )

    @Suppress("LongParameterList")
    fun sell(
        at: Long,
        base: String,
        quote: String,
        price: String,
        qty: String,
        fee: String = "0",
        feeCoin: String = quote,
        quotePx: String = "1",
        feePx: String = quotePx,
        source: RecordSource = RecordSource.Manual,
        id: String = freshId(),
        seq: Long = 0,
        estimated: Boolean = false,
    ): TradeEvent = trade(
        at = at, side = Side.SELL, base = base, quote = quote, price = price, qty = qty,
        fee = fee, feeCoin = feeCoin, quotePx = quotePx, feePx = feePx,
        source = source, id = id, seq = seq, estimated = estimated,
    )

    @Suppress("LongParameterList")
    fun anchor(
        at: Long,
        coin: String,
        exchangeQty: String,
        delta: String,
        deltaFiat: String,
        id: String = freshId(),
        seq: Long = 0,
    ): AnchorEvent = AnchorEvent(
        id = id,
        at = at(at),
        coin = coin,
        exchangeQuantity = exchangeQty.bd(),
        delta = delta.bd(),
        deltaFiat = deltaFiat.bd(),
        seq = seq,
    )
}

internal fun String.bd(): BigDecimal = BigDecimal(this)

/** 数值断言：忽略 scale 的等值比较（compareTo == 0）。 */
internal fun assertMoney(expected: String, actual: BigDecimal, label: String = "money") {
    kotlin.test.assertEquals(0, actual.compareTo(expected.bd()), "$label: expected $expected but got $actual")
}
