package com.wuzhufolio.data.hello

import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * T0.4 hello 链路：空界面启动 -> 迁移建库 -> 读设置 -> 打一条脱敏日志。
 * 是 P3 的端到端最小验证链，也是 M1 起真实启动引导（bootstrap）的雏形。
 */
class HelloChain(
    private val database: WzDatabase,
    private val settings: SettingsRepository,
    private val logger: Logger = LoggerFactory.getLogger(HelloChain::class.java),
) {

    data class Result(
        val schemaVersion: Int,
        val settings: Map<String, String>,
        val theme: ThemeMode,
        val pnlScheme: PnlColorScheme,
    )

    fun run(): Result {
        val version = database.migrateToLatest()
        val all = settings.getAllGlobal()
        val summary = all.entries.joinToString(" ") { it.key + "=" + it.value }
        // 演示脱敏：键名含敏感词的设置值与长密钥串均不得进入日志原文（PRD §6）
        logger.info(
            LogRedactor.redact(
                "hello-chain ok | schema_version=" + version +
                    " | settings(count=" + all.size + "): " + summary +
                    " | market_api_key=CG-DEMO-0123456789abcdef0123456789abcdef",
            ),
        )
        return Result(
            schemaVersion = version,
            settings = all,
            theme = ThemeMode.fromStorage(all["theme"]),
            pnlScheme = PnlColorScheme.fromStorage(all["pnl_scheme"]),
        )
    }
}
