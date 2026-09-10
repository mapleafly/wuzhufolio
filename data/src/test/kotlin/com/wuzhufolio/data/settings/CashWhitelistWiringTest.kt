package com.wuzhufolio.data.settings

import com.wuzhufolio.data.ledger.LedgerTestEnv
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 稳定币白名单设置生效（M10 T10.1 验收「稳定币白名单 → 可用现金」）：
 * 扩展白名单后，扩展币种计入可用现金（DefaultFundService 总览）并进入事件构造 USD 锚定集合。
 */
class CashWhitelistWiringTest {

    private fun input(coin: String, quantity: String, at: Instant) = FundInput(
        kind = FlowKind.DEPOSIT,
        coinSymbol = coin,
        pickedCoinId = null,
        quantity = BigDecimal(quantity),
        time = at,
        sourceDest = null,
        notes = null,
    )

    @Test
    fun `extended whitelist coin counts into available cash`() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val at = Instant.parse("2026-09-10T08:00:00Z")
            // 增资 1000 USDT（现金）→ 买 1 BNB（扣 300 USDT）；BNB 行情价 400 USD
            env.fundService.saveFund(input("USDT", "1000", at))
            val bnb = env.coin("BNB") ?: throw AssertionError("BNB not seeded")
            env.seedExchangeTrade("BNB", com.wuzhufolio.domain.engine.Side.BUY, "300", "1", at)
            env.putSnapshot(bnb.id, "USD", "400", at)

            // 默认白名单：现金 = USDT 余额 700（BNB 不计现金）
            val defaultOverview = env.fundService.listFunds(FundFilter()).overview!!
            assertEquals(0, BigDecimal("700").compareTo(defaultOverview.availableCashFiat), "默认白名单不含 BNB")

            // 扩展白名单（含 binancecoin）：BNB 市值 400 计入可用现金 → 1100
            val extended = com.wuzhufolio.data.ledger.DefaultFundService(
                sessions = env.sessions,
                transactions = env.repository,
                funds = env.funds,
                recons = env.recons,
                catalog = env.catalog,
                settings = env.settings,
                eventBuilder = env.eventBuilder,
                assembler = env.assembler,
                cashCoinIds = { setOf("tether", "usd-coin", "dai", "true-usd", "binancecoin") },
            )
            val extendedOverview = extended.listFunds(FundFilter()).overview!!
            assertEquals(0, BigDecimal("1100").compareTo(extendedOverview.availableCashFiat), "扩展项市值计入可用现金")
        }
    }

    @Test
    fun `default wiring preserves usd anchored stablecoin flows`() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // 默认装配（provider 缺省 = 引擎常量）行为保持 M9 前口径：USDT 增资 1:1 非估算入账
            env.fundService.saveFund(input("USDT", "100", Instant.parse("2026-09-10T08:00:00Z")))
            val page = env.fundService.listFunds(FundFilter())
            assertEquals(FundEntryType.DEPOSIT, page.rows.single().entryType)
            assertEquals(0, BigDecimal("100").compareTo(page.rows.single().baseAmount))
        }
    }
}
