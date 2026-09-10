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
import com.wuzhufolio.data.settings.DefaultDiagnosticsService
import com.wuzhufolio.data.settings.DefaultDesktopSettingsService
import com.wuzhufolio.data.settings.DefaultGeneralSettingsService
import com.wuzhufolio.data.settings.DesktopPreferences
import com.wuzhufolio.data.settings.GeneralSettingsKeys
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.data.autostart.PlatformAutostartService
import com.wuzhufolio.domain.autostart.AutostartCommand
import com.wuzhufolio.data.proxy.ProxyRuntime
import com.wuzhufolio.data.schedule.BackgroundScheduler
import com.wuzhufolio.data.schedule.DefaultSchedulerSources
import com.wuzhufolio.data.backup.BackupSettingsStore
import com.wuzhufolio.domain.schedule.BackupReminder
import com.wuzhufolio.domain.settings.DesktopSettingsService
import kotlinx.coroutines.runBlocking
import java.time.Instant
import com.wuzhufolio.data.catalog.SqlCoinCatalog
import com.wuzhufolio.data.logging.FileLogAccess
import com.wuzhufolio.data.logging.LogRotator
import com.wuzhufolio.data.market.CmcMarketClient
import com.wuzhufolio.data.market.CoingeckoMarketClient
import com.wuzhufolio.data.market.DefaultMarketRefreshService
import com.wuzhufolio.data.market.DefaultMarketSettingsService
import com.wuzhufolio.data.market.DeviceSecretStore
import com.wuzhufolio.data.market.PriceSnapshotRepository
import com.wuzhufolio.data.market.RefreshableRankProvider
import com.wuzhufolio.data.market.SettingsMarketWatchService
import com.wuzhufolio.data.market.SettingsQuotaLedger
import com.wuzhufolio.data.market.SnapshotMarketQuotesService
import com.wuzhufolio.data.market.newOkHttpMarketClient
import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.exchange.BinanceAdapter
import com.wuzhufolio.data.exchange.DefaultExchangeSyncService
import com.wuzhufolio.data.exchange.ExchangeTransactionRepository
import com.wuzhufolio.data.exchange.SyncLogRepository
import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.data.ledger.CsvTradeParser
import com.wuzhufolio.data.ledger.DefaultCalibrationService
import com.wuzhufolio.data.ledger.DefaultFeeRuleService
import com.wuzhufolio.data.ledger.DefaultFundService
import com.wuzhufolio.data.portfolio.DefaultPortfolioService
import com.wuzhufolio.data.ledger.DefaultTransactionLedgerService
import com.wuzhufolio.data.ledger.FeeRuleRepository
import com.wuzhufolio.data.ledger.FundFlowRepository
import com.wuzhufolio.data.ledger.LedgerEventAssembler
import com.wuzhufolio.data.ledger.LedgerTransactionRepository
import com.wuzhufolio.data.ledger.ReconciliationRepository
import com.wuzhufolio.data.ledger.TransactionEventBuilder
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.ledger.CalibrationUseCase
import com.wuzhufolio.domain.ledger.FeeRuleService
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.market.MarketQuotesService
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.accounts.AccountService
import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.DiagnosticsService
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.LogAccess
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

    /** 主壳初始展示状态（主题/盈亏配色/界面语言来自设置；降级说明非空时弹安全提示）。 */
    data class UiState(
        val theme: ThemeMode,
        val pnlScheme: PnlColorScheme,
        /** M12 T12.4：界面语言（settings 全局键 `ui.language`，默认中文）。 */
        val language: AppLanguage,
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
        /** M7：交易账本用例（手动增删改 + 费率自动计算 + CSV 导入，交易管理页）。 */
        val transactionLedgerService: TransactionLedgerService,
        /** M7：费率规则设置（最小 CRUD，设置页「手续费」分组；M10 整页接管）。 */
        val feeRuleService: FeeRuleService,
        /** M8：资金管理用例（增资/撤资 + 校准行入列 + 资金页，T8.1–T8.3）。 */
        val fundService: FundService,
        /** M8：持仓校准用例（单一来源门 + 差额规划 + 锚点入库 + 日志留痕）。 */
        val calibrationService: CalibrationUseCase,
        /** M12 T12.1：聚合页只读数据源（仪表盘/资产列表/币种详情）。 */
        val portfolioService: PortfolioService,
        /** M12：币种目录（币种详情校准需 coins 行主键，配合 cg_id 精确消歧）。 */
        val coinCatalog: com.wuzhufolio.domain.catalog.CoinCatalog,
        /** M12：状态栏备份提醒天数提供者（距上次备份 > 30 天；null = 不提醒）。 */
        val backupReminderDaysProvider: suspend () -> Long?,
        /** M12：最近一次备份时刻（仪表盘「安全与隐私」面板末行；null = 从未备份）。 */
        val lastBackupAtProvider: suspend () -> java.time.Instant?,
        /** M9：备份恢复用例（.cpro 导出/预览/恢复 + CSV 明文导出，数据管理区）。 */
        val backupService: com.wuzhufolio.domain.backup.BackupService,
        /** M10：通用设置用例（T10.1：法币/精度/枚举开关/稳定币白名单/阈值/代理开关）。 */
        val generalSettingsService: GeneralSettingsService,
        /** M10：诊断报告用例（T10.3：版本/schema/脱敏日志片段/调用计数）。 */
        val diagnosticsService: DiagnosticsService,
        /** M10：本地日志查看/导出（T10.2：查看/导出均逐行脱敏）。 */
        val logAccess: LogAccess,
        /** M11 T11.3：系统代理运行期（检测状态流 + 注入两个 HTTP 客户端的开关感知 selector）。 */
        val proxyRuntime: ProxyRuntime,
        /** M11 T11.1/T11.2：托盘与后台设置用例（最小化到托盘/自启/同步通知/备份提醒）。 */
        val desktopSettingsService: DesktopSettingsService,
        /** M11：桌面集成偏好运行期镜像（关窗/通知判定走内存，不阻塞 EDT）。 */
        val desktopPreferences: DesktopPreferences,
        /** M11：后台调度循环宿主（行情刷新/交易同步/运行期日志轮转）。 */
        val scheduler: BackgroundScheduler,
        /** 引导期日志器（调度事件旁路与运行期诊断复用）。 */
        val logger: Logger,
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

        // M10 T10.2：日志轮转（本地日志 1 万条/90 天 + sync_logs 同口径）——启动执行一次，摘要入日志
        val logRotation = LogRotator.rotate(AppDirs.logDir())
        val syncRotation = SyncLogRepository(gate).rotate(java.time.Instant.now())
        logger.info(
            LogRedactor.redact(
                "log rotation ok | files: " + logRotation +
                    " | sync_logs removed byAge=" + syncRotation.first + " byCount=" + syncRotation.second,
            ),
        )

        val generalSettings: GeneralSettingsService = DefaultGeneralSettingsService(settings)

        // M11 T11.3：代理运行期——开关初值取自设置（缺省开），selector 注入两个 HTTP 客户端
        // （同一实例：PRD §7.2-6.2「所有对外请求均经代理」由共用 selector 保证，不各自判定）
        val proxyRuntime = ProxyRuntime()
        proxyRuntime.setEnabled(
            settings.getGlobal(GeneralSettingsKeys.PROXY_ENABLED)?.let { it != "off" } ?: true,
        )
        logger.info(
            LogRedactor.redact(
                "proxy init | enabled=" + proxyRuntime.current().enabled +
                    " | indicator=" + proxyRuntime.current().indicator,
            ),
        )

        val market = MarketServicesBundle.run(gate, settings, logger, proxyRuntime.selector)
        val exchange = ExchangeServicesBundle.run(
            gate,
            settings,
            sessions,
            market.catalog,
            rankWarmUp = { market.marketRefreshService.warmUpRankCache() },
            logger = logger,
            proxySelector = proxyRuntime.selector,
        )
        val ledger = LedgerServicesBundle.run(
            gate,
            settings,
            sessions,
            market.catalog,
            exchange = exchange,
            cashCoinIds = { (generalSettings as DefaultGeneralSettingsService).cashCoinIds() },
        )
        val backup = BackupServicesBundle.run(
            gate,
            settings,
            sessions,
            market.catalog,
            exchange = exchange,
            backupsDir = AppDirs.dataDir().resolve("backups"),
            logger = logger,
        )
        val diagnostics: DiagnosticsService = DefaultDiagnosticsService(
            appVersion = com.wuzhufolio.data.backup.DefaultBackupService.APP_VERSION,
            schemaVersion = hello.schemaVersion,
            quota = SettingsQuotaLedger(settings),
            syncCount = { SyncLogRepository(gate).countAll() },
            lastSyncAt = { SyncLogRepository(gate).lastSyncAt() },
            logTail = { LogRotator.tailLines(AppDirs.logDir().resolve("wuzhufolio.log"), 100) },
        )

        startKoin { modules(appModule(db, gate, settings)) }

        // M11 T11.1/T11.2：托盘与后台设置 + 自启平台注册（启动自愈：注册项漂移修正，失败不阻断启动）
        val desktopPreferences = DesktopPreferences()
        val desktopSettingsService: DesktopSettingsService = DefaultDesktopSettingsService(
            settings = settings,
            autostart = PlatformAutostartService(
                // 只在打包版（jpackage 启动器）注册自启：`jpackage.app-path` 为空 = 开发态，
                // 此时命令行首 token 是裸 java（无 -cp），注册项自启即退——宁可不支持也不写坏注册项。
                // 因此这里显式传 commandLine = null（判定与理由见模块记录 M11 §5-2）。
                executablePath = AutostartCommand.resolve(
                    System.getProperty("jpackage.app-path"),
                    null,
                ),
                logger = logger,
            ),
            prefs = desktopPreferences,
            logger = logger,
        )
        val autostartNote = runBlocking {
            val note = runCatching { desktopSettingsService.reconcileAutostart() }
                .getOrDefault("")
            runCatching { desktopSettingsService.view() } // 初始化 DesktopPreferences 镜像
            note
        }
        if (autostartNote.isNotEmpty()) logger.info(LogRedactor.redact("autostart reconcile | " + autostartNote))

        // M11：后台调度循环宿主（行情刷新/交易同步/运行期日志轮转；tick 顺带复检系统代理）
        val scheduler = BackgroundScheduler(
            sources = DefaultSchedulerSources(
                marketRefreshService = market.marketRefreshService,
                marketSettingsService = market.marketSettingsService,
                syncService = exchange.exchangeSyncService,
                rotateLogs = { rotateLogsNow(gate) },
                backupReminderDays = { backupReminderDays(gate, accountRepository, sessions) },
            ),
            onTick = { proxyRuntime.refresh() },
            logger = logger,
        )

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
                language = AppLanguage.fromStorage(settings.getGlobal(GeneralSettingsKeys.LANGUAGE)),
                securityNotice = securityNotice(report, keyFile),
            ),
            session = SessionRuntime(authService = authService, rememberStore = rememberStore),
            marketSettingsService = market.marketSettingsService,
            marketRefreshService = market.marketRefreshService,
            marketWatchService = market.marketWatchService,
            marketQuotesService = market.marketQuotesService,
            exchangeSyncService = exchange.exchangeSyncService,
            transactionLedgerService = ledger.transactionLedgerService,
            feeRuleService = ledger.feeRuleService,
            fundService = ledger.fundService,
            calibrationService = ledger.calibrationService,
            portfolioService = ledger.portfolioService,
            coinCatalog = market.catalog,
            backupReminderDaysProvider = { backupReminderDays(gate, accountRepository, sessions) },
            lastBackupAtProvider = { lastBackupAt(gate, sessions) },
            backupService = backup.backupService,
            generalSettingsService = generalSettings,
            diagnosticsService = diagnostics,
            logAccess = FileLogAccess(AppDirs.logDir()),
            proxyRuntime = proxyRuntime,
            desktopSettingsService = desktopSettingsService,
            desktopPreferences = desktopPreferences,
            scheduler = scheduler,
            logger = logger,
            deviceStore = market.deviceStore,
            keyring = if (report.backend == KeyStorageBackend.OS_KEYCHAIN) keyring else null,
            deviceKeyring = market.deviceKeyring,
            httpClient = market.httpClient,
            exchangeHttpClient = exchange.httpClient,
        )
    }

    /**
     * 最近一次备份时刻（M12 仪表盘「安全与隐私」面板；账户级元数据 backup.last_at，M9 落盘）。
     * 从未备份 / 解析失败 → null（面板不展示该行，不猜测时间）。
     */
    private fun lastBackupAt(gate: DbGate, sessions: ActiveSessionStore): Instant? =
        sessions.get()?.account?.id
            ?.let { accountId ->
                BackupSettingsStore(gate).getAccountSetting(
                    accountId,
                    com.wuzhufolio.data.backup.DefaultBackupService.SETTING_LAST_BACKUP,
                )
            }
            ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }

    /** 运行期日志轮转（M11 · M10 §5-5 遗留闭环）：本地日志 + sync_logs 同口径，返回脱敏摘要。 */
    private fun rotateLogsNow(gate: DbGate): String {
        val files = LogRotator.rotate(AppDirs.logDir())
        val syncLogs = SyncLogRepository(gate).rotate(Instant.now())
        return "files=" + files + " syncLogs(byAge=" + syncLogs.first + ",byCount=" + syncLogs.second + ")"
    }

    /**
     * 备份提醒判定（PRD 6.1：距上次备份 > 30 天）。基线口径见
     * [com.wuzhufolio.domain.schedule.BackupReminder]：优先账户级 `backup.last_at`（M9 落盘），
     * 从未备份过则退化为账户创建时刻（新装用户满 30 天前不打扰）。返回 null = 不提醒。
     */
    private fun backupReminderDays(
        gate: DbGate,
        accounts: AccountRepository,
        sessions: ActiveSessionStore,
    ): Long? {
        val accountId = sessions.get()?.account?.id
        val lastBackup = accountId?.let {
            BackupSettingsStore(gate)
                .getAccountSetting(it, com.wuzhufolio.data.backup.DefaultBackupService.SETTING_LAST_BACKUP)
        }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val createdAt = accountId?.let { accounts.findById(it)?.createdAt }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val now = Instant.now()
        // 到期才返回天数；基线 = 上次备份时刻，从未备份则退化为账户创建时刻（两者皆无 → null）
        val baseline = if (BackupReminder.isDue(lastBackup, createdAt, now)) lastBackup ?: createdAt else null
        return baseline?.let { java.time.Duration.between(it, now).toDays() }
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
            fun run(
                gate: DbGate,
                settings: SettingsRepository,
                logger: Logger,
                /** M11 T11.3：系统代理 selector（null = OkHttp 默认；测试装配可不传）。 */
                proxySelector: java.net.ProxySelector? = null,
            ): MarketServicesBundle {
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
                val httpClient = newOkHttpMarketClient(proxySelector)
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
        /** M8 校准流复用：密钥仓库/同步日志仓库/适配器工厂（同一 HTTP 客户端，不另建连接池）。 */
        val apiKeyRepository: ApiKeyRepository,
        val syncLogRepository: SyncLogRepository,
        val adapterFactory: (com.wuzhufolio.domain.exchange.ExchangeCredentials) ->
        com.wuzhufolio.domain.exchange.ExchangeAdapter,
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
                /** M11 T11.3：系统代理 selector（与行情客户端共用同一实例——PRD 6.2 全请求经代理）。 */
                proxySelector: java.net.ProxySelector? = null,
            ): ExchangeServicesBundle {
                val httpClient = newOkHttpExchangeClient(proxySelector)
                val adapterFactory: (com.wuzhufolio.domain.exchange.ExchangeCredentials) ->
                com.wuzhufolio.domain.exchange.ExchangeAdapter = { credentials ->
                    BinanceAdapter(httpClient, credentials)
                }
                val apiKeyRepository = ApiKeyRepository(gate)
                val syncLogRepository = SyncLogRepository(gate)
                val syncService: ExchangeSyncService = DefaultExchangeSyncService(
                    sessions = sessions,
                    crypto = CryptoService(),
                    apiKeyRepository = apiKeyRepository,
                    syncLogRepository = syncLogRepository,
                    transactionsRepository = ExchangeTransactionRepository(gate),
                    catalog = catalog,
                    settings = settings,
                    adapterFactory = adapterFactory,
                    rankWarmUp = rankWarmUp,
                    logger = logger,
                )
                return ExchangeServicesBundle(
                    exchangeSyncService = syncService,
                    apiKeyRepository = apiKeyRepository,
                    syncLogRepository = syncLogRepository,
                    adapterFactory = adapterFactory,
                    httpClient = httpClient,
                )
            }
        }
    }

    /**
     * M7/M8 交易账本服务装配（T7.1–T7.4 + T8.1–T8.3）：账本仓库 + 资金/校准仓库 + 费率规则仓库 +
     * CSV 解析器 + 事件构造层 + 全量事件装配层 + 用例服务（交易/资金/校准）。
     * 与 M6 同步编排共享同一币种目录（market.catalog——事件 FK->cg_id 解析、CSV/手动消歧同口径）；
     * 快照仓库独立实例（同表无状态，先例 = SnapshotMarketQuotesService）。
     */
    @Suppress("LongParameterList") // 装配袋（gate/设置/会话/目录/交换束/日志轮转），同 Runtime 释放链
    class LedgerServicesBundle internal constructor(
        val transactionLedgerService: TransactionLedgerService,
        val feeRuleService: FeeRuleService,
        val fundService: FundService,
        val calibrationService: CalibrationUseCase,
        /** M12 T12.1：聚合页只读数据源（仪表盘/资产列表/币种详情的持仓与指标）。 */
        val portfolioService: PortfolioService,
    ) {
        companion object {
            fun run(
                gate: DbGate,
                settings: SettingsRepository,
                sessions: ActiveSessionStore,
                catalog: SqlCoinCatalog,
                exchange: ExchangeServicesBundle,
                /** M10 T10.1：稳定币白名单运行期读取（设置扩展 → 事件构造 USD 锚定 + 资金总览可用现金）。 */
                cashCoinIds: () -> Set<String> = {
                    com.wuzhufolio.domain.engine.PortfolioCalculator.DEFAULT_CASH_COIN_IDS
                },
            ): LedgerServicesBundle {
                val feeRules = FeeRuleRepository(gate)
                val txRepository = LedgerTransactionRepository(gate)
                val fundRepository = FundFlowRepository(gate)
                val reconRepository = ReconciliationRepository(gate)
                val snapshots = PriceSnapshotRepository(gate)
                val eventBuilder = TransactionEventBuilder(catalog, snapshots, cashCoinIds)
                val assembler = LedgerEventAssembler(catalog, eventBuilder)
                val service: TransactionLedgerService = DefaultTransactionLedgerService(
                    sessions = sessions,
                    repository = txRepository,
                    fundsRepository = fundRepository,
                    reconsRepository = reconRepository,
                    catalog = catalog,
                    settings = settings,
                    feeRules = feeRules,
                    parser = CsvTradeParser(catalog),
                    eventBuilder = eventBuilder,
                    assembler = assembler,
                )

                return LedgerServicesBundle(
                    transactionLedgerService = service,
                    feeRuleService = DefaultFeeRuleService(sessions, feeRules),
                    fundService = DefaultFundService(
                        sessions = sessions,
                        transactions = txRepository,
                        funds = fundRepository,
                        recons = reconRepository,
                        catalog = catalog,
                        settings = settings,
                        eventBuilder = eventBuilder,
                        assembler = assembler,
                        cashCoinIds = cashCoinIds,
                    ),
                    calibrationService = DefaultCalibrationService(
                        sessions = sessions,
                        catalog = catalog,
                        settings = settings,
                        transactions = txRepository,
                        funds = fundRepository,
                        recons = reconRepository,
                        assembler = assembler,
                        eventBuilder = eventBuilder,
                        apiKeyRepository = exchange.apiKeyRepository,
                        crypto = CryptoService(),
                        adapterFactory = exchange.adapterFactory,
                        syncLogs = exchange.syncLogRepository,
                    ),
                    portfolioService = portfolioService(
                        sessions, txRepository, fundRepository, reconRepository,
                        catalog, snapshots, settings, assembler, eventBuilder, cashCoinIds,
                    ),
                )
            }

        /**
             * M12 T12.1 聚合页数据源装配（与交易/资金/校准共享同一批仓库与事件装配层，保证仪表盘指标与
             * 资金页总览同源——同一重放输入、同一计算器）；现价取数链同 [TransactionEventBuilder]
             *（快照 → USD 锚定 1:1 → 最近可得），首刷成功前纯稳定币账户两页口径一致。
             */
        @Suppress("LongParameterList") // 装配袋（七个依赖 + 三个纯函数参数），与同类 run() 同构
            private fun portfolioService(
                sessions: ActiveSessionStore,
                txRepository: LedgerTransactionRepository,
                fundRepository: FundFlowRepository,
                reconRepository: ReconciliationRepository,
                catalog: SqlCoinCatalog,
                snapshots: PriceSnapshotRepository,
                settings: SettingsRepository,
                assembler: LedgerEventAssembler,
                eventBuilder: TransactionEventBuilder,
                cashCoinIds: () -> Set<String>,
            ): PortfolioService = DefaultPortfolioService(
                sessions = sessions,
                transactions = txRepository,
                funds = fundRepository,
                recons = reconRepository,
                catalog = catalog,
                snapshots = snapshots,
                settings = settings,
                assembler = assembler,
                eventBuilder = eventBuilder,
                cashCoinIds = cashCoinIds,
            )

        }
    }

    /**
     * M9 备份恢复服务装配（T9.1–T9.3）：备份读取/快照/设置存储 + 恢复应用器 + 编排服务。
     * 与 M6/M7/M8 共享会话/目录/密钥仓库；快照仓库独立实例（同表无状态，先例 = SnapshotMarketQuotesService）；
     * 临时备份目录 = 数据目录/backups（全量覆盖前自动临时备份，PRD 5.2-6）。
     */
    class BackupServicesBundle internal constructor(
        val backupService: com.wuzhufolio.domain.backup.BackupService,
    ) {
        companion object {
            @Suppress("LongParameterList") // 装配袋（gate/设置/会话/目录/交换束/目录/日志），同其他 Bundle 释放链
            fun run(
                gate: DbGate,
                settings: SettingsRepository,
                sessions: ActiveSessionStore,
                catalog: SqlCoinCatalog,
                exchange: ExchangeServicesBundle,
                backupsDir: Path,
                logger: Logger,
            ): BackupServicesBundle {
                val crypto = CryptoService()
                val snapshots = PriceSnapshotRepository(gate)
                val assembler = com.wuzhufolio.data.ledger.LedgerEventAssembler(
                    catalog,
                    com.wuzhufolio.data.ledger.TransactionEventBuilder(catalog, snapshots),
                )
                val service: com.wuzhufolio.domain.backup.BackupService =
                    com.wuzhufolio.data.backup.DefaultBackupService(
                        sessions = sessions,
                        crypto = crypto,
                        gate = gate,
                        catalog = catalog,
                        settings = settings,
                        ledgerRead = com.wuzhufolio.data.backup.BackupLedgerReadStore(gate),
                        feeRules = FeeRuleRepository(gate),
                        apiKeys = exchange.apiKeyRepository,
                        assembler = assembler,
                        settingsStore = com.wuzhufolio.data.backup.BackupSettingsStore(gate),
                        snapshotStore = com.wuzhufolio.data.backup.BackupSnapshotStore(gate),
                        restoreStore = com.wuzhufolio.data.backup.BackupRestoreStore(crypto),
                        backupsDir = backupsDir,
                        logger = logger,
                    )
                return BackupServicesBundle(service)
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
