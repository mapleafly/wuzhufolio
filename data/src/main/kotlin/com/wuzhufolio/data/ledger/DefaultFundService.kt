package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.catalog.FiatLeg
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.LedgerEvent
import com.wuzhufolio.domain.engine.MutationVerdict
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.PortfolioCalculator
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.ledger.FiatValuePreview
import com.wuzhufolio.domain.ledger.FundConflictClassifier
import com.wuzhufolio.domain.ledger.FundDateRange
import com.wuzhufolio.domain.ledger.FundEntryRow
import com.wuzhufolio.domain.ledger.FundEntryType
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.FundPage
import com.wuzhufolio.domain.ledger.FundsOverview
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.CoinResolutionException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 资金管理用例实现（M8 · T8.1 + T8.2 列表半边 · 契约 = domain.ledger.FundService）。
 *
 * 编排口径：
 * - 手动增删改 = 事件构造（含操作/不含操作双列表，[LedgerEventAssembler] 三类事件合并）->
 *   ReplayEngine.validateMutation 相对校验（M4 §5-5）-> [FundConflictClassifier] 分类
 *  （撤资本位点 V7 / 其余 V9）上浮 [LedgerValidationException]，文案映射在 UI 层（ui/ledger/FundsCopy）；
 * - **V7 撤资同点绝对校验**（M4 §5-5「宽松口径复核归 M8」的收紧结论）：撤资本位点出现任何负边界
 *  （含导入既存异常位点被继续加深）都阻止——相对校验对「既存负位点继续恶化」放行，
 *   对撤资而言会静默加深持仓异常，故收紧（模块记录 M8 §5）；
 * - 折算 = 记录时行情价链（事件构造层 [TransactionEventBuilder.resolveFiatValue]）；
 *   完全缺失 = 名义零折算 + PENDING（保数量链，黄金用例 9 回填自动纠正）；
 * - 校准行以「校准」类型并入列表（不可编辑、可删除——删除同样走相对校验）。
 */
@Suppress("TooManyFunctions", "LongParameterList") // 用例面（列表/增删改/总览/预览/检索）+ 依赖注入袋固有
class DefaultFundService(
    private val sessions: ActiveSessionStore,
    private val transactions: LedgerTransactionRepository,
    private val funds: FundFlowRepository,
    private val recons: ReconciliationRepository,
    private val catalog: CoinCatalog,
    private val settings: SettingsRepository,
    private val eventBuilder: TransactionEventBuilder,
    private val assembler: LedgerEventAssembler,
) : FundService {

    // ---- 列表 + 总览（T8.2/T8.3） ----

    override suspend fun listFunds(filter: FundFilter): FundPage {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val baseFiat = baseFiat()
        val rows = loadRows(accountId)
        val build = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat)
        val coinCache = HashMap<Long, CatalogCoin?>()
        suspend fun coinOf(id: Long): CatalogCoin? = coinCache.getOrPut(id) { catalog.getById(id) }

        val fundEvents = build.events.filterIsInstance<FundEvent>().associateBy { it.id }
        val entries = ArrayList<FundEntryRow>(rows.funds.size + rows.recons.size)
        for (row in rows.funds) {
            val coin = coinOf(row.coinId) ?: continue
            val event = fundEvents[row.uuid]
            entries += FundEntryRow(
                id = row.id,
                uuid = row.uuid,
                entryType = if (row.kind == FlowKind.WITHDRAWAL) FundEntryType.WITHDRAWAL else FundEntryType.DEPOSIT,
                kind = row.kind,
                coinSymbol = coin.symbol,
                quantity = row.amount,
                baseAmount = event?.fiatValue ?: row.baseAmount,
                time = row.time,
                sourceDest = row.sourceDest,
                notes = row.notes,
                estimated = event?.estimated ?: true,
            )
        }
        for (row in rows.recons) {
            val coin = coinOf(row.coinId) ?: continue
            entries += FundEntryRow(
                id = row.id,
                uuid = row.uuid,
                entryType = FundEntryType.RECONCILIATION,
                kind = null,
                coinSymbol = coin.symbol,
                quantity = row.delta,
                baseAmount = row.baseAmount, // 差额账务按记录值固定（M4 §5-7），不随行情重解析
                time = row.createdAt,
                sourceDest = row.exchange,
                notes = null,
                estimated = false,
            )
        }
        return FundPage(applyFilter(entries, filter), overview(build.events, baseFiat))
    }

    // ---- 手动增删改（T8.1） ----

    override suspend fun saveFund(input: FundInput): Long {
        val session = sessions.requireActive()
        val accountId = session.account.id
        validateShape(input)
        val coin = resolveFundCoin(input.coinSymbol)
        val baseFiat = baseFiat()
        val rows = loadRows(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val seq = assembler.nextSeq(rows.tx, rows.funds, rows.recons)
        val candidate = mutationEvent(
            id = "candidate-" + UUID.randomUUID(),
            seq = seq,
            coin = coin,
            kind = input.kind,
            quantity = input.quantity,
            at = input.time,
            baseFiat = baseFiat,
        )
        validateMutation(without + candidate, without, candidate)
        if (input.kind == FlowKind.WITHDRAWAL) {
            assertNoViolationAt(without + candidate, candidate, coin.symbol)
        }
        val value = eventBuilder.resolveFiatValue(coin, input.quantity, baseFiat, input.time)
        return funds.insert(
            NewFundFlowRow(
                accountId = accountId,
                kind = input.kind,
                amount = input.quantity,
                baseAmount = value.fiat,
                currency = coin.symbol.uppercase(),
                coinId = coin.id.toInt(),
                time = input.time,
                sourceDest = input.sourceDest?.trim()?.takeIf { it.isNotEmpty() },
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
                priceStatus = if (value.estimated) PRICE_PENDING else PRICE_OK,
            ),
        )
    }

    override suspend fun updateFund(id: Long, input: FundInput) {
        val session = sessions.requireActive()
        val accountId = session.account.id
        validateShape(input)
        val old = funds.findById(accountId, id) ?: throw IllegalArgumentException("资金记录不存在或已删除")
        val coin = resolveFundCoin(input.coinSymbol)
        val baseFiat = baseFiat()
        val rows = loadRows(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val candidate = mutationEvent(old.uuid, old.id, coin, input.kind, input.quantity, input.time, baseFiat)
        val rowsWithoutOld = rows.copy(funds = rows.funds.filter { it.id != old.id })
        val withOp = assembler.build(rowsWithoutOld.tx, rowsWithoutOld.funds, rowsWithoutOld.recons, baseFiat).events +
            candidate
        validateMutation(withOp, without, candidate)
        if (input.kind == FlowKind.WITHDRAWAL) {
            assertNoViolationAt(withOp, candidate, coin.symbol)
        }
        val value = eventBuilder.resolveFiatValue(coin, input.quantity, baseFiat, input.time)
        funds.update(
            UpdatedFundFlowRow(
                id = id,
                accountId = accountId,
                kind = input.kind,
                amount = input.quantity,
                baseAmount = value.fiat,
                currency = coin.symbol.uppercase(),
                coinId = coin.id.toInt(),
                time = input.time,
                sourceDest = input.sourceDest?.trim()?.takeIf { it.isNotEmpty() },
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    override suspend fun deleteFunds(uuids: List<String>) {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val doomed = uuids.distinct()
        if (doomed.isEmpty()) return
        val fundRows = funds.findByUuids(accountId, doomed)
        val reconRows = recons.findByUuids(accountId, doomed)
        val found = fundRows.size + reconRows.size
        require(found == doomed.size) { "部分资金记录不存在或已删除" }
        val baseFiat = baseFiat()
        val rows = loadRows(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val doomedSet = doomed.toSet()
        val rowsWithout = rows.copy(
            tx = rows.tx.filter { it.uuid !in doomedSet },
            funds = rows.funds.filter { it.uuid !in doomedSet },
            recons = rows.recons.filter { it.uuid !in doomedSet },
        )
        val withOp = assembler.build(rowsWithout.tx, rowsWithout.funds, rowsWithout.recons, baseFiat).events
        validateMutation(withOp, without, operated = null)
        funds.deleteByUuids(accountId, doomed)
        recons.deleteByUuids(accountId, doomed)
    }

    // ---- 总览 / 预览 / 检索（T8.3） ----

    override suspend fun fundsOverview(): FundsOverview {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val baseFiat = baseFiat()
        val rows = loadRows(accountId)
        val build = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat)
        return overview(build.events, baseFiat)
    }

    /** 表单折算预览（记录时行情价；null = 暂无行情价）。 */
    @Suppress("ReturnCount") // 数量/币种/待定价三态早退
    override suspend fun fiatValuePreview(
        coinSymbol: String,
        quantity: BigDecimal,
        at: Instant,
    ): FiatValuePreview {
        if (quantity.signum() <= 0) return FiatValuePreview(null, false)
        val coin = runCatching { resolveFundCoin(coinSymbol) }.getOrNull()
            ?: return FiatValuePreview(null, false)
        val value = eventBuilder.resolveFiatValue(coin, quantity, baseFiat(), at)
        // 名义零折算（无任何可得价）= 待定价（null）；估算路径（最近可得价）保留金额并标估算
        if (value.estimated && value.fiat.signum() == 0) return FiatValuePreview(null, false)
        return FiatValuePreview(value.fiat, value.estimated)
    }

    override suspend fun searchCoins(query: String, limit: Int): List<CatalogCoin> = catalog.search(query, limit)

    override suspend fun defaultCoinSymbol(): String {
        val fiat = settings.getGlobal(DefaultTransactionLedgerService.SETTING_FIAT)?.takeIf { it.isNotBlank() }
            ?: "USD"
        // PRD §9.8：基础法币为 USD 时默认 USDT，其余法币默认 USDC
        return if (fiat.equals("USD", ignoreCase = true)) "USDT" else "USDC"
    }

    // ---- 内部 ----

    private val rowSource = LedgerRowsSource(transactions, funds, recons)

    private suspend fun loadRows(accountId: Int) = rowSource.load(accountId)

    private suspend fun mutationEvent(
        id: String,
        seq: Long,
        coin: CatalogCoin,
        kind: FlowKind,
        quantity: BigDecimal,
        at: Instant,
        baseFiat: String,
    ): FundEvent {
        val value = eventBuilder.resolveFiatValue(coin, quantity, baseFiat, at)
        return FundEvent(
            id = id,
            at = at,
            coin = coin.cgId,
            kind = kind,
            quantity = quantity,
            fiatValue = value.fiat,
            seq = seq,
            estimated = value.estimated,
        )
    }

    /** 相对校验 + 违例分类（V7 撤资 / V9 其余——api-contracts §4 错误码映射）。 */
    private suspend fun validateMutation(
        withOp: List<LedgerEvent>,
        without: List<LedgerEvent>,
        operated: FundEvent?,
    ) {
        when (val verdict = ReplayEngine.validateMutation(withOp, without)) {
            is MutationVerdict.Ok -> Unit
            is MutationVerdict.Blocked -> {
                val violation = verdict.violation
                val code = FundConflictClassifier.classify(violation, operated)
                val symbol = catalog.getByCgId(violation.coinId)?.symbol ?: violation.coinId
                throw LedgerValidationException(
                    code = code,
                    coinSymbol = symbol,
                    shortage = violation.shortage.abs(),
                    conflictAt = violation.at,
                )
            }
        }
    }

    /**
     * V7 撤资同点绝对校验（M8 收紧口径）：撤资本位点出现任何负边界（含导入既存异常位点被加深）
     * 即阻止——「XX 持仓不足，无法撤资」。
     */
    private fun assertNoViolationAt(events: List<LedgerEvent>, operated: FundEvent, coinSymbol: String) {
        val violation = ReplayEngine.replay(events, NegativePolicy.LENIENT)
            .violations
            .firstOrNull { it.eventId == operated.id }
            ?: return
        throw LedgerValidationException(
            code = LedgerErrorCode.INSUFFICIENT_POSITION,
            coinSymbol = coinSymbol,
            shortage = violation.shortage.abs(),
        )
    }

    /** 总览卡（PortfolioCalculator 单一真源；缺价币不计现金——「无行情」语义）。 */
    private suspend fun overview(events: List<LedgerEvent>, baseFiat: String): FundsOverview {
        val outcome = ReplayEngine.replay(events, NegativePolicy.LENIENT)
        val prices = HashMap<String, BigDecimal>()
        for (cgId in outcome.holdings.keys) {
            val coin = catalog.getByCgId(cgId) ?: continue
            eventBuilder.currentPrice(coin.id, baseFiat)?.let { prices[cgId] = it }
        }
        val metrics = PortfolioCalculator().compute(outcome, prices)
        return FundsOverview(
            availableCashFiat = metrics.availableCashFiat,
            investedNetFiat = metrics.investedNetFiat,
            cumulativeDepositsFiat = metrics.cumulativeDepositsFiat,
            cumulativeWithdrawalsFiat = metrics.cumulativeWithdrawalsFiat,
        )
    }

    private fun applyFilter(entries: List<FundEntryRow>, filter: FundFilter): List<FundEntryRow> {
        val query = filter.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val now = Instant.now()
        return entries
            .filter { row ->
                (filter.type == null || row.entryType == filter.type) &&
                    filter.dateRange.contains(row.time, now) &&
                    (query == null ||
                        row.coinSymbol.lowercase().contains(query) ||
                        (row.sourceDest?.lowercase()?.contains(query) == true) ||
                        (row.notes?.lowercase()?.contains(query) == true))
            }
            .sortedWith(compareByDescending<FundEntryRow> { it.time }.thenByDescending { it.id })
    }

    /**
     * 币种解析（法币不入账——PRD 故事 6.1/V1.4「法币退出账本资产」）：孪生映射法币点名稳定币
     * （USD -> 请记录 USDT），无映射法币同样拒绝；普通币种走目录精确/四级消歧（与交易半边同口径）。
     */
    @Suppress("ThrowsCount") // 法币两态 + 消歧两态各抛类型化异常（调用方 UI 逐态映射文案）
    private suspend fun resolveFundCoin(symbol: String): CatalogCoin {
        val norm = symbol.trim().uppercase()
        when (val leg = catalog.fiatQuoteLeg(norm)) {
            is FiatLeg.TwinStable -> throw CoinResolutionException(
                norm,
                FIAT_HINT + "请记录兑换后实际到账的 " + leg.stableCoin.symbol,
            )
            is FiatLeg.ThirdCurrency -> throw CoinResolutionException(
                norm,
                FIAT_HINT + "请记录兑换后实际到账的稳定币",
            )
            null -> Unit
        }
        val exact = catalog.getBySymbol(norm).filter { it.status == CoinStatus.ACTIVE }
        if (exact.size == 1) return exact.first()
        return when (val res = catalog.resolve(null, norm)) {
            is Resolution.Unique -> res.coin
            is Resolution.Ambiguous -> throw CoinResolutionException(
                norm,
                "歧义（同名资产 " + res.candidates.size + " 个，请从候选选择）",
            )
            Resolution.NotFound -> throw CoinResolutionException(norm, "未收录（币种目录无此资产，请更新目录或检查拼写）")
        }
    }

    private fun validateShape(input: FundInput) {
        require(input.coinSymbol.trim().isNotEmpty()) { "币种必填" }
        require(input.quantity.signum() > 0) { "数量必须大于 0（V6）" }
    }

    private fun baseFiat(): String =
        settings.getGlobal(DefaultTransactionLedgerService.SETTING_FIAT)?.takeIf { it.isNotBlank() } ?: "USD"

    private fun ActiveSessionStore.requireActive(): com.wuzhufolio.data.accounts.ActiveSession =
        get() ?: error("未登录：请先登录账户")

    companion object {
        const val PRICE_OK = "OK"
        const val PRICE_PENDING = "PENDING"

        /** 法币输入提示前缀（PRD §9.8「输入法币代码时提示改为记录兑换后到账的稳定币」）。 */
        const val FIAT_HINT = "法币不入账本（仅作计价单位），"
    }
}
