package com.wuzhufolio.app.integration

import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * **P5 人工验收回归 · 首次运行链路（真实网络，env 门控）**。
 *
 * 复现并验证人工验收报告的问题 2/3：
 * ① 全新安装（coins 目录为空）时录入增资 → 报「币种无法唯一确定」（用户实测「未收录」）；
 * ② 一次行情刷新后目录就绪 → 录入成功（含用户实测「过一会儿就能添加」）；
 * ③ 买入后的**持仓币**必须被后续刷新定价——此前定时刷新只刷 4 个现金白名单币，持仓币永远「无行情」。
 *
 * 运行（联网；匿名公共档，不使用任何 Key）：
 * ```
 * WZF_LIVE_SMOKE=1 ./gradlew :app:test --tests "com.wuzhufolio.app.integration.LiveMarketRefreshWiringTest" --rerun-tasks -i
 * ```
 * 默认跳过：外部 API 不得进入默认回归（M5 先例，见 `docs/test/integration-report.md` §5.2）。
 */
internal class LiveMarketRefreshWiringTest {

    @Test
    fun `全新安装 空目录不可录入 刷新后目录就绪且持仓币被定价`() {
        assumeTrue(System.getenv("WZF_LIVE_SMOKE") == "1", "live smoke 默认跳过（设 WZF_LIVE_SMOKE=1 开启）")

        // 全新安装口径：目录与快照皆空
        IntegrationHarness.boot(seedDirectory = false, seedPrices = false).use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            runBlocking { auth.createAccount(CreateAccountReq("fresh", "Passw0rd!".toCharArray(), false)) }

            // ① 空目录 → 增资不可录入（人工验收实测现象；类型化上浮为 CoinResolutionException）
            assertFailsWith<CoinResolutionException> {
                runBlocking {
                    funds.saveFund(
                        FundInput(FlowKind.DEPOSIT, "USDT", null, BigDecimal("100000"), Instant.now()),
                    )
                }
            }

            // ② 一次行情刷新（真实 CoinGecko）→ 目录维护 + 当前价快照
            val first = runBlocking { h.runtime.marketRefreshService.refresh(manual = true) }
            assertEquals(null, first.error, "真实网络刷新应成功（失败时检查网络/限流）：${first.error}")
            assertNotNull(runBlocking { h.catalog.getByCgId("tether") }, "刷新后 USDT 应进入本地目录")

            // ③ 目录就绪后录入成功，并可下单买入 BTC
            // 真实目录里 "USDT" 符号可能有多个同名资产（人工验收实测的同名歧义），显式用 cg_id 锚定 tether
            val usdt = assertNotNull(runBlocking { h.catalog.getByCgId("tether") }, "目录应含 tether")
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("100000"), Instant.now()))
                h.runtime.transactionLedgerService.saveTransaction(
                    TransactionInput(
                        "BINANCE", "BTC", "USDT", Side.BUY,
                        BigDecimal("50000"), BigDecimal("0.01"), BigDecimal.ZERO, "USDT", Instant.now(),
                    ),
                )
            }

            // ④ 关键回归：**不显式传币集**的刷新（= 定时刷新/设置页刷新口径）必须覆盖持仓币 BTC
            val second = runBlocking { h.runtime.marketRefreshService.refresh(manual = false) }
            assertEquals(null, second.error, "持仓币刷新应成功：${second.error}")
            val btc = assertNotNull(runBlocking { h.catalog.getByCgId("bitcoin") }, "目录应含 bitcoin")
            val btcPrice = runBlocking { h.snapshots.latest(btc.id.toInt(), "USD") }
            assertNotNull(btcPrice, "持仓币 BTC 必须被默认刷新定价（此前恒刷 4 个现金币 → 看板长期「无行情」）")

            // ⑤ 看板：BTC 有价、计入净值（不再是「只有现金」）
            val snapshot = runBlocking { h.runtime.portfolioService.snapshot() }
            println("[live] rows=" + snapshot.rows.map { it.cgId + ":" + it.quantity + "@" + it.priceFiat })
            val btcRow = snapshot.rows.first { it.cgId == "bitcoin" }
            assertTrue(btcRow.priced, "BTC 行应有现价")
            assertNotNull(btcRow.marketValueFiat, "BTC 市值应可算")
            assertTrue(
                snapshot.metrics.netValueFiat > BigDecimal.ZERO,
                "净值应包含持仓市值：${snapshot.metrics.netValueFiat}",
            )
        }
    }
}
