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
import com.wuzhufolio.data.settings.SettingsRepository
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

    /** 运行期装配结果：窗口关闭时 [close] 释放数据库连接与钥匙串会话。 */
    class Runtime internal constructor(
        val db: WzDatabase,
        val gate: DbGate,
        val settings: SettingsRepository,
        val uiState: UiState,
        private val keyring: MasterKeyStore?,
    ) {
        fun close() {
            runCatching { keyring?.close() }
            db.close()
        }
    }

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
        val hello = HelloChain(db, settings, logger).run()

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
            keyring = if (report.backend == KeyStorageBackend.OS_KEYCHAIN) keyring else null,
        )
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
