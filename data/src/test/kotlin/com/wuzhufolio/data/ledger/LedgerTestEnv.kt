package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.SnapshotWrite
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * M7/M8 数据层测试环境：临时 SQLCipher 库（schema 12）+ 会话注入 + 种子币目录 + 快照/费率仓库 +
 * 资金/校准服务。种子目录含稳定币孪生对（tether/usd-coin/dai），事件构造 USD 锚定 1:1 可解析。
 */
internal class LedgerTestEnv : AutoCloseable {

    val db: WzDatabase = WzDatabase(
        Files.createTempDirectory("wuzhufolio-ledger").resolve("ledger.db"),
        randomDbKey(),
    )
    val gate: DbGate = DbGate(db)
    val settings: SettingsRepository = SettingsRepository(gate)
    val accounts: AccountRepository = AccountRepository(gate)
    val sessions: ActiveSessionStore = ActiveSessionStore()
    val catalog: SqlCoinCatalog = SqlCoinCatalog(gate)
    val snapshots: PriceSnapshotRepository = PriceSnapshotRepository(gate)
    val repository: LedgerTransactionRepository = LedgerTransactionRepository(gate)
    val funds: FundFlowRepository = FundFlowRepository(gate)
    val recons: ReconciliationRepository = ReconciliationRepository(gate)
    val feeRules: FeeRuleRepository = FeeRuleRepository(gate)
    val parser: CsvTradeParser = CsvTradeParser(catalog)
    val eventBuilder: TransactionEventBuilder = TransactionEventBuilder(catalog, snapshots)
    val assembler: LedgerEventAssembler = LedgerEventAssembler(catalog, eventBuilder)
    val service: DefaultTransactionLedgerService = DefaultTransactionLedgerService(
        sessions = sessions,
        repository = repository,
        fundsRepository = funds,
        reconsRepository = recons,
        catalog = catalog,
        settings = settings,
        feeRules = feeRules,
        parser = parser,
        eventBuilder = eventBuilder,
        assembler = assembler,
    )
    val fundService: DefaultFundService = DefaultFundService(
        sessions = sessions,
        transactions = repository,
        funds = funds,
        recons = recons,
        catalog = catalog,
        settings = settings,
        eventBuilder = eventBuilder,
        assembler = assembler,
    )

    // ---- M8 校准测试依赖（Fake 适配器 + 真实字段级加解密，与同步链路同口径） ----

    /** Fake 适配器返回的余额（校准取数输入）。 */
    var fakeBalances: List<com.wuzhufolio.domain.exchange.Balance> = emptyList()

    /** Fake 适配器抛出的错误（fetchBalance 失败路径）。 */
    var fakeBalanceError: com.wuzhufolio.domain.exchange.ExchangeError? = null

    private val fakeAdapter = object : com.wuzhufolio.domain.exchange.ExchangeAdapter {
        override val exchangeName: String = "BINANCE"

        override suspend fun validateCredentials() = Unit

        override suspend fun fetchBalances(): List<com.wuzhufolio.domain.exchange.Balance> {
            fakeBalanceError?.let { throw com.wuzhufolio.domain.exchange.ExchangeApiException(it) }
            return fakeBalances
        }

        override suspend fun fetchTrades(
            symbol: String,
            sinceId: Long?,
            limit: Int,
        ): List<com.wuzhufolio.domain.exchange.ExchangeTrade> = emptyList()

        override suspend fun fetchPairs(): List<com.wuzhufolio.domain.exchange.PairInfo> = emptyList()
    }

    val calibrationService: DefaultCalibrationService = DefaultCalibrationService(
        sessions = sessions,
        catalog = catalog,
        settings = settings,
        transactions = repository,
        funds = funds,
        recons = recons,
        assembler = assembler,
        eventBuilder = eventBuilder,
        apiKeyRepository = com.wuzhufolio.data.exchange.ApiKeyRepository(gate),
        crypto = com.wuzhufolio.domain.security.CryptoService(),
        adapterFactory = { fakeAdapter },
        syncLogs = com.wuzhufolio.data.exchange.SyncLogRepository(gate),
    )

    /** 建一个测试 API 密钥行（真实字段级加密，AAD 与同步链路同口径）。 */
    fun addKey(name: String = "test-key", exchange: String = "BINANCE") {
        val session = sessions.get() ?: error("login first")
        val repo = com.wuzhufolio.data.exchange.ApiKeyRepository(gate)
        val crypto = com.wuzhufolio.domain.security.CryptoService()
        repo.create(session.account.id, name, exchange) { rowId ->
            com.wuzhufolio.data.exchange.ApiKeyCiphers(
                apiKey = crypto.encryptField(
                    "k-$name", session.dek, session.account.id.toString(), rowId.toString(), "api_key",
                ),
                secretKey = crypto.encryptField(
                    "s-$name", session.dek, session.account.id.toString(), rowId.toString(), "secret_key",
                ),
            )
        }
    }

    /** 直插一条交易所来源交易行（构造「单一数据来源」币种；绕过同步编排）。 */
    @Suppress("LongParameterList") // 交易所行 7 字段直插（测试夹具）
    suspend fun seedExchangeTrade(
        symbol: String,
        side: com.wuzhufolio.domain.engine.Side,
        price: String,
        quantity: String,
        at: Instant,
        exchange: String = "BINANCE",
    ): Long {
        val session = sessions.get() ?: error("login first")
        val coin = coin(symbol) ?: error("coin $symbol not seeded")
        val stable = coin("USDT") ?: error("USDT not seeded")
        return repository.insert(
            NewLedgerTxRow(
                accountId = session.account.id,
                exchange = exchange,
                exchangeOrderId = null,
                pair = symbol + "/USDT",
                baseCoinId = coin.id.toInt(),
                quoteCoinId = stable.id.toInt(),
                side = side,
                price = BigDecimal(price),
                quantity = BigDecimal(quantity),
                fee = BigDecimal.ZERO,
                feeCurrency = "USDT",
                time = at,
                notes = null,
                source = TransactionEventBuilder.SOURCE_BINANCE,
                priceStatus = "OK",
            ),
        )
    }

    init {
        db.migrateToLatest()
        runBlocking { catalog.refreshDirectory(seedDirectory()) }
    }

    /** 造一个账户并把会话置为激活（DEK = 测试固定 32B）。 */
    fun login(username: String = "tester"): ActiveSession {
        val account = runBlocking {
            accounts.createWrapped(username, "hash", "00".repeat(16), "p") { id -> "wrapped-" + id }
        }
        val session = ActiveSession(AccountSummary(account.id, username), dek())
        sessions.set(session)
        return session
    }

    /** 目录中取币（symbol 大小写不敏感）。 */
    suspend fun coin(symbol: String): CatalogCoin? =
        catalog.getBySymbol(symbol.uppercase()).firstOrNull()

    /** 写入一条价格快照（记录时折算输入）。 */
    suspend fun putSnapshot(coinId: Long, fiat: String, price: String, at: Instant) {
        snapshots.upsert(
            SnapshotWrite(
                coinId = coinId.toInt(),
                fiat = fiat,
                price = BigDecimal(price),
                source = PriceSource.COINGECKO,
                at = at,
            ),
        )
    }

    override fun close() {
        db.close()
    }

    companion object {
        fun dek(): ByteArray = ByteArray(32) { (it + 1).toByte() }

        fun seedDirectory(): List<CoinDirectoryEntry> = listOf(
            CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
            CoinDirectoryEntry("ethereum", "eth", "Ethereum"),
            CoinDirectoryEntry("tether", "usdt", "Tether"),
            CoinDirectoryEntry("usd-coin", "usdc", "USD Coin"),
            CoinDirectoryEntry("dai", "dai", "Dai"),
            CoinDirectoryEntry("binancecoin", "bnb", "BNB"),
        )
    }
}
