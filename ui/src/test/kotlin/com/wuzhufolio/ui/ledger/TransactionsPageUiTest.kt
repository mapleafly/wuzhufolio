package com.wuzhufolio.ui.ledger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.CoinPositionDelta
import com.wuzhufolio.domain.ledger.CsvImportSummary
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.ledger.CsvPreviewRow
import com.wuzhufolio.domain.ledger.CsvRowError
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.FeeQuoteResult
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 交易管理页 UI 走查（M7 · T7.4 · 共性约束 7.3）：空态/列表渲染/添加弹窗首输入框聚焦 +
 * 键盘录入（performTextInput + assertIsFocused）/V1–V4 校验不触达服务/保存成功/删除确认/CSV 向导。
 */
@OptIn(ExperimentalTestApi::class)
class TransactionsPageUiTest {

    @Suppress("LongParameterList") // 测试行构造器（默认值 + 命名参数覆盖）
    private fun row(
        id: Long,
        pair: String = "BTC/USDT",
        side: Side = Side.BUY,
        price: String = "50000",
        qty: String = "0.1",
        realized: BigDecimal? = null,
    ): TransactionRow = TransactionRow(
        id = id,
        exchange = "BINANCE",
        exchangeOrderId = null,
        pair = pair,
        baseSymbol = pair.substringBefore("/"),
        quoteSymbol = pair.substringAfter("/"),
        side = side,
        price = BigDecimal(price),
        quantity = BigDecimal(qty),
        fee = BigDecimal("0.1"),
        feeCurrency = "USDT",
        total = BigDecimal(price).multiply(BigDecimal(qty)),
        time = Instant.parse("2026-08-30T08:00:00Z"),
        notes = null,
        source = "Manual",
        priceStatus = "OK",
        estimated = false,
        realizedPnlFiat = realized,
    )

    private inner class FakeLedgerService : TransactionLedgerService {
        val rows: MutableList<TransactionRow> = mutableListOf()
        var savedInput: TransactionInput? = null
        var updated: Pair<Long, TransactionInput>? = null
        var deletedIds: List<Long>? = null
        var feeQuoteResult: FeeQuoteResult? = null
        var searchResults: List<CatalogCoin> = emptyList()
        var parsePreview: CsvPreview? = null
        var importResult: CsvImportSummary = CsvImportSummary(1, 0, 0, emptyList())
        var importedSessionId: String? = null

        override suspend fun listTransactions(filter: TxFilter): List<TransactionRow> =
            rows.toList()

        override suspend fun saveTransaction(input: TransactionInput): Long {
            savedInput = input
            val r = row(rows.size + 1L, pair = input.baseSymbol + "/" + input.quoteSymbol,
                side = input.side, price = input.price.toPlainString(), qty = input.quantity.toPlainString())
            rows.add(r)
            return r.id
        }

        override suspend fun updateTransaction(id: Long, input: TransactionInput) {
            updated = id to input
        }

        override suspend fun deleteTransactions(ids: List<Long>) {
            deletedIds = ids
            rows.removeAll { it.id in ids }
        }

        override suspend fun feeQuote(request: FeeQuoteRequest): FeeQuoteResult? = feeQuoteResult

        override suspend fun searchCoins(query: String, limit: Int): List<CatalogCoin> = searchResults

        override suspend fun parseCsv(bytes: ByteArray): CsvPreview =
            parsePreview ?: CsvPreview(
                "s1", 0, 0, 0, 0,
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            )

        override suspend fun confirmCsvImport(
            sessionId: String,
            ambiguityChoices: Map<String, String>,
            includeRowKeys: Set<String>,
        ): CsvImportSummary {
            importedSessionId = sessionId
            return importResult
        }

        override fun csvTemplateCsv(): String = "exchange,pair,side,price,quantity,time"
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String, substring: Boolean = true): Int =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun emptyStateShowsAddButtonAndEmptyText() = runComposeUiTest {
        setContent { TransactionsPage(FakeLedgerService(), { null }, { null }) }
        onNodeWithTag("transactions-page").assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.EMPTY) >= 1 }
        onNodeWithTag("tx-add").assertIsDisplayed()
        onNodeWithTag("tx-import").assertIsDisplayed()
    }

    @Test
    fun addModalFocusesBaseInputAndSavesViaKeyboard() = runComposeUiTest {
        val svc = FakeLedgerService()
        setContent { TransactionsPage(svc, { null }, { null }) }
        onNodeWithTag("tx-add").performClick()
        onNodeWithTag("tx-modal", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("tx-base-input").assertIsFocused() }.isSuccess
        }
        // 键盘逐字录入（共性约束 7.3 验收强制项）
        onNodeWithTag("tx-base-input").performTextInput("BTC")
        onNodeWithTag("tx-quote-input").performTextInput("USDT")
        onNodeWithTag("tx-price-input").performTextInput("50000")
        onNodeWithTag("tx-qty-input").performTextInput("0.1")
        onNodeWithTag("tx-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals("BTC", svc.savedInput!!.baseSymbol)
        assertEquals("USDT", svc.savedInput!!.quoteSymbol)
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.SAVE_SUCCESS) >= 1 }
    }

    @Test
    fun emptyFormShowsValidationErrorsAndDoesNotTouchService() = runComposeUiTest {
        val svc = FakeLedgerService()
        setContent { TransactionsPage(svc, { null }, { null }) }
        onNodeWithTag("tx-add").performClick()
        onNodeWithTag("tx-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.V4_REQUIRED) >= 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.V1_PRICE) >= 1 }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.V1_QTY) >= 1 }
        assertTrue(svc.savedInput == null, "空表单不得触达服务")
    }

    @Test
    fun listRendersSellRealizedAndDeleteConfirmFlow() = runComposeUiTest {
        val svc = FakeLedgerService().apply {
            rows.add(row(1L, pair = "BTC/USDT", side = Side.BUY))
            rows.add(row(2L, pair = "ETH/USDT", side = Side.SELL, realized = BigDecimal("150")))
        }
        setContent { TransactionsPage(svc, { null }, { null }) }
        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-row-2").assertIsDisplayed()
        // 卖出行显示已实现盈亏 +150（setScale(2) 展示为 +150.00，子串匹配）
        waitUntil(timeoutMillis = 2_000) { textCount("+150", substring = true) >= 1 }
        // 勾选 -> 删除交易 -> 确认框 -> 确认
        onNodeWithTag("tx-check-1").performClick()
        onNodeWithTag("tx-delete-batch").performClick()
        onNodeWithTag("tx-delete-confirm", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) {
            textCount(TransactionCopy.DELETE_CONFIRM.replace("N", "1"), substring = true) >= 1
        }
        onNodeWithTag("tx-delete-confirm-btn", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.deletedIds == listOf(1L) }
    }

    @Test
    fun rowEditOpensFormPrefilled() = runComposeUiTest {
        val svc = FakeLedgerService().apply {
            rows.add(row(7L, pair = "BTC/USDT", side = Side.SELL, realized = BigDecimal("5")))
        }
        setContent { TransactionsPage(svc, { null }, { null }) }
        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-edit-7").performClick()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("tx-modal", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.FORM_TITLE_EDIT) >= 1 }
    }

    @Test
    fun csvWizardParsesPreviewsAndConfirms() = runComposeUiTest {
        val svc = FakeLedgerService().apply {
            parsePreview = CsvPreview(
                sessionId = "s-1",
                totalRows = 1,
                newRows = 1,
                duplicateRows = 0,
                unresolvedRows = 0,
                errorRows = emptyList(),
                rows = listOf(
                    CsvPreviewRow(
                        rowKey = "L2",
                        time = Instant.parse("2025-01-10T08:00:00Z"),
                        pair = "BTC/USDT",
                        side = Side.BUY,
                        price = BigDecimal("50000"),
                        quantity = BigDecimal("0.1"),
                        status = com.wuzhufolio.domain.ledger.CsvRowStatus.IMPORT,
                    ),
                ),
                ambiguous = emptyList(),
                affectedCoins = listOf(CoinPositionDelta("BTC", BigDecimal("0.1"))),
                anomalousCoins = emptyList(),
            )
            importResult = CsvImportSummary(1, 0, 0, emptyList())
        }
        // 平台无关临时文件（Windows 无 /tmp；createTempFile 落在系统临时目录）
        val fakeCsv = java.nio.file.Files.createTempFile("fake", ".csv")
        java.nio.file.Files.writeString(fakeCsv, "x")
        setContent { TransactionsPage(svc, { fakeCsv.toString() }, { null }) }
        onNodeWithTag("tx-import").performClick()
        onNodeWithTag("csv-modal", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("csv-pick").performClick()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("csv-preview", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        waitUntil(timeoutMillis = 2_000) { textCount("新增 1 条") >= 1 }
        onNodeWithTag("csv-confirm", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.importedSessionId == "s-1" }
        waitUntil(timeoutMillis = 2_000) { textCount("导入完成 · 新增 1") >= 1 }
    }

    @Test
    fun filterButtonsPersistQueryAndSideFilter() = runComposeUiTest {
        val svc = FakeLedgerService().apply {
            rows.add(row(1L, pair = "BTC/USDT", side = Side.BUY))
            rows.add(row(2L, pair = "ETH/USDT", side = Side.SELL))
        }
        setContent { TransactionsPage(svc, { null }, { null }) }
        waitUntil(timeoutMillis = 2_000) { textCount("ETH/USDT") >= 1 }
        onNodeWithTag("tx-filter-sell").performClick()
        // 假服务不过滤（本地展示），断言按钮切换不崩溃且列表仍在
        waitUntil(timeoutMillis = 2_000) { textCount("ETH/USDT") >= 1 }
        onNodeWithTag("tx-search").performTextInput("ETH")
        onNodeWithTag("tx-search").assertIsDisplayed()
    }

    // ---- 2026-09-07 GUI 走查修复轮回归 ----

    /** 问题 3/6：手续费填 0 可保存（此前走查表现为「保存无反应」）。 */
    @Test
    fun feeZeroIsAllowedAndSaveReachesService() = runComposeUiTest {
        val svc = FakeLedgerService()
        setContent { TransactionsPage(svc, { null }, { null }) }
        onNodeWithTag("tx-add").performClick()
        onNodeWithTag("tx-base-input").performTextInput("BTC")
        onNodeWithTag("tx-quote-input").performTextInput("USDT")
        onNodeWithTag("tx-price-input").performTextInput("50000")
        onNodeWithTag("tx-qty-input").performTextInput("0.1")
        onNodeWithTag("tx-fee-input").performTextInput("0")
        onNodeWithTag("tx-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.savedInput != null }
        assertEquals(0, BigDecimal.ZERO.compareTo(svc.savedInput!!.fee), "手续费 0 应可保存")
    }

    /** 问题 5：交易时间/备注输入组件必须可见（此前被弹窗裁切）。 */
    @Test
    fun formShowsTimeAndNotesInputs() = runComposeUiTest {
        setContent { TransactionsPage(FakeLedgerService(), { null }, { null }) }
        onNodeWithTag("tx-add").performClick()
        // 字段须可达（窗口较小时可滚动到；标准 1280×800 下全部可见）
        onNodeWithTag("tx-auto-fee", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("tx-total", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("tx-time-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag("tx-notes-input", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /** 问题 10：编辑弹窗保存须调用 updateTransaction 并关闭弹窗。 */
    @Test
    fun editModalSaveRoutesToUpdateAndCloses() = runComposeUiTest {
        val svc = FakeLedgerService().apply { rows.add(row(7L, pair = "BTC/USDT")) }
        setContent { TransactionsPage(svc, { null }, { null }) }
        waitUntil(timeoutMillis = 2_000) { textCount("BTC/USDT") >= 1 }
        onNodeWithTag("tx-edit-7").performClick()
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("tx-modal", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        onNodeWithTag("tx-qty-input").performTextInput("2")
        onNodeWithTag("tx-save", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2_000) { svc.updated != null }
        assertEquals(7L, svc.updated!!.first)
        waitUntil(timeoutMillis = 2_000) { textCount(TransactionCopy.UPDATE_SUCCESS) >= 1 }
    }

    /** 问题 4：计价币输入应出目录候选并可点选。 */
    @Test
    fun quoteInputShowsCandidatesAndPicks() = runComposeUiTest {
        val usdt = CatalogCoin(3L, "tether", null, "USDT", "Tether", CoinStatus.ACTIVE)
        val svc = FakeLedgerService().apply { searchResults = listOf(usdt) }
        setContent { TransactionsPage(svc, { null }, { null }) }
        onNodeWithTag("tx-add").performClick()
        onNodeWithTag("tx-quote-input").performTextInput("USDT")
        waitUntil(timeoutMillis = 2_000) { textCount("USDT · Tether") >= 1 }
        onNodeWithTag("suggestion-list", useUnmergedTree = true).assertIsDisplayed()
    }

    /** 问题 8：文件选择器须在非 EDT 线程调用（此前 invokeAndWait 从 EDT 调用直接崩溃）。 */
    @Test
    fun csvFilePickerRunsOffEventDispatchThread() = runComposeUiTest {
        val svc = FakeLedgerService()
        var pickerOnEdt: Boolean? = null
        setContent {
            TransactionsPage(
                svc,
                pickCsvFile = {
                    pickerOnEdt = java.awt.EventQueue.isDispatchThread()
                    null
                },
                pickTemplatePath = { null },
            )
        }
        onNodeWithTag("tx-import").performClick()
        onNodeWithTag("csv-pick").performClick()
        waitUntil(timeoutMillis = 2_000) { pickerOnEdt != null }
        assertEquals(false, pickerOnEdt, "文件选择器不得在 EDT 上调用 invokeAndWait")
    }

    /** 问题 8/9：模板下载应写出模板内容并提示（文件选择器同样在非 EDT 线程）。 */
    @Test
    fun templateDownloadWritesFileAndToasts() = runComposeUiTest {
        val svc = FakeLedgerService()
        val target = java.nio.file.Files.createTempDirectory("wz-tpl").resolve("template.csv")
        var pickerOnEdt: Boolean? = null
        setContent {
            TransactionsPage(
                svc,
                pickCsvFile = { null },
                pickTemplatePath = {
                    pickerOnEdt = java.awt.EventQueue.isDispatchThread()
                    target.toString()
                },
            )
        }
        onNodeWithTag("tx-import").performClick()
        onNodeWithTag("csv-template").performClick()
        // 等待写入完成（Files.exists 在创建瞬间即真，须等到内容落盘）
        waitUntil(timeoutMillis = 3_000) {
            runCatching { java.nio.file.Files.readString(target) == svc.csvTemplateCsv() }
                .getOrDefault(false)
        }
        assertEquals(false, pickerOnEdt, "模板保存路径选择不得在 EDT 上调用 invokeAndWait")
        assertEquals(svc.csvTemplateCsv(), java.nio.file.Files.readString(target))
        waitUntil(timeoutMillis = 2_000) { textCount("标准模板已下载", substring = true) >= 1 }
    }
}
