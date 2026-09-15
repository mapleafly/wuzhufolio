package com.wuzhufolio.ui.ledger

import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationResult
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.FiatValuePreview
import com.wuzhufolio.domain.ledger.FundDateRange
import com.wuzhufolio.domain.ledger.FundEntryRow
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.FundPage
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.domain.ledger.FundsOverview
import com.wuzhufolio.domain.ledger.ReconciliationRow
import java.math.BigDecimal
import java.time.Instant
import com.wuzhufolio.ui.i18n.ledgerStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 资金管理页 UI 走查（M8 · T8.3 · 共性约束 7.3）：空态/总览卡/列表（增资/撤资/校准行）/
 * 表单首输入框聚焦 + 键盘录入（performTextInput + assertIsFocused）/V6 校验不触达服务/
 * 保存成功/删除确认/校准向导（预览 -> 执行）。
 */
@OptIn(ExperimentalTestApi::class)
class FundsPageUiTest {

    @Suppress("LongParameterList") // 测试行构造器（默认值 + 命名参数覆盖，M7 同口径）
    private fun fundRow(
        id: Long,
        uuid: String,
        type: FundEntryType,
        coin: String = "USDT",
        qty: String = "100",
        coinId: Long = id + 100,
    ) = FundEntryRow(
        id = id,
        uuid = uuid,
        entryType = type,
        kind = if (type == FundEntryType.DEPOSIT) FlowKind.DEPOSIT else if (type == FundEntryType.WITHDRAWAL) {
            FlowKind.WITHDRAWAL
        } else {
            null
        },
        coinSymbol = coin,
        coinId = coinId,
        quantity = BigDecimal(qty),
        baseAmount = BigDecimal("1000"),
        time = Instant.parse("2026-09-01T08:00:00Z"),
        sourceDest = if (type == FundEntryType.RECONCILIATION) "BINANCE" else null,
        notes = null,
        estimated = false,
    )

    private inner class FakeFundService : FundService {
        val rows = mutableListOf<FundEntryRow>()
        var savedInput: FundInput? = null
        var updated: Pair<Long, FundInput>? = null
        var deletedUuids: List<String>? = null
        var searchResults: List<CatalogCoin> = emptyList()

        override suspend fun listFunds(filter: FundFilter): FundPage = FundPage(
            rows = rows.toList(),
            overview = FundsOverview(BigDecimal("900"), BigDecimal("900"), BigDecimal("1000"), BigDecimal("100")),
        )

        override suspend fun saveFund(input: FundInput): Long {
            savedInput = input
            rows.add(fundRow(rows.size + 1L, "uuid-save-" + rows.size, FundEntryType.DEPOSIT, input.coinSymbol))
            return rows.size.toLong()
        }

        override suspend fun updateFund(id: Long, input: FundInput) {
            updated = id to input
        }

        override suspend fun deleteFunds(uuids: List<String>) {
            deletedUuids = uuids
            rows.removeAll { it.uuid in uuids }
        }

        override suspend fun fundsOverview(): FundsOverview =
            FundsOverview(BigDecimal("900"), BigDecimal("900"), BigDecimal("1000"), BigDecimal("100"))

        override suspend fun fiatValuePreview(
            coinSymbol: String,
            quantity: BigDecimal,
            at: Instant,
            pickedCoinId: Long?,
        ): FiatValuePreview = FiatValuePreview(BigDecimal("100"), false)

        override suspend fun searchCoins(query: String, limit: Int): List<CatalogCoin> = searchResults

        override suspend fun defaultCoin(): CatalogCoin? = null
    }

    private inner class FakeCalibration(
        private val blockReason: CalibrationBlockedException.Reason? = null,
    ) : CalibrationUseCase {
        var preparedCoin: String? = null
        var executedCoin: String? = null

        private val prep = CalibrationPreparation(
            coinSymbol = "BTC",
            coinName = "Bitcoin",
            exchangeName = "BINANCE",
            apiKeyId = 1L,
            apiKeyName = "test-key",
            localQuantity = BigDecimal("0.4"),
            exchangeQuantity = BigDecimal("0.9"),
            delta = BigDecimal("0.5"),
            deltaFiat = BigDecimal("25000"),
            marketPrice = BigDecimal("50000"),
            direction = FlowKind.DEPOSIT,
        )

        override suspend fun prepare(coinSymbol: String, pickedCoinId: Long?): CalibrationPreparation {
            preparedCoin = coinSymbol
            blockReason?.let { throw CalibrationBlockedException(it, "提示：" + it.name) }
            return prep
        }

        override suspend fun execute(coinSymbol: String, pickedCoinId: Long?): CalibrationResult {
            executedCoin = coinSymbol
            return CalibrationResult(
                recorded = true,
                row = ReconciliationRow(
                    id = 1,
                    uuid = "uuid-recon",
                    symbol = "BTC",
                    exchange = "BINANCE",
                    localQuantity = BigDecimal("0.4"),
                    exchangeQuantity = BigDecimal("0.9"),
                    delta = BigDecimal("0.5"),
                    baseAmount = BigDecimal("25000"),
                    createdAt = Instant.parse("2026-09-08T08:00:00Z"),
                ),
            )
        }

        override suspend fun history(coinSymbol: String, pickedCoinId: Long?): List<ReconciliationRow> = emptyList()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String, substring: Boolean = true): Int =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    /**
     * P6 人工门 DEF-13 追加验证（真实页面版）：**资金页内的命令区按钮必须能纯键盘到达并触发**。
     * 与 `KeyboardA11yUiTest`（主壳 + 槽位）互补——本用例用**真实 FundsPage**，排除「页面内控件不可聚焦」。
     */
    @Test
    fun commandAreaIsReachableByKeyboardOnly() = runComposeUiTest {
        val svc = FakeFundService()
        setContent { FundsPage(svc, FakeCalibration()) }

        fun focusedTag(): String? = onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
            ?.let { runCatching { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }.getOrNull() }

        // Tab 直到「记录增资」获得焦点（上限 20 步，覆盖总览卡/命令区/列表等前置节点）
        var hops = 0
        while (hops < 20 && focusedTag() != "fund-add-deposit") {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            hops++
        }
        assertEquals("fund-add-deposit", focusedTag(), "「记录增资」应可由 Tab 到达（实际 $hops 步）")

        // 回车触发（键盘激活）→ 弹窗打开且首输入框自动聚焦
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("fund-modal", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        // 弹窗打开即聚焦首输入框（共性约束 7.3-②），键盘可直接继续录入
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("fund-coin-input").assertIsFocused() }.isSuccess
        }
    }

    @Test
    fun emptyStateShowsCommandAreaAndOverview() = runComposeUiTest {
        setContent { FundsPage(FakeFundService(), FakeCalibration()) }
        onNodeWithTag("funds-page").assertIsDisplayed()
        onNodeWithTag("fund-add-deposit").assertIsDisplayed()
        onNodeWithTag("fund-add-withdraw").assertIsDisplayed()
        onNodeWithTag("fund-calibrate").assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.EMPTY) >= 1 }
        // 总览卡
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.CARD_CASH) >= 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.CARD_PRINCIPAL) >= 1 }
    }

    @Test
    fun depositModalFocusesCoinInputAndSavesViaKeyboard() = runComposeUiTest {
        val svc = FakeFundService()
        setContent { FundsPage(svc, FakeCalibration()) }
        onNodeWithTag("fund-add-deposit").performClick()
        onNodeWithTag("fund-modal", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("fund-coin-input").assertIsFocused() }.isSuccess
        }
        // 键盘逐字录入（共性约束 7.3 验收强制项）
        onNodeWithTag("fund-coin-input").performTextInput("USDT")
        onNodeWithTag("fund-qty-input").performTextInput("1000")
        onNodeWithTag("fund-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals("USDT", svc.savedInput!!.coinSymbol)
        assertEquals(FlowKind.DEPOSIT, svc.savedInput!!.kind)
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.SAVE_SUCCESS) >= 1 }
    }

    @Test
    fun withdrawModalUsesWithdrawTitleAndKind() = runComposeUiTest {
        val svc = FakeFundService()
        setContent { FundsPage(svc, FakeCalibration()) }
        onNodeWithTag("fund-add-withdraw").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.FORM_TITLE_WITHDRAW) >= 1 }
        onNodeWithTag("fund-coin-input").performTextInput("USDT")
        onNodeWithTag("fund-qty-input").performTextInput("50")
        onNodeWithTag("fund-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals(FlowKind.WITHDRAWAL, svc.savedInput!!.kind)
    }

    /**
     * DEF-20（2026-09-15 Windows 人工走查）：币种候选行点选后该行消失，焦点此前掉回窗口根
     * （下一次 Tab 从侧边栏重来）。修复后焦点交到弹窗内的「数量」字段，键盘可继续录入。
     */
    @Test
    fun coinPickHandsFocusToQuantityFieldInsideModal() = runComposeUiTest {
        val picked = CatalogCoin(78L, "tether", null, "USDT", "Tether", CoinStatus.ACTIVE)
        val svc = FakeFundService().apply { searchResults = listOf(picked) }
        setContent { FundsPage(svc, FakeCalibration()) }
        onNodeWithTag("fund-add-deposit").performClick()
        onNodeWithTag("fund-coin-input").performTextInput("USDT")
        waitUntil(timeoutMillis = 2_000) { textCount("tether") >= 1 }
        onNodeWithTag("fund-suggestion-78").performClick()
        waitForIdle()
        onNodeWithTag("fund-qty-input").assertIsFocused()

        // 继续 Tab：仍在弹窗内（数量 → 日期时间），焦点没有回到窗口根的焦点环
        onNodeWithTag("fund-qty-input").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        onNodeWithTag("fund-time-input").assertIsFocused()
    }

    /** 修复轮 §8-1：候选点选必须直达保存（pickedCoinId 传入服务），且候选行展示 cg_id。 */
    @Test
    fun coinPickCarriesPickedCoinIdToService() = runComposeUiTest {
        val picked = CatalogCoin(77L, "tether", null, "USDT", "Tether", CoinStatus.ACTIVE)
        val svc = FakeFundService().apply { searchResults = listOf(picked) }
        setContent { FundsPage(svc, FakeCalibration()) }
        onNodeWithTag("fund-add-deposit").performClick()
        onNodeWithTag("fund-coin-input").performTextInput("USDT")
        waitUntil(timeoutMillis = 2_000) { textCount("tether") >= 1 }
        onNodeWithTag("fund-suggestion-77").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.PICKED_PREFIX) >= 1 }
        onNodeWithTag("fund-qty-input").performTextInput("100")
        onNodeWithTag("fund-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals(77L, svc.savedInput!!.pickedCoinId)
        assertEquals("USDT", svc.savedInput!!.coinSymbol)
    }

    @Test
    fun emptyFormShowsV6ErrorsAndDoesNotTouchService() = runComposeUiTest {
        val svc = FakeFundService()
        setContent { FundsPage(svc, FakeCalibration()) }
        onNodeWithTag("fund-add-deposit").performClick()
        onNodeWithTag("fund-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.V6_COIN_REQUIRED) >= 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.V6_QTY) >= 1 }
        assertTrue(svc.savedInput == null, "空表单不得触达服务")
    }

    @Test
    fun listRendersMixedRowsAndReconRowHasNoEdit() = runComposeUiTest {
        val svc = FakeFundService().apply {
            rows.add(fundRow(1L, "uuid-aaaaaa", FundEntryType.DEPOSIT))
            rows.add(fundRow(2L, "uuid-bbbbbb", FundEntryType.WITHDRAWAL, coin = "BTC", qty = "0.5"))
            rows.add(fundRow(3L, "uuid-cccccc", FundEntryType.RECONCILIATION, coin = "ETH", qty = "-2"))
        }
        setContent { FundsPage(svc, FakeCalibration()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 && textCount("校准") >= 1 }
        onNodeWithTag("fund-type-cccccc").assertIsDisplayed()
        // 校准行不可编辑（不渲染编辑入口），可删除（PRD §10-8 注）
        onNodeWithTag("fund-delete-cccccc").assertIsDisplayed()
        assertTrue(
            onAllNodesWithTag("fund-edit-cccccc", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "校准行不得渲染编辑按钮",
        )
    }

    @Test
    fun deleteConfirmFlowReachesService() = runComposeUiTest {
        val svc = FakeFundService().apply { rows.add(fundRow(1L, "uuid-dddddd", FundEntryType.DEPOSIT)) }
        setContent { FundsPage(svc, FakeCalibration()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("fund-check-dddddd").performClick()
        onNodeWithTag("fund-delete-batch").performClick()
        onNodeWithTag("fund-delete-confirm", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) {
            textCount(FundsCopy.DELETE_CONFIRM.replace("N", "1"), substring = true) >= 1
        }
        onNodeWithTag("fund-delete-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.deletedUuids == listOf("uuid-dddddd") }
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.DELETE_SUCCESS) >= 1 }
    }

    @Test
    fun calibrationWizardPreparesAndExecutes() = runComposeUiTest {
        val cal = FakeCalibration()
        val svc = FakeFundService()
        setContent { FundsPage(svc, cal) }
        onNodeWithTag("fund-calibrate").performClick()
        onNodeWithTag("calibration-modal", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("cal-coin-input").performTextInput("BTC")
        onNodeWithTag("cal-prepare").performClick()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("cal-preview", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        waitUntil(timeoutMillis = 2_000) { textCount("BINANCE") >= 1 }
        onNodeWithTag("cal-execute", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { cal.executedCoin == "BTC" }
        waitUntil(timeoutMillis = 2_000) { textCount(FundsCopy.CAL_SUCCESS) >= 1 }
    }

    @Test
    fun calibrationBlockedReasonSurfacesError() = runComposeUiTest {
        val cal = FakeCalibration(blockReason = CalibrationBlockedException.Reason.MULTI_SOURCE)
        setContent { FundsPage(FakeFundService(), cal) }
        onNodeWithTag("fund-calibrate").performClick()
        onNodeWithTag("cal-coin-input").performTextInput("BTC")
        onNodeWithTag("cal-prepare").performClick()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("cal-error", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        // M12 T12.4：阻断原因改由领域层类型化 Reason 映射为本地化文案（不再消费异常 message 正文）
        waitUntil(timeoutMillis = 2_000) {
            textCount(ledgerStrings.calibrationBlocked(CalibrationBlockedException.Reason.MULTI_SOURCE)) >= 1
        }
        assertTrue(cal.executedCoin == null, "被阻止时不得执行")
    }

    @Test
    fun typeAndDateFiltersToggleWithoutCrash() = runComposeUiTest {
        val svc = FakeFundService().apply {
            rows.add(fundRow(1L, "uuid-eeeeee", FundEntryType.DEPOSIT))
        }
        setContent { FundsPage(svc, FakeCalibration()) }
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("fund-type-recon").performClick()
        onNodeWithTag("fund-date-older").performClick()
        // 假服务不过滤（本地展示），断言按钮切换不崩溃且列表仍在
        waitUntil(timeoutMillis = 2_000) { textCount("USDT") >= 1 }
        onNodeWithTag("fund-search").performTextInput("BTC")
        onNodeWithTag("fund-search").assertIsDisplayed()
    }
}
