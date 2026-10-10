package com.wuzhufolio.ui.ledger

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.demo.DemoDataService
import com.wuzhufolio.domain.demo.DemoLoadResult
import com.wuzhufolio.domain.demo.DemoRejectReason
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CsvImportSummary
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.FeeQuoteResult
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 交易页「示例数据 / 首次引导」UI 走查（B1 · `D46` · T15.3）——**注入 fake 服务**（不接真库）：
 * 空态引导与按钮存在 / 点击走注入服务并刷新列表 / 非空账户拒绝文案 /
 * 示例数据显式标识 + 清除二次确认（含部分清除分支）/ 未注入服务时页面与改造前一致。
 */
@OptIn(ExperimentalTestApi::class)
class DemoDataUiTest {

    private fun row(id: Long): TransactionRow = TransactionRow(
        id = id,
        exchange = "BINANCE",
        exchangeOrderId = null,
        pair = "BTC/USDT",
        baseSymbol = "BTC",
        quoteSymbol = "USDT",
        side = Side.BUY,
        price = BigDecimal("50000"),
        quantity = BigDecimal("0.1"),
        fee = BigDecimal("0.1"),
        feeCurrency = "USDT",
        total = BigDecimal("5000"),
        time = Instant.parse("2026-08-30T08:00:00Z"),
        notes = "[示例] 建仓买入 BTC",
        source = "Manual",
        priceStatus = "OK",
        estimated = false,
        realizedPnlFiat = null,
    )

    /** 最小交易账本 fake（只用到列表/删除；其余动作在用例中不会被触达）。 */
    private class FakeLedger(val rows: MutableList<TransactionRow> = mutableListOf()) :
        TransactionLedgerService {

        override suspend fun listTransactions(filter: TxFilter): List<TransactionRow> = rows.toList()

        override suspend fun saveTransaction(input: TransactionInput): Long = 0L

        override suspend fun updateTransaction(id: Long, input: TransactionInput) = Unit

        override suspend fun deleteTransactions(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun feeQuote(request: FeeQuoteRequest): FeeQuoteResult? = null

        override suspend fun searchCoins(query: String, limit: Int): List<CatalogCoin> = emptyList()

        override suspend fun parseCsv(bytes: ByteArray): CsvPreview = error("用例不应触达 CSV 解析")

        override suspend fun confirmCsvImport(
            sessionId: String,
            ambiguityChoices: Map<String, String>,
            includeRowKeys: Set<String>,
        ): CsvImportSummary = error("用例不应触达 CSV 导入")

        override fun csvTemplateCsv(): String = ""
    }

    /** 示例数据 fake：记录调用次数，按脚本返回结果。 */
    private class FakeDemo(
        private val loadResult: DemoLoadResult = DemoLoadResult.Loaded(transactions = 4, funds = 1),
        private var present: Boolean = false,
        private val clearCount: Int = 0,
        private val remainingAfterClear: Boolean = false,
        private val onLoad: () -> Unit = {},
    ) : DemoDataService {

        var loadCalls: Int = 0
        var clearCalls: Int = 0

        override suspend fun load(): DemoLoadResult {
            loadCalls += 1
            if (loadResult is DemoLoadResult.Loaded) {
                onLoad()
                present = true
            }
            return loadResult
        }

        override suspend fun clear(): Int {
            clearCalls += 1
            present = remainingAfterClear
            return clearCount
        }

        override suspend fun hasDemoData(): Boolean = present
    }

    private fun ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun emptyStateOffersDemoGuidanceAndLoadButton() = runComposeUiTest {
        setContent { TransactionsPage(FakeLedger(), { null }, { null }, demoService = FakeDemo()) }
        onNodeWithTag("transactions-page").assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.EMPTY) >= 1 }
        onNodeWithTag("tx-empty").assertIsDisplayed()
        onNodeWithTag("tx-demo-guide").assertIsDisplayed()
        onNodeWithTag("tx-demo-load").assertIsDisplayed()
    }

    @Test
    fun withoutDemoServiceEmptyStateIsUnchanged() = runComposeUiTest {
        setContent { TransactionsPage(FakeLedger(), { null }, { null }) }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.EMPTY) >= 1 }
        onNodeWithTag("tx-demo-guide").assertDoesNotExist()
        onNodeWithTag("tx-demo-load").assertDoesNotExist()
        onNodeWithTag("tx-demo-banner").assertDoesNotExist()
    }

    @Test
    fun clickingLoadRunsInjectedServiceAndShowsLoadedRows() = runComposeUiTest {
        val ledger = FakeLedger()
        val demo = FakeDemo(onLoad = { ledger.rows.add(row(1L)) })
        setContent { TransactionsPage(ledger, { null }, { null }, demoService = demo) }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.EMPTY) >= 1 }

        onNodeWithTag("tx-demo-load").performClick()
        waitUntil(timeoutMillis = 2_000) { demo.loadCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.demoLoadSuccess(5)) >= 1 }
        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-row-1").assertIsDisplayed()
        onNodeWithTag("tx-demo-banner").assertIsDisplayed()
    }

    @Test
    fun rejectedLoadShowsEmptyAccountOnlyCopy() = runComposeUiTest {
        val demo = FakeDemo(loadResult = DemoLoadResult.Rejected(DemoRejectReason.ACCOUNT_NOT_EMPTY))
        setContent { TransactionsPage(FakeLedger(), { null }, { null }, demoService = demo) }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.EMPTY) >= 1 }

        onNodeWithTag("tx-demo-load").performClick()
        waitUntil(timeoutMillis = 2_000) { demo.loadCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.DEMO_LOAD_REJECTED) >= 1 }
        onNodeWithTag("tx-demo-banner").assertDoesNotExist()
    }

    @Test
    fun clearRequiresConfirmationAndCallsInjectedService() = runComposeUiTest {
        val ledger = FakeLedger(mutableListOf(row(1L)))
        val demo = FakeDemo(present = true, clearCount = 5)
        setContent { TransactionsPage(ledger, { null }, { null }, demoService = demo) }

        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-demo-banner").assertIsDisplayed()
        onNodeWithTag("tx-demo-badge").assertIsDisplayed()

        onNodeWithTag("tx-demo-clear").performClick()
        onNodeWithTag("tx-demo-clear-confirm", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, demo.clearCalls, "确认前不得调用清除")

        onNodeWithTag("tx-demo-clear-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { demo.clearCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.demoCleared(5)) >= 1 }
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("tx-demo-banner").assertDoesNotExist() }.isSuccess
        }
    }

    @Test
    fun partialClearKeepsBannerAndWarnsAboutDependency() = runComposeUiTest {
        val ledger = FakeLedger(mutableListOf(row(1L)))
        val demo = FakeDemo(present = true, clearCount = 4, remainingAfterClear = true)
        setContent { TransactionsPage(ledger, { null }, { null }, demoService = demo) }

        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-demo-clear").performClick()
        onNodeWithTag("tx-demo-clear-confirm-btn", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 2_000) { demo.clearCalls == 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.demoClearPartial(4)) >= 1 }
        onNodeWithTag("tx-demo-banner").assertIsDisplayed()
    }
}
