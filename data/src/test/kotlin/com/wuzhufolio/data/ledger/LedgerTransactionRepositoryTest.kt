package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 账本仓库（T7.1/T7.3）：全量/筛选读、插入、编辑、批量删除、精确/模糊去重。
 */
class LedgerTransactionRepositoryTest {

    @Suppress("LongParameterList") // 测试行构造器（全列默认值 + 命名参数覆盖），语义内聚
    private fun row(
        accountId: Int,
        pair: String = "BTC/USDT",
        side: Side = Side.BUY,
        price: String = "50000",
        quantity: String = "1",
        time: Instant = Instant.parse("2026-08-30T08:00:00Z"),
        source: String = "CSV",
        orderId: String? = null,
        base: Long = 1L,
        quote: Long = 3L,
    ) = NewLedgerTxRow(
        accountId = accountId,
        exchange = "BINANCE",
        exchangeOrderId = orderId,
        pair = pair,
        baseCoinId = base.toInt(),
        quoteCoinId = quote.toInt(),
        side = side,
        price = BigDecimal(price),
        quantity = BigDecimal(quantity),
        fee = BigDecimal("0.1"),
        feeCurrency = "USDT",
        time = time,
        notes = null,
        source = source,
        priceStatus = "OK",
    )

    @Test
    fun insertReadUpdateDeleteAndFilter() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            val btc = env.coin("BTC")!!
            val eth = env.coin("ETH")!!
            val usdt = env.coin("USDT")!!

            val id1 = env.repository.insert(row(accountId, base = btc.id, quote = usdt.id))
            val id2 = env.repository.insert(
                row(
                    accountId,
                    pair = "ETH/USDT",
                    side = Side.SELL,
                    price = "3400",
                    quantity = "1.5",
                    time = Instant.parse("2026-08-29T10:00:00Z"),
                    base = eth.id,
                    quote = usdt.id,
                ),
            )
            val id3 = env.repository.insert(
                row(accountId, pair = "BTC/USDT", price = "51000", quantity = "0.5"),
            )

            // 全量（时间升序——引擎重放输入序；最早在前）
            val all = env.repository.listAll(accountId)
            assertEquals(3, all.size)
            assertEquals(id2, all.first().id)
            assertTrue(all.map { it.id }.containsAll(listOf(id1, id2, id3)))

            // 筛选：币种 / 类型 / 文本
            assertEquals(2, env.repository.listFiltered(accountId, TxFilter(coinSymbol = "btc")).size)
            assertEquals(1, env.repository.listFiltered(accountId, TxFilter(side = Side.SELL)).size)
            assertEquals(1, env.repository.listFiltered(accountId, TxFilter(query = "ETH")).size)

            // 编辑
            env.repository.update(
                UpdatedLedgerTxRow(
                    accountId = accountId,
                    id = id2,
                    exchange = "BINANCE",
                    exchangeOrderId = null,
                    pair = "ETH/USDT",
                    baseCoinId = eth.id.toInt(),
                    quoteCoinId = usdt.id.toInt(),
                    side = Side.BUY,
                    price = BigDecimal("3300"),
                    quantity = BigDecimal("2"),
                    fee = BigDecimal.ZERO,
                    feeCurrency = "USDT",
                    time = Instant.parse("2026-08-29T10:00:00Z"),
                    notes = "改",
                ),
            )
            val edited = env.repository.findById(accountId, id2)!!
            assertEquals(Side.BUY, edited.side)
            assertEquals(BigDecimal("3300"), edited.price)

            // 删除
            env.repository.deleteByIds(accountId, listOf(id1, id3))
            assertEquals(1, env.repository.listAll(accountId).size)
        }
    }

    @Test
    fun exactAndFuzzyDedup() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val accountId = env.sessions.get()!!.account.id
            val btc = env.coin("BTC")!!
            val usdt = env.coin("USDT")!!

            val t = Instant.parse("2026-08-30T08:00:00Z")
            env.repository.insert(row(accountId, orderId = "ord-1", base = btc.id, quote = usdt.id, time = t))

            // 精确（交易所 + 订单号）
            assertTrue(env.repository.existsExact(accountId, "BINANCE", "ord-1"))
            // 模糊（时间 + 交易对 + 类型 + 数量 + 价格）
            assertNotNull(
                env.repository.findFuzzy(
                    accountId, "BINANCE", t, "BTC/USDT", "BUY", BigDecimal("1"), BigDecimal("50000"),
                ),
            )
            assertNull(
                env.repository.findFuzzy(
                    accountId, "BINANCE", t, "BTC/USDT", "BUY", BigDecimal("1"), BigDecimal("51000"),
                ),
            )
        }
    }
}
