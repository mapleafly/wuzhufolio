package com.wuzhufolio.app.integration

import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.LoginReq
import com.wuzhufolio.domain.backup.CproDecodeException
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.accounts.InvalidCredentialsException
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * **P5 集成联调 · 跨模块异常态**（interaction.md V 系 / A 系 / B 系 / N 系的**服务层**核验）。
 *
 * 与 P6 的分工：本类只验证「异常从产生到类型化上浮」的**跨模块链路**（domain 错误类型 → data 编排
 * → UI 文案映射的输入面）在**真实组合根 + 真实库**上成立；UI 文案逐字、异常态视觉与人工场景（断网/
 * 限流/真实 Key 失效）归 P6 系统测试。
 */
internal class ErrorPathIntegrationTest {

    @Test
    fun `错误口令 登录失败不暴露账户存在性（A1）`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
            runBlocking { auth.logout() }

            assertFailsWith<InvalidCredentialsException> {
                runBlocking { auth.login(LoginReq("alpha", wrongPassword(), rememberMe = false)) }
            }
            // 不存在的用户名 → 同一异常类型（A1：不暴露账户是否存在）
            assertFailsWith<InvalidCredentialsException> {
                runBlocking { auth.login(LoginReq("no-such-user", wrongPassword(), rememberMe = false)) }
            }
        }
    }

    @Test
    fun `买入余额不足 撤资超额 删除增资 三类重放校验在真实库上类型化上浮`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            val ledger = h.runtime.transactionLedgerService
            runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
            val usdt = h.coin("USDT")

            // V5：无本金即买入 → 计价腿余额不足
            val v5 = assertFailsWith<LedgerValidationException> {
                runBlocking {
                    ledger.saveTransaction(
                        TransactionInput(
                            "BINANCE", "BTC", "USDT", Side.BUY,
                            BigDecimal("50000"), BigDecimal("1"), BigDecimal.ZERO, "USDT", at(1),
                        ),
                    )
                }
            }
            assertEquals(LedgerErrorCode.INSUFFICIENT_BALANCE, v5.code, "V5 文案输入面 = 余额不足")
            assertEquals("USDT", v5.coinSymbol)

            // 增资后正常买入；再撤资超额 → V7 持仓不足
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("10000"), at(2)))
                ledger.saveTransaction(
                    TransactionInput(
                        "BINANCE", "BTC", "USDT", Side.BUY,
                        BigDecimal("5000"), BigDecimal("1"), BigDecimal.ZERO, "USDT", at(3),
                    ),
                )
            }
            val v7 = assertFailsWith<LedgerValidationException> {
                runBlocking {
                    funds.saveFund(FundInput(FlowKind.WITHDRAWAL, "USDT", usdt.id, BigDecimal("99999"), at(4)))
                }
            }
            assertEquals(LedgerErrorCode.INSUFFICIENT_POSITION, v7.code, "V7 文案输入面 = 撤资持仓不足")

            // V9：编辑/删除增资使其后交易转负 → 重放冲突（含冲突定位字段）
            val deposit = runBlocking { funds.listFunds(FundFilter()) }.rows
                .first { it.entryType == com.wuzhufolio.domain.ledger.FundEntryType.DEPOSIT }
            val v9 = assertFailsWith<LedgerValidationException> {
                runBlocking { funds.deleteFunds(listOf(deposit.uuid)) }
            }
            assertEquals(LedgerErrorCode.REPLAY_CONFLICT, v9.code, "V9 文案输入面 = 重放冲突")

            // 数据未被破坏（失败操作不产生半成品写入）
            assertEquals(1, runBlocking { ledger.listTransactions(TxFilter()) }.size)
            assertEquals(1, runBlocking { funds.listFunds(FundFilter()) }.rows.size)
        }
    }

    @Test
    fun `备份密码错误 与 非cpro文件 的类型化上浮（BACKUP_INVALID）`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val backup = h.runtime.backupService
            runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
            val file = h.dataDir.resolve("p5-err.cpro")
            runBlocking { backup.exportBackup(file, password()) }

            val wrong = assertFailsWith<CproDecodeException> {
                runBlocking { backup.prepareRestore(file, wrongPassword()) }
            }
            assertEquals(CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED, wrong.reason)

            val garbage = h.dataDir.resolve("p5-garbage.cpro")
            java.nio.file.Files.writeString(garbage, "not-a-cpro-file")
            val malformed = assertFailsWith<CproDecodeException> {
                runBlocking { backup.prepareRestore(garbage, password()) }
            }
            assertEquals(CproDecodeException.Reason.MALFORMED_FILE, malformed.reason)

            // 恢复失败不得改动本地账本
            assertTrue(runBlocking { backup.backupMetadata() }.lastRestoreAt == null, "失败恢复不得写入元数据")
        }
    }

    @Test
    fun `校准入口三类阻止原因在真实库上类型化上浮（PRD 4_1-5 按钮隐藏并提示）`() {
        IntegrationHarness.boot().use { h ->
            val auth = h.runtime.session.authService
            val funds = h.runtime.fundService
            runBlocking { auth.createAccount(CreateAccountReq("alpha", password(), false)) }
            val usdt = h.coin("USDT")
            runBlocking {
                funds.saveFund(FundInput(FlowKind.DEPOSIT, "USDT", usdt.id, BigDecimal("10000"), at(1)))
            }

            // ① 只有手动记录 → 来源 = Manual（非单一交易所 API）→ MULTI_SOURCE（PRD 故事 4.1-5）
            runBlocking {
                h.runtime.transactionLedgerService.saveTransaction(
                    TransactionInput(
                        "BINANCE", "ETH", "USDT", Side.BUY,
                        BigDecimal("3000"), BigDecimal("1"), BigDecimal.ZERO, "USDT", at(2),
                    ),
                )
            }
            val manualOnly = assertFailsWith<CalibrationBlockedException> {
                runBlocking { h.runtime.calibrationService.prepare("ETH") }
            }
            assertEquals(CalibrationBlockedException.Reason.MULTI_SOURCE, manualOnly.reason)

            // ② 无任何记录 → NO_RECORDS
            val noRecords = assertFailsWith<CalibrationBlockedException> {
                runBlocking { h.runtime.calibrationService.prepare("DAI") }
            }
            assertEquals(CalibrationBlockedException.Reason.NO_RECORDS, noRecords.reason)

            // ③ 单一交易所 API 来源但本账户无只读密钥 → NO_EXCHANGE_KEY
            //    （直插交易所来源行：绕过同步编排，模拟「已同步但密钥被移除」的既存数据面）
            val btc = h.coin("BTC")
            val stable = h.coin("USDT")
            runBlocking {
                com.wuzhufolio.data.ledger.LedgerTransactionRepository(h.runtime.gate).insert(
                    com.wuzhufolio.data.ledger.NewLedgerTxRow(
                        accountId = h.runtime.session.sessions.get()!!.account.id,
                        exchange = "BINANCE",
                        exchangeOrderId = null,
                        pair = "BTC/USDT",
                        baseCoinId = btc.id.toInt(),
                        quoteCoinId = stable.id.toInt(),
                        side = Side.BUY,
                        price = BigDecimal("5000"),
                        quantity = BigDecimal("1"),
                        fee = BigDecimal.ZERO,
                        feeCurrency = "USDT",
                        time = at(3),
                        notes = null,
                        source = com.wuzhufolio.data.ledger.TransactionEventBuilder.SOURCE_BINANCE,
                        priceStatus = "OK",
                    ),
                )
            }
            val noKey = assertFailsWith<CalibrationBlockedException> {
                runBlocking { h.runtime.calibrationService.prepare("BTC") }
            }
            assertEquals(CalibrationBlockedException.Reason.NO_EXCHANGE_KEY, noKey.reason)
        }
    }

    private fun at(hour: Int): Instant = Instant.parse("2026-09-13T%02d:00:00Z".format(hour))

    private fun password(): CharArray = "Passw0rd!".toCharArray()

    private fun wrongPassword(): CharArray = "Wrong123!".toCharArray()
}
