package com.wuzhufolio.ui.portfolio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.catalog.FiatLeg
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.catalog.DirectoryRefreshSummary
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationResult
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.FeeQuoteResult
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.ledger.CsvImportSummary
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.domain.market.TwentyFourHour
import com.wuzhufolio.domain.portfolio.CalibrationRecord
import com.wuzhufolio.domain.portfolio.CoinDetail
import com.wuzhufolio.domain.portfolio.PortfolioRow
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.portfolio.PortfolioSnapshot
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.GeneralSettingsView
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.PrecisionPreset
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.i18n.I18n
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.theme.WuzhuTheme
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 聚合页（仪表盘 / 资产列表 / 币种详情）单测 + Compose 离屏走查（M12 T12.1）。
 *
 * 覆盖：
 * - 排序（市值/名称/盈亏，点击表头切换方向）；
 * - 环形图小额阈值归并「其他」与分区占比；
 * - 24h 副行标注（覆盖 N/M、混合数据源、"--"）；
 * - 仪表盘总览卡与环形图渲染、扇区点击后浮窗内容；
 * - 资产列表「无行情」「持仓异常」标注与行点击进入币种详情；
 * - 币种详情汇总 + 三重筛选 + 校准入口可见性（单一来源才显示）。
 */
@OptIn(ExperimentalTestApi::class)
class PortfolioPagesUiTest {

    // ---------- 假实现 ----------

    private class FakeGeneralSettings : GeneralSettingsService {
        var view = GeneralSettingsView()
        override suspend fun view(): GeneralSettingsView = view
        override suspend fun setBaseFiat(code: String) = Unit
        override suspend fun setPrecision(preset: PrecisionPreset) = Unit
        override suspend fun setUsernameEnum(on: Boolean) = Unit
        override suspend fun addCashCoin(cgId: String) = Unit
        override suspend fun removeCashCoin(cgId: String) = Unit
        override suspend fun setSmallAmountThreshold(threshold: BigDecimal) = Unit
        override suspend fun setProxyEnabled(on: Boolean) = Unit
        override suspend fun setLanguage(language: AppLanguage) = Unit
    }

    private class FakeRefresh : MarketRefreshService {
        override suspend fun refresh(manual: Boolean, coins: List<String>, fiats: List<String>): MarketRefreshResult =
            MarketRefreshResult(
                at = NOW,
                source = PriceSource.COINGECKO,
                refreshedCoins = coins.size,
                untracked = emptyList(),
                quotaPercentUsed = null,
                error = null,
                cgConfigured = false,
                cmcConfigured = false,
            )

        override fun lastResult(): MarketRefreshResult? = null
        override suspend fun quotaPercentUsed(): Int? = null
        override suspend fun directoryFresh(): Boolean = true
    }

    private class FakePortfolio(
        private val rows: List<PortfolioRow>,
        private val fiat: String = "USD",
        private val change24h: TwentyFourHour.Result = TwentyFourHour.Result(null, null, 0, 0, false),
        private val calibrations: List<CalibrationRecord> = emptyList(),
    ) : PortfolioService {
        override suspend fun snapshot(): PortfolioSnapshot {
            val replay = ReplayEngine.replay(emptyList())
            val metrics = PortfolioCalculator().compute(
                replay,
                rows.mapNotNull { row -> row.priceFiat?.let { row.cgId to it } }.toMap(),
            )
            return PortfolioSnapshot(
                fiat = fiat,
                metrics = metrics,
                rows = rows,
                change24h = change24h,
                priceAsOf = NOW,
                estimated = false,
                anomalousCoins = rows.filter { it.anomalous }.map { it.cgId },
            )
        }

        override suspend fun coinDetail(cgId: String, filter: TxFilter): CoinDetail {
            val row = rows.firstOrNull { it.cgId == cgId }
            return CoinDetail(
                cgId = cgId,
                symbol = row?.symbol ?: cgId,
                name = row?.name ?: cgId,
                row = row,
                calibrations = calibrations,
            )
        }
    }

    private class FakeLedger(private val rows: List<TransactionRow>) : TransactionLedgerService {
        var lastFilter: TxFilter? = null
        override suspend fun listTransactions(filter: TxFilter): List<TransactionRow> {
            lastFilter = filter
            return rows
        }

        override suspend fun saveTransaction(input: TransactionInput): Long = 1L
        override suspend fun updateTransaction(id: Long, input: TransactionInput) = Unit
        override suspend fun deleteTransactions(ids: List<Long>) = Unit
        override suspend fun feeQuote(request: FeeQuoteRequest): FeeQuoteResult? = null
        override suspend fun searchCoins(query: String, limit: Int): List<CatalogCoin> = emptyList()
        override suspend fun parseCsv(bytes: ByteArray): CsvPreview = error("not used")
        override suspend fun confirmCsvImport(
            sessionId: String,
            ambiguityChoices: Map<String, String>,
            includeRowKeys: Set<String>,
        ): CsvImportSummary = error("not used")
        override fun csvTemplateCsv(): String = ""
    }

    private class FakeCatalog(private val coins: Map<String, CatalogCoin>) : CoinCatalog {
        override suspend fun search(query: String, limit: Int): List<CatalogCoin> = emptyList()
        override suspend fun getBySymbol(symbol: String): List<CatalogCoin> =
            coins.values.filter { it.symbol.equals(symbol, ignoreCase = true) }
        override suspend fun getByCgId(cgId: String): CatalogCoin? = coins[cgId]
        override suspend fun getById(id: Long): CatalogCoin? = coins.values.firstOrNull { it.id == id }
        override suspend fun refreshDirectory(entries: List<CoinDirectoryEntry>): DirectoryRefreshSummary =
            DirectoryRefreshSummary(0, 0)
        override suspend fun refreshCmcIds(cmcByCgId: Map<String, String>): Int = 0
        override suspend fun resolve(
            exchange: String?,
            asset: String,
            context: com.wuzhufolio.domain.catalog.ResolveContext,
        ): Resolution = Resolution.NotFound
        override suspend fun freezeMapping(exchange: String, asset: String, coinId: Long) = Unit
        override suspend fun mappingFor(exchange: String, asset: String): CatalogCoin? = null
        override suspend fun exchangeAssetFor(exchange: String, coinId: Long): String? = null
        override suspend fun fiatQuoteLeg(quoteSymbol: String): FiatLeg? = null
    }

    private class FakeCalibration(private val prep: CalibrationPreparation?) : CalibrationUseCase {
        var executed = 0
        override suspend fun prepare(coinSymbol: String, pickedCoinId: Long?): CalibrationPreparation =
            prep ?: error("blocked")
        override suspend fun execute(coinSymbol: String, pickedCoinId: Long?): CalibrationResult {
            executed++
            return CalibrationResult(recorded = true, row = null)
        }
        override suspend fun history(coinSymbol: String, pickedCoinId: Long?) = emptyList<Nothing>()
    }

    @Suppress("LongParameterList") // 测试构造器：字段与页面列一一对应，命名参数调用更易读
    private fun row(
        cgId: String,
        symbol: String,
        name: String,
        quantity: String,
        avgCost: String?,
        price: String?,
        realized: String = "0",
        anomalous: Boolean = false,
        sources: Set<RecordSource> = setOf(RecordSource.Exchange("BINANCE")),
    ): PortfolioRow {
        val qty = BigDecimal(quantity)
        val px = price?.let { BigDecimal(it) }
        val marketValue = px?.multiply(qty)
        val cost = avgCost?.let { BigDecimal(it).multiply(qty) }
        return PortfolioRow(
            cgId = cgId,
            symbol = symbol,
            name = name,
            quantity = qty,
            avgCostFiat = avgCost?.let { BigDecimal(it) },
            priceFiat = px,
            marketValueFiat = marketValue,
            floatPnlFiat = if (marketValue != null && cost != null) marketValue - cost else null,
            floatPnlPercent = if (marketValue != null && cost != null && cost.signum() != 0) {
                (marketValue - cost).multiply(BigDecimal(100)).divide(cost, 8, java.math.RoundingMode.HALF_UP)
            } else {
                null
            },
            realizedPnlFiat = BigDecimal(realized),
            sharePercent = null,
            priced = px != null,
            anomalous = anomalous,
            estimated = false,
            sources = sources,
            sourceClassification = com.wuzhufolio.domain.engine.ReconciliationService()
                .classifySources(sources),
        )
    }

    private fun threeRows(): List<PortfolioRow> = listOf(
        row("bitcoin", "BTC", "Bitcoin", "0.5", "40000", "50000", realized = "4915"),
        row("ethereum", "ETH", "Ethereum", "4", "2000", "1800", realized = "-100"),
        row("tether", "USDT", "Tether", "1000", "1", null, anomalous = true),
    )

    // ---------- VM 级单测 ----------

    @Test
    fun `default sort is market value descending and header click flips direction`() = runComposeUiTest {
        val vm = PortfolioViewModel(
            FakePortfolio(threeRows()),
            FakeRefresh(),
            FakeGeneralSettings(),
        )
        waitUntil { vm.state.value.loading.not() }
        assertEquals(listOf("bitcoin", "ethereum", "tether"), vm.state.value.rows.map { it.cgId })

        vm.toggleSort(AssetSortKey.COIN)
        assertEquals(listOf("bitcoin", "ethereum", "tether"), vm.state.value.rows.map { it.cgId })
        vm.toggleSort(AssetSortKey.COIN)
        assertEquals(listOf("tether", "ethereum", "bitcoin"), vm.state.value.rows.map { it.cgId })

        vm.toggleSort(AssetSortKey.FLOAT_PNL)
        assertEquals("bitcoin", vm.state.value.rows.first().cgId)
        vm.dispose()
    }

    @Test
    fun `distribution merges slices below the small threshold into other`() = runComposeUiTest {
        val settings = FakeGeneralSettings().apply {
            view = GeneralSettingsView(smallThreshold = BigDecimal("10000"))
        }
        val vm = PortfolioViewModel(FakePortfolio(threeRows()), FakeRefresh(), settings)
        waitUntil { vm.state.value.loading.not() }
        val slices = vm.state.value.distribution
        // BTC 25,000 ≥ 阈值；ETH 7,200 与 USDT 无价（不计）→ ETH 归入「其他」
        assertEquals(listOf("bitcoin", PortfolioUiState.OTHER_SLICE_ID), slices.map { it.cgId })
        assertEquals("其他", slices.last().label)
        assertEquals(BigDecimal("7200"), slices.last().value)
        vm.dispose()
    }

    @Test
    fun `twenty four hour subtitle carries coverage and mixed source notes`() {
        val partial = TwentyFourHour.Result(
            pnlFiat = BigDecimal("120.5"),
            pct = BigDecimal("1.25"),
            covered = 2,
            total = 3,
            mixedSource = true,
        )
        val subtitle = change24hSubtitle(partial)!!
        assertTrue(subtitle.contains("+1.25%"), subtitle)
        assertTrue(subtitle.contains("覆盖 2/3 个币种"), subtitle)
        assertTrue(subtitle.contains("混合数据源"), subtitle)
        assertEquals(null, change24hSubtitle(null))
    }

    // ---------- Compose 渲染走查 ----------

    @Test
    fun `dashboard renders overview cards donut and popup on slice click`() = runComposeUiTest {
        val vm = PortfolioViewModel(FakePortfolio(threeRows()), FakeRefresh(), FakeGeneralSettings())
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                DashboardPage(
                    portfolioService = FakePortfolio(threeRows()),
                    refreshService = FakeRefresh(),
                    generalSettings = FakeGeneralSettings(),
                    accountName = "Alex",
                )
            }
        }
        onNodeWithTag("page-DASHBOARD").assertIsDisplayed()
        onNodeWithTag("card-net-value").assertIsDisplayed()
        onNodeWithTag("card-24h").assertIsDisplayed()
        onNodeWithTag("card-roi").assertIsDisplayed()
        onNodeWithTag("asset-donut").assertIsDisplayed()
        onNodeWithText("账户 Alex · 全部币种持仓按现价折算 USD").assertIsDisplayed()

        onNodeWithTag("donut-legend-bitcoin").performClick()
        onNodeWithTag("donut-pop").assertIsDisplayed()
        onNodeWithText("持有数量").assertIsDisplayed()
        onNodeWithTag("donut-pop-close").performClick()
        vm.dispose()
    }

    @Test
    fun `assets page marks unpriced rows and opens coin detail on row click`() = runComposeUiTest {
        var opened: String? = null
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                AssetsPage(
                    portfolioService = FakePortfolio(threeRows()),
                    refreshService = FakeRefresh(),
                    generalSettings = FakeGeneralSettings(),
                    onOpenCoin = { opened = it },
                )
            }
        }
        onNodeWithTag("page-ASSETS").assertIsDisplayed()
        onNodeWithTag("noprice-tether", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("anomaly-tether", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("无行情", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("sort-market-value").performClick()
        onNodeWithTag("holding-row-bitcoin").performClick()
        assertEquals("bitcoin", opened)
    }

    @Test
    fun `coin detail shows summary filters realised pnl and calibration entry`() = runComposeUiTest {
        val ledger = FakeLedger(
            listOf(
                txRow(1L, "BTC/USDT", Side.BUY, null),
                txRow(2L, "BTC/USDT", Side.SELL, BigDecimal("4915")),
            ),
        )
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                CoinDetailPage(
                    cgId = "bitcoin",
                    portfolioService = FakePortfolio(threeRows()),
                    ledgerService = ledger,
                    calibrationUseCase = FakeCalibration(preparation()),
                    catalog = FakeCatalog(mapOf("bitcoin" to catalogCoin("bitcoin", "BTC", 1L))),
                    onBack = {},
                )
            }
        }
        onNodeWithTag("page-COIN-DETAIL").assertIsDisplayed()
        onNodeWithTag("coin-qty").assertIsDisplayed()
        onNodeWithTag("coin-value").assertIsDisplayed()
        onNodeWithTag("coin-tx-2").assertIsDisplayed()
        onNodeWithTag("coin-tx-realized-2").assertIsDisplayed()
        onNodeWithTag("calibrate-open").assertIsDisplayed()
        onNodeWithTag("coin-filter-side").assertIsDisplayed()

        // 类型筛选（卖出）→ 过滤条件传给服务（口径 = TxFilter.side）
        onNodeWithTag("coin-filter-side").performClick()
        onNodeWithTag("coin-filter-side-opt-2").performClick()
        waitForIdle()
        assertEquals(Side.SELL, ledger.lastFilter?.side)
    }

    @Test
    fun `coin detail hides the calibration entry for multi source coins`() = runComposeUiTest {
        val multi = listOf(
            row(
                "bitcoin", "BTC", "Bitcoin", "0.5", "40000", "50000",
                sources = setOf(RecordSource.Exchange("BINANCE"), RecordSource.Manual),
            ),
        )
        setContent {
            WuzhuTheme(themeMode = ThemeMode.LIGHT) {
                CoinDetailPage(
                    cgId = "bitcoin",
                    portfolioService = FakePortfolio(multi),
                    ledgerService = FakeLedger(emptyList()),
                    calibrationUseCase = FakeCalibration(null),
                    catalog = FakeCatalog(emptyMap()),
                    onBack = {},
                )
            }
        }
        onNodeWithTag("calibrate-hint").assertIsDisplayed()
        onNodeWithTag("coin-calibrations-empty").assertIsDisplayed()
    }

    @Test
    fun `language switch rerenders the aggregator pages in english`() = runComposeUiTest {
        I18n.set(AppLanguage.EN)
        WzFormat.precision = PrecisionPreset.DEFAULT
        try {
            setContent {
                WuzhuTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.EN) {
                    AssetsPage(
                        portfolioService = FakePortfolio(threeRows()),
                        refreshService = FakeRefresh(),
                        generalSettings = FakeGeneralSettings(),
                        onOpenCoin = {},
                    )
                }
            }
            onNodeWithText("Portfolio").assertIsDisplayed()
            onNodeWithText("Unrealized P&L").assertIsDisplayed()
            onNodeWithText("No price", useUnmergedTree = true).assertIsDisplayed()
        } finally {
            I18n.set(AppLanguage.ZH)
        }
    }

    // ---------- 构造工具 ----------

    private fun txRow(id: Long, pair: String, side: Side, realized: BigDecimal?): TransactionRow = TransactionRow(
        id = id,
        exchange = "BINANCE",
        exchangeOrderId = null,
        pair = pair,
        baseSymbol = pair.substringBefore('/'),
        quoteSymbol = pair.substringAfter('/'),
        side = side,
        price = BigDecimal("50000"),
        quantity = BigDecimal("0.1"),
        fee = BigDecimal("0.0001"),
        feeCurrency = "BTC",
        total = BigDecimal("5000"),
        time = NOW,
        notes = null,
        source = "Manual",
        priceStatus = "OK",
        estimated = false,
        realizedPnlFiat = realized,
    )

    private fun catalogCoin(cgId: String, symbol: String, id: Long): CatalogCoin = CatalogCoin(
        id = id,
        cgId = cgId,
        cmcId = null,
        symbol = symbol,
        name = cgId,
        status = CoinStatus.ACTIVE,
    )

    private fun preparation(): CalibrationPreparation = CalibrationPreparation(
        coinSymbol = "BTC",
        coinName = "Bitcoin",
        exchangeName = "BINANCE",
        apiKeyId = 1L,
        apiKeyName = "main",
        localQuantity = BigDecimal("0.5"),
        exchangeQuantity = BigDecimal("0.6"),
        delta = BigDecimal("0.1"),
        deltaFiat = BigDecimal("5000"),
        marketPrice = BigDecimal("50000"),
        direction = FlowKind.DEPOSIT,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-10T07:24:00Z")

        /** 供断言引用，避免未用导入告警。 */
        val unusedRef: Class<*> = TradeEvent::class.java
    }
}
