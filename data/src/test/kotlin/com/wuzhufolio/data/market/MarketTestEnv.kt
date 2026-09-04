package com.wuzhufolio.data.market

import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import java.nio.file.Files

/**
 * M5 数据层测试环境：临时 SQLCipher 库（schema 6）+ 种子币目录（含 cmc_id）+ 快照仓库 + 设置仓库。
 */
internal class MarketTestEnv : AutoCloseable {

    val db: WzDatabase = WzDatabase(
        Files.createTempDirectory("wuzhufolio-market").resolve("market.db"),
        randomDbKey(),
    )
    val gate: DbGate = DbGate(db)
    val catalog: SqlCoinCatalog = SqlCoinCatalog(gate)
    val snapshots: PriceSnapshotRepository = PriceSnapshotRepository(gate)
    val settings: SettingsRepository = SettingsRepository(gate)
    val deviceStore: DeviceSecretStore = DeviceSecretStore(testDeviceKey(), settings)

    init {
        db.migrateToLatest()
        seedDirectory()
    }

    fun seedDirectory(entries: List<CoinDirectoryEntry> = DEFAULT_ENTRIES) {
        kotlinx.coroutines.runBlocking {
            catalog.refreshDirectory(entries)
            catalog.refreshCmcIds(
                mapOf(
                    "bitcoin" to "1",
                    "ethereum" to "1027",
                    "tether" to "825",
                    "usd-coin" to "3408",
                    "dai" to "4943",
                    "true-usd" to "2563",
                    "binancecoin" to "1839",
                ),
            )
        }
    }

    suspend fun coinIdOf(cgId: String): Int = catalog.getByCgId(cgId)!!.id.toInt()

    override fun close() {
        deviceStore.close()
        db.close()
    }

    companion object {
        fun testDeviceKey(): ByteArray = ByteArray(32) { (it + 3).toByte() }

        val DEFAULT_ENTRIES: List<CoinDirectoryEntry> = listOf(
            CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
            CoinDirectoryEntry("ethereum", "eth", "Ethereum"),
            CoinDirectoryEntry("tether", "usdt", "Tether"),
            CoinDirectoryEntry("usd-coin", "usdc", "USD Coin"),
            CoinDirectoryEntry("dai", "dai", "Dai"),
            CoinDirectoryEntry("true-usd", "tusd", "TrueUSD"),
            CoinDirectoryEntry("binancecoin", "bnb", "BNB"),
            CoinDirectoryEntry("aaa-token-one", "aaa", "AAA Token One"),
            CoinDirectoryEntry("aaa-token-two", "aaa", "AAA Token Two"),
        )
    }
}

