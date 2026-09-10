package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.ReconciliationService
import com.wuzhufolio.domain.engine.ReplayEngine
import com.wuzhufolio.domain.engine.AnchorEvent
import com.wuzhufolio.domain.engine.CalibrationPlan
import com.wuzhufolio.domain.engine.MutationVerdict
import com.wuzhufolio.domain.engine.NegativePolicy
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.exchange.Balance
import com.wuzhufolio.domain.exchange.ExchangeAdapter
import com.wuzhufolio.domain.exchange.ExchangeApiException
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.ledger.CalibrationBlockedException
import com.wuzhufolio.domain.ledger.CalibrationPreparation
import com.wuzhufolio.domain.ledger.CalibrationResult
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.CoinResolutionKind
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.ReconciliationRow
import com.wuzhufolio.domain.security.CryptoService
import java.math.BigDecimal
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 持仓校准用例实现（M8 · T8.2 + M4 遗留「校准执行流」· 契约 = domain.ledger.CalibrationUseCase）。
 *
 * 执行链（PRD 全局说明「持仓校准规则」/ 故事 4.1-5）：
 * 1. 币种解析（coins 目录）→ 全量重放取本地持仓与**交易事件来源集合**（资金/锚点不计——M4 §5-6）；
 * 2. 单一来源门：NoRecords / Multi → [CalibrationBlockedException]（多来源提示原文落 UI）；
 * 3. 依据交易所只读密钥（api_keys 按交易所名匹配，取最近同步优先）→ fetchBalances 实时余额
 *  （币种资产符号经 exchange_coin_map 反向映射，缺映射按 symbol 兜底，缺余额行按 0 计）；
 * 4. 校准时市价（事件构造层现价链）不可得 → NO_MARKET_PRICE（「行情完全不可用时暂不允许执行」）；
 * 5. 差额规划 = ReconciliationService.plan（正→系统增资 / 负→系统撤资语义）；
 * 6. 执行 = 相对校验（锚点把持仓对齐到较小余额时其后的卖出可能转负 → V9 阻止，模块记录 M8 §5）
 *  → reconciliation_records 入库 → sync_logs 留痕（PRD 故事 4.1-5「在同步日志留痕」）。
 */
@Suppress("TooManyFunctions", "LongParameterList") // 校准链六步 + 依赖注入袋固有（与 M6 同步服务同型）
class DefaultCalibrationService(
    private val sessions: ActiveSessionStore,
    private val catalog: CoinCatalog,
    private val settings: SettingsRepository,
    private val transactions: LedgerTransactionRepository,
    private val funds: FundFlowRepository,
    private val recons: ReconciliationRepository,
    private val assembler: LedgerEventAssembler,
    private val eventBuilder: TransactionEventBuilder,
    private val apiKeyRepository: ApiKeyRepository,
    private val crypto: CryptoService,
    private val adapterFactory: (ExchangeCredentials) -> ExchangeAdapter,
    private val syncLogs: SyncLogRepository,
    private val logger: Logger = LoggerFactory.getLogger(DefaultCalibrationService::class.java),
) : CalibrationUseCase {

    private val reconciliation = ReconciliationService()

    override suspend fun prepare(coinSymbol: String, pickedCoinId: Long?): CalibrationPreparation {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val coin = resolveCoin(coinSymbol, pickedCoinId)
        val local = localPosition(accountId, coin)
        val exchangeName = requireSingleSource(local.sources)
        val key = pickKey(accountId, exchangeName)
            ?: throw blocked(
                CalibrationBlockedException.Reason.NO_EXCHANGE_KEY,
                "未找到 " + exchangeName + " 的 API 密钥，请先在「设置 → API 管理」添加只读密钥",
            )
        val exchangeQuantity = fetchBalance(coin, exchangeName, session.dek, key.id)
        val price = eventBuilder.currentPrice(coin.id, baseFiat())
        val plan = try {
            reconciliation.plan(coin.cgId, local.quantity, exchangeQuantity, price)
        } catch (e: com.wuzhufolio.domain.engine.CalibrationPriceUnavailableException) {
            logger.info("calibration blocked: price unavailable ({})", e.message)
            throw blocked(
                CalibrationBlockedException.Reason.NO_MARKET_PRICE,
                "行情不可用（无校准时市价），暂无法执行校准——请先在「设置」配置行情 Key 并刷新行情",
            )
        }
        return toPreparation(
            coin,
            exchangeName,
            key.name,
            key.id.toLong(),
            local.quantity,
            exchangeQuantity,
            price,
            plan,
        )
    }

    override suspend fun execute(coinSymbol: String, pickedCoinId: Long?): CalibrationResult {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val prep = prepare(coinSymbol, pickedCoinId) // 重新实时取数（余额/市价以执行时点为准）
        if (prep.delta.signum() == 0) {
            return CalibrationResult(recorded = false, row = null)
        }
        val coin = resolveCoin(coinSymbol, pickedCoinId)
        validateAnchorMutation(accountId, coin, prep)
        val rowId = recons.insert(
            NewReconciliationRow(
                accountId = accountId,
                symbol = coin.symbol.uppercase(),
                coinId = coin.id.toInt(),
                exchange = prep.exchangeName,
                localQuantity = prep.localQuantity,
                exchangeQuantity = prep.exchangeQuantity,
                delta = prep.delta,
                baseAmount = prep.deltaFiat,
            ),
        )
        syncLogs.append(
            accountId = accountId,
            apiKeyId = prep.apiKeyId.toInt(), // 留痕：校准依据密钥（PRD 故事 4.1-5「在同步日志留痕」）
            status = SyncStatus.OK,
            newTradesCount = 0,
            message = "持仓校准 " + coin.symbol.uppercase() + "：本地 " +
                prep.localQuantity.stripTrailingZeros().toPlainString() + " -> 交易所 " +
                prep.exchangeQuantity.stripTrailingZeros().toPlainString() + "（差额 " +
                prep.delta.stripTrailingZeros().toPlainString() + "，折算 " +
                prep.deltaFiat.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " " + baseFiat() + "）",
        )
        logger.info("calibration recorded coin={} delta={}", coin.cgId, prep.delta)
        val record = recons.listAll(accountId).firstOrNull { it.id == rowId }
            ?: error("calibration row missing after insert")
        return CalibrationResult(
            recorded = true,
            row = ReconciliationRow(
                id = record.id,
                uuid = record.uuid,
                symbol = record.symbol,
                exchange = record.exchange,
                localQuantity = record.localQuantity,
                exchangeQuantity = record.exchangeQuantity,
                delta = record.delta,
                baseAmount = record.baseAmount,
                createdAt = record.createdAt,
            ),
        )
    }

    override suspend fun history(coinSymbol: String, pickedCoinId: Long?): List<ReconciliationRow> {
        val session = sessions.requireActive()
        val coin = resolveCoin(coinSymbol, pickedCoinId)
        return recons.listForCoin(session.account.id, coin.id).map { row ->
            ReconciliationRow(
                id = row.id,
                uuid = row.uuid,
                symbol = row.symbol,
                exchange = row.exchange,
                localQuantity = row.localQuantity,
                exchangeQuantity = row.exchangeQuantity,
                delta = row.delta,
                baseAmount = row.baseAmount,
                createdAt = row.createdAt,
            )
        }
    }

    // ---- 内部 ----

    private data class LocalPosition(val quantity: BigDecimal, val sources: Set<RecordSource>)

    /** 全量重放（LENIENT——导入异常不阻塞校准入口）取本地持仓与交易事件来源。 */
    private suspend fun localPosition(accountId: Int, coin: CatalogCoin): LocalPosition {
        val baseFiat = baseFiat()
        val build = assembler.build(
            transactions.listAll(accountId),
            funds.listAll(accountId),
            recons.listAll(accountId),
            baseFiat,
        )
        val outcome = ReplayEngine.replay(build.events, NegativePolicy.LENIENT)
        val holding = outcome.holdings[coin.cgId]
        return LocalPosition(holding?.quantity ?: BigDecimal.ZERO, holding?.sources ?: emptySet())
    }

    /** 单一来源门（PRD：多来源时入口隐藏并提示——提示原文见全局说明「持仓校准规则」）。 */
    private fun requireSingleSource(sources: Set<RecordSource>): String =
        when (val classification = reconciliation.classifySources(sources)) {
            is com.wuzhufolio.domain.engine.SourceClassification.NoRecords -> throw blocked(
                CalibrationBlockedException.Reason.NO_RECORDS,
                "该币种暂无交易记录，无可校准依据",
            )
            is com.wuzhufolio.domain.engine.SourceClassification.SingleExchange -> classification.exchangeName
            is com.wuzhufolio.domain.engine.SourceClassification.Multi -> throw blocked(
                CalibrationBlockedException.Reason.MULTI_SOURCE,
                "该币种存在多来源记录（" + classification.sources.joinToString("、") + "），" +
                    "请先补齐或核对其他来源，或通过增资/撤资调整",
            )
        }

    /** 依据交易所密钥：交易所名匹配（大小写不敏感），最近同步优先、其次状态 OK、再次行序。 */
    private fun pickKey(accountId: Int, exchangeName: String) =
        apiKeyRepository.list(accountId)
            .filter { it.exchangeName.equals(exchangeName, ignoreCase = true) }
            .sortedWith(
                compareBy(
                    { it.status != "OK" },
                    { it.lastSyncTime == null },
                    { it.lastSyncTime ?: Instant.EPOCH },
                ),
            )
            .firstOrNull()

    /** 实时余额：密钥解密 -> fetchBalances 实时取数（映射表反向查资产符号，缺映射按 symbol 兜底）。 */
    private suspend fun fetchBalance(
        coin: CatalogCoin,
        exchangeName: String,
        dek: ByteArray,
        keyId: Int,
    ): BigDecimal {
        val credentials = resolveCredentials(keyId, dek)
            ?: throw blocked(
                CalibrationBlockedException.Reason.BALANCE_FETCH_FAILED,
                "API 密钥不存在、已删除或解密失败，请在「设置 → API 管理」检查密钥后重试",
            )
        val adapter = adapterFactory(credentials)
        val balances = try {
            adapter.fetchBalances()
        } catch (e: ExchangeApiException) {
            throw blocked(
                CalibrationBlockedException.Reason.BALANCE_FETCH_FAILED,
                balanceFailureCopy(e.kind, exchangeName),
                e,
            )
        }
        return locateBalance(balances, exchangeName, coin)
    }

    /** 凭证解密（行缺失/解密失败 = null；异常入诊断日志，UI 只见类型化文案）。 */
    private suspend fun resolveCredentials(keyId: Int, dek: ByteArray): ExchangeCredentials? {
        val record = apiKeyRepository.findById(sessions.requireActive().account.id, keyId)
            ?: return null
        return try {
            val apiKey = crypto.decryptField(
                record.apiKeyCipher,
                dek,
                record.accountId.toString(),
                record.id.toString(),
                "api_key",
            )
            val secret = crypto.decryptField(
                record.secretKeyCipher,
                dek,
                record.accountId.toString(),
                record.id.toString(),
                "secret_key",
            )
            ExchangeCredentials(apiKey, secret)
        } catch (e: Exception) {
            logger.warn("calibration key decrypt failed (key={})", record.id, e)
            null
        }
    }

    /** 余额定位：优先 exchange_coin_map 反向映射资产符号，缺映射按币种 symbol 兜底。 */
    private suspend fun locateBalance(
        balances: List<Balance>,
        exchangeName: String,
        coin: CatalogCoin,
    ): BigDecimal {
        val mappedAsset = catalog.exchangeAssetFor(exchangeName, coin.id)
        if (mappedAsset != null) {
            balances.firstOrNull { it.asset.equals(mappedAsset, ignoreCase = true) }?.let { return it.total }
        }
        return balances.firstOrNull { it.asset.equals(coin.symbol, ignoreCase = true) }?.total ?: BigDecimal.ZERO
    }

    /** 锚点入库前相对校验：锚点把持仓对齐到较小余额时，其后记录可能转负 → V9 阻止。 */
    private suspend fun validateAnchorMutation(accountId: Int, coin: CatalogCoin, prep: CalibrationPreparation) {
        val baseFiat = baseFiat()
        val rows = LedgerRowsSource(transactions, funds, recons).load(accountId)
        val without = assembler.build(rows.tx, rows.funds, rows.recons, baseFiat).events
        val candidate = AnchorEvent(
            id = "candidate-" + java.util.UUID.randomUUID(),
            at = Instant.now(),
            coin = coin.cgId,
            exchangeQuantity = prep.exchangeQuantity,
            delta = prep.delta,
            deltaFiat = prep.deltaFiat,
            seq = assembler.nextSeq(rows.tx, rows.funds, rows.recons),
        )
        when (val verdict = ReplayEngine.validateMutation(without + candidate, without)) {
            is MutationVerdict.Ok -> Unit
            is MutationVerdict.Blocked -> throw LedgerValidationException(
                code = LedgerErrorCode.REPLAY_CONFLICT,
                coinSymbol = coin.symbol,
                shortage = verdict.violation.shortage.abs(),
                conflictAt = verdict.violation.at,
            )
        }
    }

    private fun toPreparation(
        coin: CatalogCoin,
        exchangeName: String,
        apiKeyName: String,
        apiKeyId: Long,
        localQuantity: BigDecimal,
        exchangeQuantity: BigDecimal,
        marketPrice: BigDecimal?,
        plan: CalibrationPlan,
    ) = CalibrationPreparation(
        coinSymbol = coin.symbol,
        coinName = coin.name,
        exchangeName = exchangeName,
        apiKeyId = apiKeyId,
        apiKeyName = apiKeyName,
        localQuantity = localQuantity,
        exchangeQuantity = exchangeQuantity,
        delta = plan.delta,
        deltaFiat = plan.deltaFiat,
        marketPrice = marketPrice ?: BigDecimal.ZERO,
        direction = plan.direction,
    )

    @Suppress("ReturnCount", "ThrowsCount") // 点选直达 + 精确 + 消歧三段早退（与 DefaultFundService 同构）
    private suspend fun resolveCoin(symbol: String, pickedCoinId: Long? = null): CatalogCoin {
        val norm = symbol.trim().uppercase()
        // 候选点选直达（M8 修复轮 §8-1）：按行 id 取用并校验符号一致，绕过同名符号歧义
        if (pickedCoinId != null) {
            val picked = catalog.getById(pickedCoinId)
                ?: throw CoinResolutionException(
                    norm,
                    "所选币种不存在或已删除，请重新从候选列表选择",
                    CoinResolutionKind.CANDIDATE_GONE,
                )
            if (!picked.symbol.equals(norm, ignoreCase = true)) {
                throw CoinResolutionException(
                    norm,
                    "所选币种（" + picked.symbol + "）与输入符号不一致，请重新从候选列表选择",
                    CoinResolutionKind.CANDIDATE_GONE,
                )
            }
            return picked
        }
        val exact = catalog.getBySymbol(norm).filter { it.status == com.wuzhufolio.domain.catalog.CoinStatus.ACTIVE }
        if (exact.size == 1) return exact.first()
        return when (val res = catalog.resolve(null, norm)) {
            is com.wuzhufolio.domain.catalog.Resolution.Unique -> res.coin
            is com.wuzhufolio.domain.catalog.Resolution.Ambiguous -> throw CoinResolutionException(
                norm,
                "歧义（同名资产 " + res.candidates.size + " 个，请从候选选择）",
                CoinResolutionKind.AMBIGUOUS,
            )
            com.wuzhufolio.domain.catalog.Resolution.NotFound ->
                throw CoinResolutionException(
                    norm,
                    "未收录（币种目录无此资产，请更新目录或检查拼写）",
                    CoinResolutionKind.NOT_FOUND,
                )
        }
    }

    private fun blocked(
        reason: CalibrationBlockedException.Reason,
        message: String,
        cause: Throwable? = null,
    ) = CalibrationBlockedException(reason, message, cause)

    private fun balanceFailureCopy(error: ExchangeError, exchangeName: String): String = when (error) {
        ExchangeError.InvalidKey, ExchangeError.SignatureInvalid ->
            exchangeName + " API 密钥已失效，请检查或更新（B2）"
        ExchangeError.RateLimited -> exchangeName + " 限流触发，请稍后重试"
        ExchangeError.Network -> "网络不可达，无法获取交易所余额（保留本地数据）"
        ExchangeError.TimestampSkew -> exchangeName + " 时间戳偏差，请重试"
        is ExchangeError.Http -> exchangeName + " 余额获取失败（HTTP " + error.code + "）"
        is ExchangeError.Internal -> exchangeName + " 余额获取失败（内部错误，详情见日志）"
    }

    private fun baseFiat(): String =
        settings.getGlobal(DefaultTransactionLedgerService.SETTING_FIAT)?.takeIf { it.isNotBlank() } ?: "USD"

    private fun ActiveSessionStore.requireActive(): com.wuzhufolio.data.accounts.ActiveSession =
        get() ?: error("未登录：请先登录账户")
}

/** 三类记录联合读取（DefaultFundService/DefaultCalibrationService 共用的装配输入源）。 */
internal class LedgerRowsSource(
    private val transactions: LedgerTransactionRepository,
    private val funds: FundFlowRepository,
    private val recons: ReconciliationRepository,
) {
    data class Rows(
        val tx: List<LedgerTxRow>,
        val funds: List<FundFlowRow>,
        val recons: List<ReconciliationRecordRow>,
    )

    suspend fun load(accountId: Int): Rows = Rows(
        tx = transactions.listAll(accountId),
        funds = funds.listAll(accountId),
        recons = recons.listAll(accountId),
    )
}
