package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.ActiveSession
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.security.CryptoService
import java.nio.file.Files

/**
 * M6 数据层测试环境：临时 SQLCipher 库（schema 9）+ 会话注入（固定 DEK/账户） + 种子币目录。
 */
internal class ExchangeTestEnv : AutoCloseable {

    val db: WzDatabase = WzDatabase(
        Files.createTempDirectory("wuzhufolio-exchange").resolve("exchange.db"),
        randomDbKey(),
    )
    val gate: DbGate = DbGate(db)
    val settings: SettingsRepository = SettingsRepository(gate)
    val crypto: CryptoService = CryptoService()
    val accounts: AccountRepository = AccountRepository(gate)
    val apiKeys: ApiKeyRepository = ApiKeyRepository(gate)
    val syncLogs: SyncLogRepository = SyncLogRepository(gate)
    val transactions: ExchangeTransactionRepository = ExchangeTransactionRepository(gate)
    val catalog = com.wuzhufolio.data.catalog.SqlCoinCatalog(gate)
    val sessions = ActiveSessionStore()

    init {
        db.migrateToLatest()
        kotlinx.coroutines.runBlocking {
            catalog.refreshDirectory(seedDirectory())
        }
    }

    /** 造一个账户并把会话置为激活（DEK = 测试固定 32B）。 */
    fun loginAccount(username: String = "tester"): ActiveSession {
        val account = kotlinx.coroutines.runBlocking {
            accounts.createWrapped(username, "hash", "00".repeat(16), "p") { id -> "wrapped-" + id }
        }
        val session = ActiveSession(AccountSummary(account.id, username), dek())
        sessions.set(session)
        return session
    }

    override fun close() {
        db.close()
    }

    companion object {
        fun dek(): ByteArray = ByteArray(32) { (it + 1).toByte() }

        fun seedDirectory(): List<com.wuzhufolio.domain.catalog.CoinDirectoryEntry> = listOf(
            com.wuzhufolio.domain.catalog.CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
            com.wuzhufolio.domain.catalog.CoinDirectoryEntry("ethereum", "eth", "Ethereum"),
            com.wuzhufolio.domain.catalog.CoinDirectoryEntry("tether", "usdt", "Tether"),
            com.wuzhufolio.domain.catalog.CoinDirectoryEntry("binancecoin", "bnb", "BNB"),
        )
    }
}
