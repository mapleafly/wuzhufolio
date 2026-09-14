package com.wuzhufolio.app.integration

import com.wuzhufolio.app.AppBootstrap
import com.wuzhufolio.app.AppDirs
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.SnapshotWrite
import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

/**
 * P5 集成联调测试基座：**以真实组合根启动全栈**（[AppBootstrap.run]）——
 * SQLCipher 加密库 + 迁移 + 密钥链 + 全部用例服务（账户/会话/行情/同步/账本/资金/校准/聚合/备份/设置/诊断）。
 *
 * 隔离口径：
 * - 数据目录 = 临时目录（`-Dwuzhufolio.dataDir`，[AppDirs] 的既有开发/测试覆盖入口）；
 * - 日志目录 = 同一临时目录（`-Dwuzhufolio.logdir`，logback 读取）；
 * - 每个基座一个独立库（随机主密钥或钥匙串密钥），测试结束 [close] 释放数据库/密钥/会话并停 Koin。
 *
 * 网络口径：外部行情/交易所端点不参与本基座（真实网络链路见 `data` 模块的 live smoke 与
 * `BinanceLoopbackIntegrationTest` 的本地回环桩）。行情数据在**数据边界**注入（目录 + 价格快照，
 * 均经真实仓库写入真实库）——这正是 M4/M7/M8/M12 消费行情的接口面。
 */
internal class IntegrationHarness private constructor(
    val dataDir: Path,
    val runtime: AppBootstrap.Runtime,
) : AutoCloseable {

    val catalog: SqlCoinCatalog get() = runtime.coinCatalog as SqlCoinCatalog
    val snapshots: PriceSnapshotRepository get() = PriceSnapshotRepository(runtime.gate)

    /** 目录中按符号取币（种子目录内唯一）。 */
    fun coin(symbol: String): CatalogCoin =
        runBlocking { catalog.getBySymbol(symbol.uppercase()).firstOrNull() }
            ?: error("coin $symbol not seeded")

    /** 写一条价格快照（真实仓库 + 真实库）。 */
    fun putPrice(symbol: String, fiat: String, price: String, at: Instant = NOW) {
        runBlocking {
            snapshots.upsert(
                SnapshotWrite(
                    coinId = coin(symbol).id.toInt(),
                    fiat = fiat,
                    price = BigDecimal(price),
                    source = PriceSource.COINGECKO,
                    at = at,
                ),
            )
        }
    }

    override fun close() {
        runCatching { runtime.close() }
        runCatching { stopKoin() }
        runCatching { System.clearProperty(DATA_DIR_PROPERTY) }
        runCatching { System.clearProperty(LOG_DIR_PROPERTY) }
        runCatching { dataDir.toFile().deleteRecursively() }
    }

    companion object {
        /** 固定基准时刻（快照/事件时间轴确定性；UTC）。 */
        val NOW: Instant = Instant.parse("2026-09-13T12:00:00Z")

        const val DATA_DIR_PROPERTY = "wuzhufolio.dataDir"
        const val LOG_DIR_PROPERTY = "wuzhufolio.logdir"

        /** 币种目录种子（与共享规范 §6 消歧口径一致的最小集：稳定币孪生 + 两个可交易币 + BNB）。 */
        val SEED_DIRECTORY: List<CoinDirectoryEntry> = listOf(
            CoinDirectoryEntry("bitcoin", "btc", "Bitcoin"),
            CoinDirectoryEntry("ethereum", "eth", "Ethereum"),
            CoinDirectoryEntry("tether", "usdt", "Tether"),
            CoinDirectoryEntry("usd-coin", "usdc", "USD Coin"),
            CoinDirectoryEntry("dai", "dai", "Dai"),
            CoinDirectoryEntry("binancecoin", "bnb", "BNB"),
        )

        /**
         * 启动全栈（真实组合根）。[seedPrices] 为 true 时写入种子币的基准价快照
         * （usdt/usdc/dai = 1.00，btc = 50,000，eth = 3,000，均为 USD 口径）。
         */
        /**
         * 启动全栈（真实组合根）。[seedDirectory] = 写入币种目录种子；[seedPrices] = 写入种子币基准价快照。
         * 二者同时为 false = **全新安装**口径（目录与快照皆空，录入前须由行情刷新补齐——P5 人工验收复现）。
         */
        fun boot(seedDirectory: Boolean = true, seedPrices: Boolean = true): IntegrationHarness {
            val dir = Files.createTempDirectory("wuzhufolio-p5")
            System.setProperty(DATA_DIR_PROPERTY, dir.toString())
            System.setProperty(LOG_DIR_PROPERTY, dir.resolve("logs").toString())
            AppDirs.ensureDataDirs()
            // Koin 为进程级单例：同一 JVM 内多次启动必须先停（测试隔离）
            runCatching { stopKoin() }
            val logger = LoggerFactory.getLogger("wuzhufolio.p5.integration")
            val runtime = AppBootstrap.run(logger)
            val harness = IntegrationHarness(dir, runtime)
            if (seedDirectory) runBlocking { harness.catalog.refreshDirectory(SEED_DIRECTORY) }
            if (seedPrices) {
                harness.putPrice("USDT", "USD", "1.00")
                harness.putPrice("USDC", "USD", "1.00")
                harness.putPrice("DAI", "USD", "1.00")
                harness.putPrice("BTC", "USD", "50000")
                harness.putPrice("ETH", "USD", "3000")
            }
            return harness
        }
    }
}
