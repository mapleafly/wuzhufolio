package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.exchange.ImportedTradeRow
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ApiKeySyncResult
import com.wuzhufolio.domain.exchange.CredentialValidation
import com.wuzhufolio.domain.exchange.CredentialValidationFailed
import com.wuzhufolio.domain.exchange.ExchangeAdapter
import com.wuzhufolio.domain.exchange.ExchangeApiException
import com.wuzhufolio.domain.exchange.ExchangeCadence
import com.wuzhufolio.domain.exchange.ExchangeCredentials
import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.domain.exchange.ExchangeLimits
import com.wuzhufolio.domain.exchange.ExchangeSyncPolicy
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.PairInfo
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.domain.exchange.ExchangeTrade
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.redaction.LogRedactor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * 交易所同步用例实现（T6.2/T6.3 · 契约 = domain.exchange.ExchangeSyncService）。
 *
 * 编排（单飞 Mutex 防并发；每 key 一轮独立完成）：
 * 1. 凭证解密（账户 DEK + FieldCipher，仅调用瞬间驻内存）→ BinanceAdapter 实例；
 * 2. fetchBalances（GET /account 亦做 B2 校验）→ 非零资产 → 余额推导候选；
 * 3. fetchPairs（exchangeInfo 注册表）→ 候选 symbol 收敛（余额推导 ∪ 已同步 pair，ADR-004 §3.1）
 *    → 预算分批（≤120 次 myTrades 调用，超额 queue 下轮并在 sync_logs 注明）；
 * 4. 逐 symbol 增量拉取（sinceId = 已同步最大成交 id）→ 逐笔 pair 切分 + CoinCatalog 币解析冻结（AUTO）
 *    → transactions 去重写本地账本（部分唯一索引 + 先查后写）；
 * 5. 写 sync_logs（脱敏 message）+ 更新 api_keys.last_sync_time/status（OK/FAILED）。
 *
 * 币解析未命中（NotFound/Ambiguous/未收录）→ 该笔跳过并计数（unresolvedSkipped，message 注明；目录更新/CSV 补录）。
 * 触发宿主（启动/定时 15/30/60 默认 30）随 M11 桌面集成（M5 调度宿主拆分裁决同口径）。
 */
@Suppress("TooManyFunctions", "LongParameterList", "SwallowedException")
class DefaultExchangeSyncService(
    private val sessions: ActiveSessionStore,
    private val crypto: CryptoService,
    private val apiKeyRepository: ApiKeyRepository,
    private val syncLogRepository: SyncLogRepository,
    private val transactionsRepository: ExchangeTransactionRepository,
    private val catalog: CoinCatalog,
    private val settings: SettingsRepository,
    private val adapterFactory: (ExchangeCredentials) -> ExchangeAdapter,
    private val logger: Logger = LoggerFactory.getLogger(DefaultExchangeSyncService::class.java),
) : ExchangeSyncService {

    private val syncMutex = Mutex()

    override suspend fun listKeys(): List<ApiKeyInfo> {
        val session = sessions.requireActive()
        return apiKeyRepository.list(session.account.id).map { it.toInfo() }
    }

    override suspend fun addAndSync(input: ApiKeyInput): ApiKeySyncResult {
        validate(input)
        val session = sessions.requireActive()
        val accountId = session.account.id
        val record = apiKeyRepository.create(accountId, input.name.trim(), input.exchangeName.trim()) { rowId ->
            val id = accountId.toString()
            ApiKeyCiphers(
                apiKey = crypto.encryptField(input.apiKey, session.dek, id, rowId.toString(), "api_key"),
                secretKey = crypto.encryptField(input.secretKey, session.dek, id, rowId.toString(), "secret_key"),
            )
        }
        // PRD 流程图 3：保存后立即执行首次同步
        return syncNow(record.id.toLong()).singleOrNull()
            ?: error("first sync did not return a result")
    }

    override suspend fun removeKey(apiKeyId: Long) {
        val session = sessions.requireActive()
        apiKeyRepository.remove(session.account.id, apiKeyId.toInt())
    }

    override suspend fun testCredentials(input: ApiKeyInput): CredentialValidation {
        val adapter = adapterFactory(ExchangeCredentials(input.apiKey.trim(), input.secretKey.trim()))
        return try {
            adapter.validateCredentials()
            CredentialValidation.Ok
        } catch (e: ExchangeApiException) {
            CredentialValidation.Failed(e.kind)
        }
    }

    override suspend fun syncNow(apiKeyId: Long?): List<ApiKeySyncResult> = syncMutex.withLock {
        val session = sessions.requireActive()
        val accountId = session.account.id
        val records = if (apiKeyId == null) {
            apiKeyRepository.list(accountId)
        } else {
            listOfNotNull(apiKeyRepository.findById(accountId, apiKeyId.toInt()))
        }
        if (records.isEmpty()) return@withLock emptyList()
        records.map { syncOne(accountId, session.dek, it) }
    }

    override suspend fun recentSyncLogs(limit: Int): List<SyncLogRow> {
        val session = sessions.requireActive()
        return syncLogRepository.recent(session.account.id, limit)
    }

    override suspend fun syncIntervalMinutes(): Int {
        val raw = settings.getGlobal(SYNC_INTERVAL_KEY)
            ?: return ExchangeCadence.DEFAULT_MINUTES
        return raw.toIntOrNull()?.let { minutes ->
            ExchangeCadence.ALLOWED_MINUTES.firstOrNull { it == minutes }
        } ?: ExchangeCadence.DEFAULT_MINUTES
    }

    override suspend fun saveSyncIntervalMinutes(minutes: Int) {
        require(minutes in ExchangeCadence.ALLOWED_MINUTES) {
            "sync interval must be one of " + ExchangeCadence.ALLOWED_MINUTES
        }
        settings.putGlobal(SYNC_INTERVAL_KEY, minutes.toString())
    }

    // ---- 内部 ----

    private suspend fun validate(input: ApiKeyInput) {
        require(input.name.trim().isNotEmpty()) { "别名不能为空" }
        require(input.exchangeName.trim().equals("BINANCE", ignoreCase = true)) { "MVP 仅支持 Binance" }
        require(input.apiKey.trim().isNotEmpty()) { "API Key 不能为空" }
        require(input.secretKey.trim().isNotEmpty()) { "Secret Key 不能为空" }
        when (val result = testCredentials(input)) {
            is CredentialValidation.Ok -> Unit
            is CredentialValidation.Failed -> throw CredentialValidationFailed(result)
        }
    }

    @Suppress("LongMethod") // 单 key 编排步骤内聚（取凭证→预算→逐 symbol 落账→状态/日志），拆分反损可读性
    private suspend fun syncOne(accountId: Int, dek: ByteArray, record: ApiKeyRecord): ApiKeySyncResult {
        val at = Instant.now()
        val credentials = try {
            val key = crypto.decryptField(record.apiKeyCipher, dek, accountId.toString(),
                record.id.toString(), "api_key")
            val secret = crypto.decryptField(record.secretKeyCipher, dek, accountId.toString(),
                record.id.toString(), "secret_key")
            ExchangeCredentials(key, secret)
        } catch (e: Exception) {
            logger.warn("api key decrypt failed (key={})", record.id)
            val message = "同步失败：本地密钥解密异常，请重新保存 API 密钥"
            logFailure(accountId, record.id, message)
            return failureResult(record, ExchangeError.Internal("decrypt failed"), message, at)
        }
        val adapter = adapterFactory(credentials)

        return try {
            val balances = adapter.fetchBalances()
            val pairs = adapter.fetchPairs()
            val syncedPairs = transactionsRepository.syncedPairs(accountId, adapter.exchangeName)
            val syncedIds = syncedPairs.associate { it.pair.replace("/", "") to it.maxOrderId }
            val balanceDerived = ExchangeSyncPolicy.balanceDerivedSymbols(balances, pairs)
            val syncedSymbols = ExchangeSyncPolicy.syncedSymbolsSet(
                syncedPairs.map { it.pair.replace("/", "") })
            val candidates = ExchangeSyncPolicy.candidateSymbols(balanceDerived, syncedSymbols)
            val plan = ExchangeSyncPolicy.plan(candidates, syncedIds)

            val registry = pairs.filter { it.status == "TRADING" }.associateBy { it.symbol }
            val tally = SyncTally()
            for (cursor in plan.cursors) {
                tally.addSymbolOutcome(syncSymbol(accountId, adapter, registry, cursor))
            }

            val partial = plan.queuedSymbols > 0
            val message = tally.toMessage(partial, plan.queuedSymbols)
            apiKeyRepository.updateSyncState(accountId, record.id, at, SyncStatus.OK.storageValue)
            syncLogRepository.append(accountId, record.id, SyncStatus.OK, tally.newTrades,
                LogRedactor.redact(message))
            ApiKeySyncResult(
                apiKeyId = record.id.toLong(),
                apiKeyName = record.name,
                status = SyncStatus.OK,
                newTrades = tally.newTrades,
                duplicatesSkipped = tally.duplicates,
                unresolvedSkipped = tally.unresolved,
                partial = partial,
                queuedSymbols = plan.queuedSymbols,
                error = null,
                message = message,
                at = at,
            )
        } catch (e: ExchangeApiException) {
            val message = ExchangeFailureCopy.failureMessage(e.kind)
            logFailure(accountId, record.id, message)
            apiKeyRepository.markFailed(accountId, record.id)
            failureResult(record, e.kind, message, at)
        } catch (e: Exception) {
            logger.warn("exchange sync failed unexpectedly (key={})", record.id, e)
            val message = "同步失败：内部错误（详情见日志）"
            logFailure(accountId, record.id, message)
            apiKeyRepository.markFailed(accountId, record.id)
            failureResult(record, ExchangeError.Internal(e.message ?: ""), message, at)
        }
    }

    /** 单 symbol 一轮：拉取增量成交 → 逐笔映射落账；返回新增/去重/未解析计数。 */
    private suspend fun syncSymbol(
        accountId: Int,
        adapter: ExchangeAdapter,
        registry: Map<String, PairInfo>,
        cursor: com.wuzhufolio.domain.exchange.SymbolCursor,
    ): SyncTally {
        val tally = SyncTally()
        val pairInfo = registry[cursor.symbol]
        if (pairInfo == null) {
            tally.unresolved++ // 注册表已无此 symbol（下架）→ 计未解析，下轮不再枚举
            return tally
        }
        val trades = fetchSafely(adapter, cursor, tally)
        for (trade in trades) {
            val row = resolveRow(accountId, adapter.exchangeName, pairInfo, trade)
            when {
                row == null -> tally.unresolved++
                transactionsRepository.insertIfAbsent(row) -> tally.newTrades++
                else -> tally.duplicates++
            }
        }
        return tally
    }

    /** 单 symbol 拉取（交易所失败计 unresolved 后返回空——不中断整轮，幂等保留下轮续传）。 */
    private suspend fun fetchSafely(
        adapter: ExchangeAdapter,
        cursor: com.wuzhufolio.domain.exchange.SymbolCursor,
        tally: SyncTally,
    ): List<ExchangeTrade> = try {
        adapter.fetchTrades(cursor.symbol, cursor.sinceId, ExchangeLimits.TRADES_PAGE_LIMIT)
    } catch (e: ExchangeApiException) {
        tally.unresolved++
        emptyList()
    }

    /** pair 注册表 + 成交 → 账本行（base/quote 币解析冻结；失败返回 null = 计入 unresolved）。 */
    @Suppress("ReturnCount")
    private suspend fun resolveRow(
        accountId: Int,
        exchange: String,
        pairInfo: PairInfo,
        trade: ExchangeTrade,
    ): ImportedTradeRow? {
        val base = catalog.resolve(exchange, pairInfo.baseAsset) as? Resolution.Unique ?: return null
        val quote = catalog.resolve(exchange, pairInfo.quoteAsset) as? Resolution.Unique ?: return null
        if (base.coin.id == quote.coin.id) return null // 退化对
        return ImportedTradeRow(
            accountId = accountId,
            exchange = exchange,
            exchangeOrderId = trade.id.toString(),
            pair = pairInfo.baseAsset + "/" + pairInfo.quoteAsset,
            baseCoinId = base.coin.id.toInt(),
            quoteCoinId = quote.coin.id.toInt(),
            type = trade.side.storageValue,
            price = trade.price,
            quantity = trade.qty,
            fee = trade.fee,
            feeCurrency = trade.feeAsset.ifEmpty { pairInfo.quoteAsset },
            transactionTime = trade.time,
        )
    }

    private fun logFailure(accountId: Int, apiKeyId: Int, message: String) {
        syncLogRepository.append(accountId, apiKeyId, SyncStatus.FAILED, 0,
            LogRedactor.redact(message))
    }

    private fun failureResult(
        record: ApiKeyRecord,
        error: ExchangeError,
        message: String,
        at: Instant,
    ): ApiKeySyncResult = ApiKeySyncResult(
        apiKeyId = record.id.toLong(),
        apiKeyName = record.name,
        status = SyncStatus.FAILED,
        newTrades = 0,
        duplicatesSkipped = 0,
        unresolvedSkipped = 0,
        partial = false,
        queuedSymbols = 0,
        error = error,
        message = message,
        at = at,
    )

    private fun ActiveSessionStore.requireActive(): com.wuzhufolio.data.accounts.ActiveSession =
        get() ?: error("未登录：请在账户会话中管理交易所 API")

    private object ExchangeFailureCopy {
        fun failureMessage(kind: ExchangeError): String = when (kind) {
            ExchangeError.InvalidKey,
            ExchangeError.SignatureInvalid -> "Binance API 密钥已失效，请检查或更新"
            ExchangeError.RateLimited -> "Binance 限流触发，同步已停止，请稍后重试"
            ExchangeError.TimestampSkew -> "Binance 时间偏差，同步已停止，请稍后重试"
            ExchangeError.Network -> "网络不可达 Binance，同步失败（本地账本已保留）"
            is ExchangeError.Http -> "Binance 请求失败（HTTP " + kind.code + "）"
            is ExchangeError.Internal -> "同步失败：内部错误（详情见日志）"
        }
    }

    companion object {
        const val SYNC_INTERVAL_KEY = "sync.interval_minutes"
    }
}

/** 单 symbol/单 key 同步计数聚合。 */
private class SyncTally {
    var newTrades: Int = 0
    var duplicates: Int = 0
    var unresolved: Int = 0

    fun addSymbolOutcome(other: SyncTally) {
        newTrades += other.newTrades
        duplicates += other.duplicates
        unresolved += other.unresolved
    }

    fun toMessage(partial: Boolean, queued: Int): String {
        val sb = StringBuilder()
        sb.append("同步成功 · 新增 ").append(newTrades)
        if (duplicates > 0) sb.append(" · 去重跳过 ").append(duplicates)
        if (unresolved > 0) sb.append(" · 未解析跳过 ").append(unresolved)
        if (partial) sb.append(" · 部分同步（").append(queued).append(" 个交易对下轮续传）")
        return sb.toString()
    }
}
