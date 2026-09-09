package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.catalog.FiatLeg
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.catalog.ResolveContext
import com.wuzhufolio.domain.engine.FeeCalculator
import com.wuzhufolio.domain.engine.FeeCoinRole
import com.wuzhufolio.domain.engine.FeeRateResolver
import com.wuzhufolio.domain.engine.MutationVerdict
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import com.wuzhufolio.domain.engine.ConversionPx
import com.wuzhufolio.domain.ledger.AmbiguousTicker
import com.wuzhufolio.domain.ledger.CoinPositionDelta
import com.wuzhufolio.domain.ledger.CsvImportSummary
import com.wuzhufolio.domain.ledger.CsvPreview
import com.wuzhufolio.domain.ledger.CsvPreviewRow
import com.wuzhufolio.domain.ledger.CsvRowStatus
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.FeeQuoteRequest
import com.wuzhufolio.domain.ledger.FeeQuoteResult
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.ledger.ReplayConflictClassifier
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 交易账本用例实现（M7 · T7.1–T7.4 · 契约 = domain.ledger.TransactionLedgerService）。
 *
 * 编排口径：
 * - 手动增删改 = 事件构造（含操作/不含操作双列表）-> ReplayEngine.validateMutation 相对校验
 *  （M4 §5-5：仅拦「操作新制造」负边界）-> 违例经 ReplayConflictClassifier 分类（V5/V7 同构/V9）
 *   上浮 [LedgerValidationException]；文案映射在 UI 层（ui/ledger/TransactionCopy）；
 * - 离线保存（PRD N3「先保存，联网后自动回填重算」）：候选事件折算价缺失时以名义折算参与
 *   **数量校验**（负持仓校验只依赖数量流，与折算值无关），行照常落库、动态估算态由事件构造层承载；
 * - CSV = parse（模板/别名表解析、UTC 时间、消歧上浮、精确/模糊去重、影响摘要预览）-> confirm
 *  （歧义固化 MANUAL -> 逐行去重复核 -> LENIENT 导入 -> 负持仓「持仓异常」清单）；
 * - 费率自动计算（T7.2）= FeeRateResolver（交易所 > 全局）+ FeeCalculator 三币种基数 + 现价折算。
 */
@Suppress("TooManyFunctions", "LongParameterList")
// 用例面（列表/增删改/费率/CSV×2/解析辅助）+ 依赖注入袋固有，拆分类反而碎片化
class DefaultTransactionLedgerService(
    private val sessions: ActiveSessionStore,
    private val repository: LedgerTransactionRepository,
    private val fundsRepository: FundFlowRepository,
    private val reconsRepository: ReconciliationRepository,
    private val catalog: CoinCatalog,
    private val settings: SettingsRepository,
    private val feeRules: FeeRuleRepository,
    private val parser: CsvTradeParser,
    private val eventBuilder: TransactionEventBuilder,
    private val assembler: LedgerEventAssembler,
) : TransactionLedgerService {

    private val csvSessions = ConcurrentHashMap<String, CsvSession>()

    /** 三类记录联合读取 + 事件装配（M8 资金半边接入：重放校验输入 = 交易+资金+锚点全集）。 */
    private val rowSource = LedgerRowsSource(repository, fundsRepository, reconsRepository)

    private suspend fun assembledBuild(
        accountId: Int,
        baseFiat: String,
    ): LedgerEventAssembler.Assembled {
        val rows = rowSource.load(accountId)
        return assembler.build(rows.tx, rows.funds, rows.recons, baseFiat)
    }

    // ---- 列表（T7.4） ----

    override suspend fun listTransactions(filter: TxFilter): List<TransactionRow> {
        val session = sessions.requireActive()
        val rows = repository.listFiltered(session.account.id, filter)
        if (rows.isEmpty()) return emptyList()
        val baseFiat = baseFiat()
        val build = assembledBuild(session.account.id, baseFiat)
        val realized = SellRealizedTracer.realizedByEvent(build.events)
        val coinCache = HashMap<Long, CatalogCoin?>()
        suspend fun coinOf(id: Long): CatalogCoin? = coinCache.getOrPut(id) { catalog.getById(id) }
        return rows.map { row ->
            val base = coinOf(row.baseCoinId)
            val quote = coinOf(row.quoteCoinId)
            TransactionRow(
                id = row.id,
                exchange = row.exchange,
                exchangeOrderId = row.exchangeOrderId,
                pair = row.pair,
                baseSymbol = base?.symbol ?: row.pair.substringBefore("/"),
                quoteSymbol = quote?.symbol ?: row.pair.substringAfter("/", ""),
                side = row.side,
                price = row.price,
                quantity = row.quantity,
                fee = row.fee,
                feeCurrency = row.feeCurrency,
                total = row.price.multiply(row.quantity),
                time = row.time,
                notes = row.notes,
                source = row.source,
                priceStatus = if (row.id in build.tradeBuild.estimatedRowIds) PRICE_PENDING else PRICE_OK,
                estimated = row.id in build.tradeBuild.estimatedRowIds,
                realizedPnlFiat = if (row.side == Side.SELL) realized[row.uuid] else null,
            )
        }
    }

    // ---- 手动增删改（T7.1） ----

    override suspend fun saveTransaction(input: TransactionInput): Long {
        val session = sessions.requireActive()
        val accountId = session.account.id
        validateShape(input)
        val resolved = resolveInput(input)
        val rows = rowSource.load(accountId)
        val baseFiat = baseFiat()
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val seq = assembler.nextSeq(rows.tx, rows.funds, rows.recons)
        val candidate = mutationEvent(resolved, "candidate-" + UUID.randomUUID(), seq, baseFiat)
        validateMutation(without + candidate, without, candidate, rows.tx)
        return repository.insert(
            NewLedgerTxRow(
                accountId = accountId,
                exchange = resolved.exchange,
                exchangeOrderId = null,
                pair = resolved.pair,
                baseCoinId = resolved.base.id.toInt(),
                quoteCoinId = resolved.quote.id.toInt(),
                side = input.side,
                price = input.price,
                quantity = input.quantity,
                fee = input.fee,
                feeCurrency = resolved.feeCurrency,
                time = input.time,
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
                source = TransactionEventBuilder.SOURCE_MANUAL,
                priceStatus = PRICE_OK,
            ),
        )
    }

    override suspend fun updateTransaction(id: Long, input: TransactionInput) {
        val session = sessions.requireActive()
        val accountId = session.account.id
        validateShape(input)
        val old = repository.findById(accountId, id) ?: throw IllegalArgumentException("交易记录不存在或已删除")
        val resolved = resolveInput(input)
        val baseFiat = baseFiat()
        val rows = rowSource.load(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val rowsWithoutOld = rows.copy(tx = rows.tx.filter { it.id != old.id })
        val candidate = mutationEvent(resolved, old.uuid, old.id, baseFiat)
        val withOp = assembler.build(rowsWithoutOld.tx, rowsWithoutOld.funds, rowsWithoutOld.recons, baseFiat).events +
            candidate
        validateMutation(withOp, without, candidate, rows.tx)
        repository.update(
            UpdatedLedgerTxRow(
                accountId = accountId,
                id = id,
                exchange = resolved.exchange,
                exchangeOrderId = old.exchangeOrderId, // 编辑不改去重键与来源（M6 口径：来源/uuid 稳定）
                pair = resolved.pair,
                baseCoinId = resolved.base.id.toInt(),
                quoteCoinId = resolved.quote.id.toInt(),
                side = input.side,
                price = input.price,
                quantity = input.quantity,
                fee = input.fee,
                feeCurrency = resolved.feeCurrency,
                time = input.time,
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    override suspend fun deleteTransactions(ids: List<Long>) {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val targets = ids.map { id ->
            repository.findById(accountId, id) ?: throw IllegalArgumentException("交易记录 $id 不存在或已删除")
        }.distinctBy { it.id }
        if (targets.isEmpty()) return
        val baseFiat = baseFiat()
        val rows = rowSource.load(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val doomedIds = targets.map { it.id }.toSet()
        val rowsWithout = rows.copy(tx = rows.tx.filter { it.id !in doomedIds })
        val withOp = assembler.build(rowsWithout.tx, rowsWithout.funds, rowsWithout.recons, baseFiat).events
        validateMutation(withOp, without, null, rows.tx)
        repository.deleteByIds(accountId, targets.map { it.id })
    }

    /**
     * 相对校验 + 违例分类（V5/V7 同构/V9——api-contracts §4 错误码映射）。
     * [rows] = 当前账户交易全量行：用于把 REPLAY_CONFLICT 定位到具体冲突记录（时间/方向/交易对/数量），
     * 让「删除一笔买入被其后卖出拦住」这类提示可操作（2026-09-08 修复轮）。
     * 事件列表 = 三类事件全集（M8 资金半边接入——交易校验须计入资金/锚点）。
     */
    private suspend fun validateMutation(
        withOp: List<com.wuzhufolio.domain.engine.LedgerEvent>,
        without: List<com.wuzhufolio.domain.engine.LedgerEvent>,
        operated: TradeEvent?,
        rows: List<LedgerTxRow>,
    ) {
        when (val verdict = ReplayEngine.validateMutation(withOp, without)) {
            is MutationVerdict.Ok -> Unit
            is MutationVerdict.Blocked -> {
                val violation = verdict.violation
                val code = ReplayConflictClassifier.classify(violation, operated)
                val symbol = catalog.getByCgId(violation.coinId)?.symbol ?: violation.coinId
                val conflicting = rows.firstOrNull { it.uuid == violation.eventId }
                throw LedgerValidationException(
                    code = code,
                    coinSymbol = symbol,
                    shortage = violation.shortage.abs(),
                    conflictAt = conflicting?.time ?: violation.at,
                    conflictSide = conflicting?.side,
                    conflictPair = conflicting?.pair,
                    conflictQuantity = conflicting?.quantity,
                )
            }
        }
    }

    /**
     * 校验/变更用事件（永不返回 null）：折算价缺失时以名义折算（quote≈1）构造——
     * 负持仓校验只依赖数量流（legFiat/feeFiat 不影响数量迁移），行保存照常（PRD N3 待定价语义）。
     */
    private suspend fun mutationEvent(resolved: ResolvedInput, id: String, seq: Long, baseFiat: String): TradeEvent {
        val draft = resolved.draft(id, seq)
        return eventBuilder.buildEvent(draft, baseFiat) ?: TradeEvent(
            id = draft.id,
            at = draft.at,
            baseCoin = draft.baseCoin.cgId,
            quoteCoin = draft.quoteCoin.cgId,
            side = draft.side,
            price = draft.price,
            quantity = draft.quantity,
            fee = draft.fee,
            feeCoin = draft.feeCoin.cgId,
            legFiat = draft.quantity.multiply(draft.price),
            feeFiat = draft.fee,
            source = draft.source,
            seq = draft.seq,
            estimated = true,
        )
    }

    // ---- 费率自动计算（T7.2） ----

    @Suppress("ReturnCount") // 空费率/无行情价/无规则三段早退
    override suspend fun feeQuote(request: FeeQuoteRequest): FeeQuoteResult? {
        val session = sessions.requireActive()
        val rules = feeRules.list(session.account.id)
        val exchange = request.exchange.trim().uppercase()
        val rate = FeeRateResolver(rules).ratePercentFor(exchange, request.side) ?: return null
        val baseFiat = baseFiat()
        val quote = resolveQuoteLeg(exchange, request.quoteSymbol)
        val base = resolveManualSymbol(exchange, request.baseSymbol)
        val feeCoin = resolveFeeCoin(exchange, base, request.quoteSymbol, quote, request.feeCoinSymbol)
        val role = FeeCalculator.roleOf(base.cgId, quote.cgId, feeCoin.cgId)
        val prices = when (role) {
            FeeCoinRole.QUOTE -> ConversionPx(quoteFiat = eventBuilder.currentPrice(quote.id, baseFiat))
            FeeCoinRole.BASE -> ConversionPx(feeFiat = eventBuilder.currentPrice(feeCoin.id, baseFiat))
            FeeCoinRole.THIRD -> ConversionPx(
                quoteFiat = eventBuilder.currentPrice(quote.id, baseFiat),
                feeFiat = eventBuilder.currentPrice(feeCoin.id, baseFiat),
            )
        }
        val fee = FeeCalculator.feeFor(rate, request.quantity, request.price, role, prices) ?: return null
        return FeeQuoteResult(ratePercent = rate, coinQty = fee.coinQty)
    }

    // ---- CSV 导入（T7.3） ----

    @Suppress("LongMethod") // 预览组装（摘要/计数/歧义/影响概览）一段式编排，拆分反损可读性
    override suspend fun parseCsv(bytes: ByteArray): CsvPreview {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val baseFiat = baseFiat()
        val output = parser.parse(bytes)
        val parsed = output.rows

        var newRows = 0
        var duplicates = 0
        var unresolved = 0
        val annotated = ArrayList<CsvPreviewRow>(parsed.size)
        for (t in parsed) {
            val status = when {
                t.baseCoin == null || t.quoteCoin == null || t.baseCoin.cgId == t.quoteCoin.cgId -> {
                    unresolved++
                    CsvRowStatus.UNRESOLVED
                }
                isDuplicate(accountId, t) -> {
                    duplicates++
                    CsvRowStatus.DUPLICATE
                }
                else -> {
                    newRows++
                    CsvRowStatus.IMPORT
                }
            }
            annotated += CsvPreviewRow(
                rowKey = t.rowKey,
                time = t.time,
                pair = t.pair,
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                status = status,
                dedupReason = if (status == CsvRowStatus.DUPLICATE) {
                    if (t.orderId != null) DEDUP_EXACT else DEDUP_FUZZY
                } else {
                    null
                },
                include = status == CsvRowStatus.IMPORT,
            )
        }

        val impact = impactSummary(accountId, parsed, baseFiat)
        val sessionId = UUID.randomUUID().toString()
        csvSessions[sessionId] = CsvSession(baseFiat, output, Instant.now())
        trimSessions()
        return CsvPreview(
            sessionId = sessionId,
            totalRows = parsed.size,
            newRows = newRows,
            duplicateRows = duplicates,
            unresolvedRows = unresolved,
            errorRows = output.errors,
            rows = annotated.sortedByDescending { it.time }.take(CsvPreview.MAX_PREVIEW_ROWS),
            ambiguous = output.ambiguous.map { (key, candidates) ->
                val parts = key.split("|", limit = 2)
                AmbiguousTicker(
                    exchange = parts.getOrElse(0) { "" },
                    asset = parts.getOrElse(1) { "" },
                    candidates = candidates,
                )
            }.sortedBy { it.asset },
            affectedCoins = impact.first,
            anomalousCoins = impact.second,
        )
    }

    @Suppress("CyclomaticComplexMethod") // 逐行状态机（歧义重解/去重复核/未解析计数）固有
    override suspend fun confirmCsvImport(
        sessionId: String,
        ambiguityChoices: Map<String, String>,
        includeRowKeys: Set<String>,
    ): CsvImportSummary {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val csv = csvSessions.remove(sessionId)
            ?: error("导入会话已过期，请重新选择文件解析")
        // 歧义选择固化（source=MANUAL，一次性决策后续自动复用——共享规范 §6 规则④）
        for ((key, cgId) in ambiguityChoices) {
            val parts = key.split("|", limit = 2)
            if (parts.size == 2) {
                // 选择值 = 候选 cg_id -> coins.id（固化映射需要行主键）
                runCatching { catalog.getByCgId(cgId)?.let { coin ->
                    catalog.freezeMapping(parts[0], parts[1], coin.id)
                } }
            }
        }
        // 未解析行重解（固化后的映射立即生效）
        val rows = csv.output.rows.map { t ->
            if (t.baseCoin != null && t.quoteCoin != null) {
                t
            } else {
                val quote = t.quoteCoin ?: resolveLeg(t.exchange, t.quoteAsset, null)
                val base = t.baseCoin ?: resolveLeg(t.exchange, t.baseAsset, quote)
                t.copy(quoteCoin = quote, baseCoin = base)
            }
        }
        var imported = 0
        var duplicatesSkipped = 0
        var unresolvedSkipped = 0
        for (t in rows) {
            val base = t.baseCoin
            val quote = t.quoteCoin
            if (base == null || quote == null || base.cgId == quote.cgId) {
                unresolvedSkipped++
                continue
            }
            val duplicate = isDuplicate(accountId, t)
            when {
                duplicate && t.rowKey !in includeRowKeys -> duplicatesSkipped++
                duplicate && t.rowKey in includeRowKeys ->
                    if (insertCsvRow(accountId, t, base, quote)) imported++ else duplicatesSkipped++
                else -> if (insertCsvRow(accountId, t, base, quote)) imported++ else duplicatesSkipped++
            }
        }
        // 导入后全量重放（LENIENT）：负持仓币种 = 「持仓异常」清单（PRD 导入路径例外）
        val build = eventBuilder.build(repository.listAll(accountId), csv.baseFiat)
        val outcome = ReplayEngine.replay(build.events, NegativePolicy.LENIENT)
        val anomalous = outcome.anomalousCoins.mapNotNull { catalog.getByCgId(it)?.symbol }.distinct().sorted()
        return CsvImportSummary(imported, duplicatesSkipped, unresolvedSkipped, anomalous)
    }

    override suspend fun searchCoins(
        query: String,
        limit: Int,
    ): List<com.wuzhufolio.domain.catalog.CatalogCoin> = catalog.search(query, limit)

    override fun csvTemplateCsv(): String = CSV_TEMPLATE

    // ---- 内部 ----

    private suspend fun isDuplicate(accountId: Int, t: CsvTradeParser.ParsedTrade): Boolean =
        if (t.orderId != null) {
            repository.existsExact(accountId, t.exchange, t.orderId)
        } else {
            repository.findFuzzy(
                accountId = accountId,
                exchange = t.exchange,
                time = t.time,
                pair = t.pair,
                type = t.side.name,
                quantity = t.quantity,
                price = t.price,
            ) != null
        }

    private suspend fun insertCsvRow(
        accountId: Int,
        t: CsvTradeParser.ParsedTrade,
        base: CatalogCoin,
        quote: CatalogCoin,
    ): Boolean {
        // 确认前复核（预览与确认之间同步可能已写入同键行）
        if (isDuplicate(accountId, t)) return false
        repository.insert(
            NewLedgerTxRow(
                accountId = accountId,
                exchange = t.exchange,
                exchangeOrderId = t.orderId,
                pair = t.pair,
                baseCoinId = base.id.toInt(),
                quoteCoinId = quote.id.toInt(),
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                fee = t.fee,
                feeCurrency = t.feeCurrency,
                time = t.time,
                notes = t.notes,
                source = TransactionEventBuilder.SOURCE_CSV,
                priceStatus = PRICE_OK,
            ),
        )
        return true
    }

    /** 影响摘要：涉及币种持仓变化概览 + 导入后负持仓预告（预览口径 = 全部可解析行）。 */
    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    // 影响摘要（既有持仓 + 导入草稿重放 -> 差额/异常）——紧凑循环，分支固有
    private suspend fun impactSummary(
        accountId: Int,
        parsed: List<CsvTradeParser.ParsedTrade>,
        baseFiat: String,
    ): Pair<List<CoinPositionDelta>, List<String>> {
        val resolvable = parsed.filter { it.baseCoin != null && it.quoteCoin != null }
        if (resolvable.isEmpty()) return Pair(emptyList(), emptyList())
        val rows = rowSource.load(accountId)
        val preEvents = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        var seq = assembler.nextSeq(rows.tx, rows.funds, rows.recons) - 1
        val drafts = ArrayList<TradeEvent>()
        for (t in resolvable) {
            seq += 1
            val base = t.baseCoin ?: continue
            val quote = t.quoteCoin ?: continue
            val feeCoin = eventBuilder.resolveFeeCoinSymbol(base, quote, t.feeCurrency)
            val draft = TradeDraft(
                id = "csv-" + t.rowKey,
                at = t.time,
                seq = seq,
                baseCoin = base,
                quoteCoin = quote,
                side = t.side,
                price = t.price,
                quantity = t.quantity,
                fee = t.fee,
                feeCoin = feeCoin,
                source = RecordSource.Csv,
            )
            // 折算价缺失 -> 名义折算参与影响概览（与 build() 同口径）
            drafts += eventBuilder.buildEvent(draft, baseFiat) ?: eventBuilder.nominalEvent(draft)
        }
        val pre = ReplayEngine.replay(preEvents, NegativePolicy.LENIENT).holdings
        val post = ReplayEngine.replay(preEvents + drafts, NegativePolicy.LENIENT)
        val deltas = LinkedHashMap<String, BigDecimal>()
        for ((cgId, holding) in post.holdings) {
            val before = pre[cgId]?.quantity ?: BigDecimal.ZERO
            val delta = holding.quantity - before
            if (delta.signum() != 0) {
                val symbol = catalog.getByCgId(cgId)?.symbol ?: cgId
                deltas[symbol] = (deltas[symbol] ?: BigDecimal.ZERO) + delta
            }
        }
        val anomalous = post.anomalousCoins.mapNotNull { catalog.getByCgId(it)?.symbol }.distinct().sorted()
        return Pair(
            deltas.entries.sortedBy { it.key }.map { CoinPositionDelta(it.key, it.value) },
            anomalous,
        )
    }

    // ---- 币种解析（手动输入归一 + 消歧上浮） ----

    @Suppress("LongParameterList") // 已解析输入的 12 字段袋（解析产物，非逻辑组合）
    private class ResolvedInput(
        val exchange: String,
        val pair: String,
        val base: CatalogCoin,
        val quote: CatalogCoin,
        val feeCoin: CatalogCoin,
        val feeCurrency: String,
        val side: Side,
        val price: BigDecimal,
        val quantity: BigDecimal,
        val fee: BigDecimal,
        val time: Instant,
        val notes: String?,
    ) {
        fun draft(id: String, seq: Long): TradeDraft = TradeDraft(
            id = id,
            at = time,
            seq = seq,
            baseCoin = base,
            quoteCoin = quote,
            side = side,
            price = price,
            quantity = quantity,
            fee = fee,
            feeCoin = feeCoin,
            source = RecordSource.Manual,
        )
    }

    private suspend fun resolveInput(input: TransactionInput): ResolvedInput {
        val exchange = input.exchange.trim().uppercase()
        val base = resolveManualSymbol(exchange, input.baseSymbol)
        val quote = resolveQuoteLeg(exchange, input.quoteSymbol)
        val feeCoin = resolveFeeCoin(exchange, base, input.quoteSymbol, quote, input.feeCoinSymbol)
        return ResolvedInput(
            exchange = exchange,
            pair = input.baseSymbol.trim().uppercase() + "/" + input.quoteSymbol.trim().uppercase(),
            base = base,
            quote = quote,
            feeCoin = feeCoin,
            feeCurrency = feeCoin.symbol.uppercase(),
            side = input.side,
            price = input.price,
            quantity = input.quantity,
            fee = input.fee,
            time = input.time,
            notes = input.notes,
        )
    }

    /** 单腿解析（确认阶段重解；歧义/未收录 -> null 计未解析跳过）。 */
    private suspend fun resolveLeg(exchange: String, asset: String, quote: CatalogCoin?): CatalogCoin? =
        when (val res = catalog.resolve(exchange, asset, ResolveContext(quoteCoinId = quote?.id))) {
            is Resolution.Unique -> res.coin
            else -> null
        }

    /** 手动符号解析：目录精确唯一 -> 命中；否则走四级消歧（含映射 AUTO 冻结，与同步同口径）。 */
    private suspend fun resolveManualSymbol(exchange: String, symbol: String): CatalogCoin {
        val norm = symbol.trim().uppercase()
        val exact = catalog.getBySymbol(norm).filter { it.status == CoinStatus.ACTIVE }
        if (exact.size == 1) return exact.first()
        return when (val res = catalog.resolve(exchange, norm)) {
            is Resolution.Unique -> res.coin
            is Resolution.Ambiguous -> throw CoinResolutionException(
                norm,
                "歧义（同名资产 " + res.candidates.size + " 个，请从候选选择或更新币种目录）",
            )
            Resolution.NotFound -> throw CoinResolutionException(norm, "未收录（币种目录无此资产，请更新目录或检查拼写）")
        }
    }

    /** 计价腿：法币 -> 孪生稳定币（黄金用例 12）；无映射法币 -> MVP 不支持（登记模块记录 §5）。 */
    private suspend fun resolveQuoteLeg(exchange: String, symbol: String): CatalogCoin =
        when (val leg = catalog.fiatQuoteLeg(symbol.trim())) {
            is FiatLeg.TwinStable -> leg.stableCoin
            is FiatLeg.ThirdCurrency -> throw CoinResolutionException(
                symbol.trim().uppercase(),
                "法币计价无同币种稳定币映射（当前版本请使用稳定币交易对记录）",
            )
            null -> resolveManualSymbol(exchange, symbol)
        }

    /** 手续费币种：与基础/计价同符号 -> 对应腿；法币输入随计价腿归一；否则目录解析（V3 提示）。 */
    @Suppress("ReturnCount") // 基础/计价/目录三态解析早退
    private suspend fun resolveFeeCoin(
        exchange: String,
        base: CatalogCoin,
        quoteInput: String,
        quote: CatalogCoin,
        feeSymbol: String,
    ): CatalogCoin {
        val norm = feeSymbol.trim().uppercase()
        if (norm == base.symbol.uppercase()) return base
        if (norm == quote.symbol.uppercase() || norm == quoteInput.trim().uppercase()) return quote
        return resolveManualSymbol(exchange, norm)
    }

    private fun validateShape(input: TransactionInput) {
        require(input.exchange.trim().isNotEmpty()) { "交易所不能为空" }
        require(input.baseSymbol.trim().isNotEmpty()) { "交易对不能为空（V4）" }
        require(input.quoteSymbol.trim().isNotEmpty()) { "交易对不能为空（V4）" }
        require(input.price.signum() > 0) { "价格必须大于 0（V1）" }
        require(input.quantity.signum() > 0) { "数量必须大于 0（V1）" }
        require(input.fee.signum() >= 0) { "手续费必须 ≥ 0（V2）" }
        require(input.feeCoinSymbol.trim().isNotEmpty()) { "手续费币种不能为空（V3）" }
    }

    private fun baseFiat(): String = settings.getGlobal(SETTING_FIAT)?.takeIf { it.isNotBlank() } ?: "USD"

    private fun ActiveSessionStore.requireActive(): ActiveSession =
        get() ?: error("未登录：请先登录账户")

    private fun trimSessions() {
        while (csvSessions.size > MAX_CSV_SESSIONS) {
            csvSessions.entries.minByOrNull { it.value.createdAt }?.let { csvSessions.remove(it.key) } ?: break
        }
    }

    private class CsvSession(
        val baseFiat: String,
        val output: CsvTradeParser.ParseOutput,
        val createdAt: Instant,
    )

    companion object {
        const val SETTING_FIAT = "fiat"
        const val PRICE_OK = "OK"
        const val PRICE_PENDING = "PENDING"
        const val DEDUP_EXACT = "精确：交易所 + 订单号"
        const val DEDUP_FUZZY = "模糊：时间 + 交易对 + 类型 + 数量 + 价格"
        const val MAX_CSV_SESSIONS = 8

        /** 标准 CSV 模板（T7.3：模板下载；# 注释行解析器跳过；示例行备注明确提示删除）。 */
        val CSV_TEMPLATE = listOf(
            "# WuZhuFolio 交易导入模板（字段说明见 # 行；# 开头的行与空行会被忽略）",
            "# pair: 交易对 BASE/QUOTE（如 BTC/USDT；法币计价按 1:1 归一为同币种稳定币）",
            "# side: 买入/卖出（或 BUY/SELL）；price/quantity: 正数；fee/fee_currency: 手续费数量与币种（可选）",
            "# time: 交易时间，一律按 UTC 解析（ISO-8601 或 yyyy-MM-dd HH:mm:ss）；order_id: 交易所订单号（可选，精确去重）",
            "exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes",
            "BINANCE,,BTC/USDT,买入,50000,0.2,0.1,USDT,2025-01-10 08:00:00,示例行——导入前请删除",
            "BINANCE,,ETH/USDT,买入,3000,2,0.6,USDT,2025-02-15 12:30:00,示例行——导入前请删除",
            "BINANCE,,BTC/USDT,卖出,60000,0.1,0.06,USDT,2025-06-01 09:00:00,示例行——导入前请删除",
        ).joinToString("\n") + "\n"
    }
}
