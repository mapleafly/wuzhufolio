package com.wuzhufolio.app

import com.wuzhufolio.app.AppDirs
import com.wuzhufolio.app.di.appModule
import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.hello.HelloChain
import com.wuzhufolio.data.security.FileMasterKeyStore
import com.wuzhufolio.data.security.KeyStorageBackend
import com.wuzhufolio.data.security.KeyStorageReport
import com.wuzhufolio.data.security.KeychainAccounts
import com.wuzhufolio.data.security.KeychainMasterKeyStore
import com.wuzhufolio.data.security.MasterKeyResolver
import com.wuzhufolio.data.security.MasterKeyStore
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.DefaultAccountService
import com.wuzhufolio.data.security.RememberMeStore
import com.wuzhufolio.data.security.RememberMeStoreFactory
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.market.CmcMarketClient
import com.wuzhufolio.data.market.CoingeckoMarketClient
import com.wuzhufolio.data.market.DefaultMarketRefreshService
import com.wuzhufolio.data.market.DefaultMarketSettingsService
import com.wuzhufolio.data.market.DeviceSecretStore
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.RefreshableRankProvider
import com.wuzhufolio.data.market.SettingsMarketWatchService
import com.wuzhufolio.data.market.SnapshotMarketQuotesService
import com.wuzhufolio.data.market.SettingsQuotaLedger
import com.wuzhufolio.data.market.newOkHttpMarketClient
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.exchange.BinanceAdapter
import com.wuzhufolio.data.exchange.DefaultExchangeSyncService
import com.wuzhufolio.data.exchange.ExchangeTransactionRepository
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.accounts.AccountService
import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import org.koin.core.context.startKoin
import org.slf4j.Logger
import java.nio.file.Path

/**
 * M1 启动引导（T1.1 落地）：主密钥取用（OS 钥匙串优先，不可用降级本地密钥文件）→ SQLCipher 开库 →
 * 迁移 → hello 链路（脱敏日志）→ Koin 组装。失败抛类型化异常，由 Main 渲染致命窗口；
 * 「从 .cpro 恢复」等正式恢复引导页面随 M2/M9 提供（interaction.md §2.1 B1）。
 */
object AppBootstrap {

    /** 主壳初始展示状态（主题/盈亏配色来自设置；降级说明非空时弹安全提示）。 */
    data class UiState(
        val theme: ThemeMode,
        val pnlScheme: PnlColorScheme,
        /** 非空 = 需向用户展示的安全降级说明（T1.1「无钥匙串降级提示」）。 */
        val securityNotice: String?,
    )

    /** 账户会话装配（M2：用例 + 记住我存储，close 释放存储后端）。 */
    class SessionRuntime internal constructor(
        val authService: AccountService,
        private val rememberStore: RememberMeStore,
    ) {
        fun close() {
            runCatching { rememberStore.close() }
        }
    }

    /** 运行期装配结果：窗口关闭时 [close] 释放数据库连接/钥匙串/会话存储/行情服务。 */
    @Suppress("LongParameterList") // 运行期装配袋（db/gate/设置/会话/行情三件/四资源句柄），窗口生命周期统一释放点
    class Runtime internal constructor(
        val db: WzDatabase,
        val gate: DbGate,
        val settings: SettingsRepository,
        val uiState: UiState,
        val session: SessionRuntime,
        /** M5：行情 Key 设置用例（T5.5，设备密钥加密全局行）。 */
        val marketSettingsService: MarketSettingsService,
        /** M5：行情刷新编排（T5.1/T5.4，主源→兜底 + 目录维护 + 额度计数）。 */
        val marketRefreshService: MarketRefreshService,
        /** D21：行情浏览页——自选（持久化）与报价组装。 */
        val marketWatchService: MarketWatchService,
        val marketQuotesService: MarketQuotesService,
        /** M6：交易所同步用例（API 管理页 + 增量同步 + sync_logs/状态）。 */
        val exchangeSyncService: ExchangeSyncService,
        private val deviceStore: DeviceSecretStore,
        private val keyring: MasterKeyStore?,
        private val deviceKeyring: MasterKeyStore?,
        private val httpClient: java.io.Closeable,
        private val exchangeHttpClient: java.io.Closeable,
    ) {
        fun close() {
            runCatching { session.close() }
            runCatching { deviceStore.close() } // 设备密钥零化（ADR-002 §2）
            runCatching { keyring?.close() }
            runCatching { deviceKeyring?.close() }
            runCatching { httpClient.close() }
            runCatching { exchangeHttpClient.close() }
            db.close()
        }
    }

    @Suppress("LongMethod") // 引导装配链（密钥→DB→服务束→Koin→Runtime），步骤内聚
    fun run(logger: Logger): Runtime {
        val keyring: MasterKeyStore =
            KeychainMasterKeyStore(KeychainAccounts.SERVICE, KeychainAccounts.DB_KEY)
        val keyFile: Path = AppDirs.dataDir().resolve("master.key")
        val report: KeyStorageReport =
            MasterKeyResolver.obtainMasterKey(keyring, FileMasterKeyStore(keyFile), logger)

        val db = try {
            WzDatabase(AppDirs.dbPath(), report.key)
        } finally {
            report.wipe() // WzDatabase 已把密钥编入连接属性，原数组立即清零（ADR-002 §2 密钥擦除）
        }
        val gate = DbGate(db)
        val settings = SettingsRepository(gate)
        val accountRepository = AccountRepository(gate)
        val rememberStore = RememberMeStoreFactory.open(report.backend, AppDirs.dataDir(), logger)
        val sessions = ActiveSessionStore()
        val authService: AccountService = DefaultAccountService(
            repository = accountRepository,
            rememberStore = rememberStore,
            sessions = sessions,
        )
        val hello = HelloChain(db, settings, logger).run()

        val market = MarketServicesBundle.run(gate, settings, logger)
        val exchange = ExchangeServicesBundle.run(
            gate,
            settings,
            sessions,
            market.catalog,
            rankWarmUp = { market.marketRefreshService.warmUpRankCache() },
            logger = logger,
        )

        startKoin { modules(appModule(db, gate, settings)) }

        logger.info(
            LogRedactor.redact(
                "bootstrap ok | db=" + AppDirs.dbPath() +
                    " | key_backend=" + report.backend +
                    " | schema=" + hello.schemaVersion +
                    " | settings(count=" + hello.settings.size + ")" +
                    " | theme=" + hello.theme.storageValue +
                    " | pnl_scheme=" + hello.pnlScheme.storageValue,
            ),
        )
        return Runtime(
            db = db,
            gate = gate,
            settings = settings,
            uiState = UiState(
                theme = hello.theme,
                pnlScheme = hello.pnlScheme,
                securityNotice = securityNotice(report, keyFile),
            ),
            session = SessionRuntime(authService = authService, rememberStore = rememberStore),
            marketSettingsService = market.marketSettingsService,
            marketRefreshService = market.marketRefreshService,
            marketWatchService = market.marketWatchService,
            marketQuotesService = market.marketQuotesService,
            exchangeSyncService = exchange.exchangeSyncService,
            deviceStore = market.deviceStore,
            keyring = if (report.backend == KeyStorageBackend.OS_KEYCHAIN) keyring else null,
            deviceKeyring = market.deviceKeyring,
            httpClient = market.httpClient,
            exchangeHttpClient = exchange.httpClient,
        )
    }

    /**
     * M5 行情服务装配（T5.1–T5.5）：设备密钥（方案甲）→ 目录/快照/额度 → 编排与 Key 设置用例。
     * deviceKeyring 仅在 OS 钥匙串后端时由 Runtime 持有关闭；降级文件后端立即释放。
     */
    @Suppress("LongParameterList") // 服务装配袋（五个用例服务 + 三个资源句柄），同 Runtime 释放链
    class MarketServicesBundle internal constructor(
        val marketSettingsService: MarketSettingsService,
        val marketRefreshService: MarketRefreshService,
        val marketWatchService: MarketWatchService,
        val marketQuotesService: MarketQuotesService,
        /** M6 二轮：共享市值排名缓存的币种目录（交易所同步消歧规则③依赖——M3 遗留 4）。 */
        val catalog: SqlCoinCatalog,
        val deviceStore: DeviceSecretStore,
        val deviceKeyring: MasterKeyStore?,
        val httpClient: java.io.Closeable,
    ) {
        companion object {
            fun run(gate: DbGate, settings: SettingsRepository, logger: Logger): MarketServicesBundle {
                val deviceKeyring: MasterKeyStore =
                    KeychainMasterKeyStore(KeychainAccounts.SERVICE, KeychainAccounts.DEVICE_KEY)
                val deviceReport: KeyStorageReport =
                    MasterKeyResolver.obtainMasterKey(
                        deviceKeyring,
                        FileMasterKeyStore(AppDirs.dataDir().resolve("device.key")),
                        logger,
                    )
                val deviceStore = DeviceSecretStore(deviceReport.key, settings, logger)
                val rankCache = RefreshableRankProvider()
                // M6 二轮：共享目录接入市值排名缓存（消歧规则③——交易所同步歧义资产按排名自动裁定）
                val catalog = SqlCoinCatalog(gate, rankCache)
                val httpClient = newOkHttpMarketClient()
                val marketRefreshService: MarketRefreshService = DefaultMarketRefreshService(
                    cgClient = CoingeckoMarketClient(httpClient),
                    cmcClient = CmcMarketClient(httpClient),
                    catalog = catalog,
                    snapshots = PriceSnapshotRepository(gate),
                    keyStore = deviceStore,
                    settings = settings,
                    quota = SettingsQuotaLedger(settings),
                    rankCache = rankCache,
                    logger = logger,
                )
                val marketSettingsService: MarketSettingsService = DefaultMarketSettingsService(deviceStore, settings)
                val marketWatchService: MarketWatchService = SettingsMarketWatchService(settings, catalog)
                val marketQuotesService: MarketQuotesService = SnapshotMarketQuotesService(
                    PriceSnapshotRepository(gate),
                )
                return MarketServicesBundle(
                    marketSettingsService = marketSettingsService,
                    marketRefreshService = marketRefreshService,
                    marketWatchService = marketWatchService,
                    marketQuotesService = marketQuotesService,
                    catalog = catalog,
                    deviceStore = deviceStore,
                    deviceKeyring = if (deviceReport.backend == KeyStorageBackend.OS_KEYCHAIN) deviceKeyring else null,
                    httpClient = httpClient,
                )
            }
        }
    }

    /**
     * M6 交易所同步服务装配（T6.1–T6.3）：api_keys/sync_logs/transactions 仓库 + 币目录 + 适配器工厂。
     * exchangeSyncService 用例供 API 管理页（ui/exchange）消费；BinanceAdapter 绑定会话内解密凭证，
     * 字段级加密（账户 DEK）由 DefaultExchangeSyncService 内完成（ADR-002 §2）。
     */
    @Suppress("LongParameterList") // 装配袋（gate/设置/会话/日志），同 Runtime 释放链
    class ExchangeServicesBundle internal constructor(
        val exchangeSyncService: ExchangeSyncService,
        val httpClient: java.io.Closeable,
    ) {
        companion object {
            fun run(
                gate: DbGate,
                settings: SettingsRepository,
                sessions: ActiveSessionStore,
                catalog: SqlCoinCatalog,
                rankWarmUp: suspend () -> Unit,
                logger: Logger,
            ): ExchangeServicesBundle {
                val httpClient = newOkHttpExchangeClient()
                val adapterFactory: (com.wuzhufolio.domain.exchange.ExchangeCredentials) ->
                com.wuzhufolio.domain.exchange.ExchangeAdapter = { credentials ->
                    BinanceAdapter(httpClient, credentials)
                }
                val syncService: ExchangeSyncService = DefaultExchangeSyncService(
                    sessions = sessions,
                    crypto = CryptoService(),
                    apiKeyRepository = ApiKeyRepository(gate),
                    syncLogRepository = SyncLogRepository(gate),
                    transactionsRepository = ExchangeTransactionRepository(gate),
                    catalog = catalog,
                    settings = settings,
                    adapterFactory = adapterFactory,
                    rankWarmUp = rankWarmUp,
                    logger = logger,
                )
                return ExchangeServicesBundle(exchangeSyncService = syncService, httpClient = httpClient)
            }
        }
    }

    private fun securityNotice(report: KeyStorageReport, keyFile: Path): String? =
        when (report.backend) {
            KeyStorageBackend.OS_KEYCHAIN -> null
            KeyStorageBackend.FILE_FALLBACK ->
                "系统钥匙串不可用（Windows 凭据管理器 / macOS 钥匙串 / Linux Secret Service 均无法使用），\n" +
                    "数据库主密钥已降级存入本地密钥文件：\n" + keyFile + "\n\n" +
                    "请确保：\n" +
                    "① 该文件仅当前用户可读（已设 0600）；\n" +
                    "② 自行妥善保管与备份（与数据库文件同等重要）；\n" +
                    "③ 此降级模式保护弱于系统钥匙串。\n\n" +
                    "钥匙串环境恢复后，可重建密钥并重新导出备份（后续版本提供迁移引导）。"
        }
}
