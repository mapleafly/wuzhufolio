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
 * M7 数据层测试环境：临时 SQLCipher 库（schema 10）+ 会话注入 + 种子币目录 + 快照/费率仓库。
 * 种子目录含稳定币孪生对（tether/usd-coin/dai），事件构造 USD 锚定 1:1 可解析。
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
    val feeRules: FeeRuleRepository = FeeRuleRepository(gate)
    val parser: CsvTradeParser = CsvTradeParser(catalog)
    val eventBuilder: TransactionEventBuilder = TransactionEventBuilder(catalog, snapshots)
    val service: DefaultTransactionLedgerService = DefaultTransactionLedgerService(
        sessions = sessions,
        repository = repository,
        catalog = catalog,
        settings = settings,
        feeRules = feeRules,
        parser = parser,
        eventBuilder = eventBuilder,
    )

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
