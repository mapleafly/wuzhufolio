package com.wuzhufolio.ui.i18n

import com.wuzhufolio.domain.proxy.ProxyMode
import com.wuzhufolio.domain.proxy.ProxyStatus
import com.wuzhufolio.domain.market.PriceSource

/**
 * 主壳文案（侧边栏/顶栏/状态栏/启动提示；M12 T12.1 + T12.2）。
 *
 * 状态栏数据源/额度/429 文案来源（M11 §6 遗留闭环）：
 * - 数据源徽章 = 上次成功刷新的行情来源与时刻（[dataSourceBadge]）；
 * - 同步状态 = 空闲 / 同步中 / 成功 N 条 / 失败（[syncStatus*]）；
 * - 额度与限流提示 = interaction.md §2.5（[quotaWarning] / [sharedRateLimitHint]）；
 * - 代理指示 = interaction.md §1.1 逐字文案（zh 档与领域层 [ProxyStatus.indicator] 等价，
 *   en 档由本表给出；领域层不承载 i18n，见模块记录 M12 §勘误）。
 */
interface ShellStrings {
    val appName: String
    val tagline: String

    // 侧边栏一级入口
    val navDashboard: String
    val navAssets: String
    val navTransactions: String
    val navFunds: String
    val navQuotes: String
    val navSettings: String
    val navGallery: String

    // 顶栏
    val manualSync: String
    val syncing: String
    val themeToggleTooltip: String
    /** 顶栏按钮短文案（宽度受限；状态栏用长文案 [refreshQuotes]）。 */
    val refreshQuotesShort: String
    val refreshingShort: String

    // 页面占位（仅开发期/未接线时可见）
    val placeholder: String

    // 启动安全提示
    val startupNoticeTitle: String
    val startupNoticeOk: String

    // 状态栏
    fun syncIdle(): String
    fun syncRunning(): String
    fun syncOk(added: Int): String
    fun syncFailed(reason: String): String
    fun proxyIndicator(status: ProxyStatus): String
    fun proxyTooltip(status: ProxyStatus): String
    fun dataSourceBadge(source: PriceSource?, at: String?): String
    fun dataSourceUnknown(): String
    fun refreshOk(cgConfigured: Boolean, fallback: Boolean): String
    fun quotaWarning(percent: Int): String
    fun sharedRateLimitHint(): String
    fun backupReminder(days: Long): String
    fun networkOffline(): String
    fun versionLabel(version: String): String
}

object ShellStringsZh : ShellStrings {
    override val appName = "WuZhuFolio"
    override val tagline = "本地 · 隐私 · 账本"
    override val navDashboard = "仪表盘"
    override val navAssets = "资产列表"
    override val navTransactions = "交易管理"
    override val navFunds = "资金管理"
    override val navQuotes = "行情"
    override val navSettings = "设置"
    override val navGallery = "组件走查"
    override val manualSync = "立即同步"
    override val syncing = "同步中…"
    override val themeToggleTooltip = "切换明/暗主题"
    override val refreshQuotesShort = "刷新行情"
    override val refreshingShort = "刷新中…"
    override val placeholder = "P4 模块页面占位（依赖顺序见 docs/tech/task-breakdown.md §2）"
    override val startupNoticeTitle = "安全提示"
    override val startupNoticeOk = "我知道了"
    override fun syncIdle() = "同步：空闲"
    override fun syncRunning() = "同步中…"
    override fun syncOk(added: Int) = "同步：成功（新增 " + added + " 条）"
    override fun syncFailed(reason: String) = "同步失败：" + reason
    override fun proxyIndicator(status: ProxyStatus) =
        if (status.mode == ProxyMode.SYSTEM) "代理：系统代理" else "直连"
    override fun proxyTooltip(status: ProxyStatus): String = when {
        status.mode == ProxyMode.SYSTEM -> "当前网络通过系统代理连接（" + status.endpoint?.label + "）"
        !status.enabled -> "系统代理已关闭——所有请求直连（设置 → 网络）"
        else -> "已开启系统代理，但系统未配置代理——当前直连"
    }
    override fun dataSourceBadge(source: PriceSource?, at: String?): String = when {
        source == null -> "数据源：未刷新"
        at == null -> "数据源：" + sourceLabel(source)
        else -> "数据源：" + sourceLabel(source) + " · 上次成功 " + at
    }
    override fun dataSourceUnknown() = "数据源：--"
    override fun refreshOk(cgConfigured: Boolean, fallback: Boolean): String = when {
        fallback -> "行情已刷新（数据源：CoinMarketCap 兜底）"
        cgConfigured -> "行情已刷新（CoinGecko 专属额度）"
        else -> "行情已刷新（CoinGecko 无 Key 公共 API）"
    }
    override fun quotaWarning(percent: Int) = "CoinGecko 额度已用 " + percent + "%，已达本月上限提示线（80%），已自动降频"
    override fun sharedRateLimitHint() = "CoinGecko 共享限流触发，已自动退避；建议注册免费个人 Key 获取专属额度"
    override fun backupReminder(days: Long) = "建议备份：距上次备份已 " + days + " 天"
    override fun networkOffline() = "网络断开"
    override fun versionLabel(version: String) = version

    private fun sourceLabel(source: PriceSource): String = when (source) {
        PriceSource.COINGECKO -> "CoinGecko"
        PriceSource.COINMARKETCAP -> "CoinMarketCap（兜底）"
    }
}

object ShellStringsEn : ShellStrings {
    override val appName = "WuZhuFolio"
    override val tagline = "Local · Private · Ledger"
    override val navDashboard = "Dashboard"
    override val navAssets = "Portfolio"
    override val navTransactions = "Transactions"
    override val navFunds = "Funds"
    override val navQuotes = "Markets"
    override val navSettings = "Settings"
    override val navGallery = "Components"
    override val manualSync = "Sync now"
    override val syncing = "Syncing…"
    override val themeToggleTooltip = "Toggle light/dark theme"
    override val refreshQuotesShort = "Refresh"
    override val refreshingShort = "Refreshing…"
    override val placeholder = "Placeholder — module page pending (see docs/tech/task-breakdown.md §2)"
    override val startupNoticeTitle = "Security notice"
    override val startupNoticeOk = "Got it"
    override fun syncIdle() = "Sync: idle"
    override fun syncRunning() = "Syncing…"
    override fun syncOk(added: Int) = "Sync: ok (" + added + " new)"
    override fun syncFailed(reason: String) = "Sync failed: " + reason
    override fun proxyIndicator(status: ProxyStatus) =
        if (status.mode == ProxyMode.SYSTEM) "Proxy: system" else "Direct"
    override fun proxyTooltip(status: ProxyStatus): String = when {
        status.mode == ProxyMode.SYSTEM -> "Traffic goes through the system proxy (" + status.endpoint?.label + ")"
        !status.enabled -> "System proxy is off — all requests connect directly (Settings → Network)"
        else -> "System proxy is on, but no proxy is configured — connecting directly"
    }
    override fun dataSourceBadge(source: PriceSource?, at: String?): String = when {
        source == null -> "Source: not refreshed"
        at == null -> "Source: " + sourceLabel(source)
        else -> "Source: " + sourceLabel(source) + " · last ok " + at
    }
    override fun dataSourceUnknown() = "Source: --"
    override fun refreshOk(cgConfigured: Boolean, fallback: Boolean): String = when {
        fallback -> "Prices refreshed (source: CoinMarketCap fallback)"
        cgConfigured -> "Prices refreshed (CoinGecko personal quota)"
        else -> "Prices refreshed (CoinGecko public API, no key)"
    }
    override fun quotaWarning(percent: Int) =
        "CoinGecko quota at " + percent + "% — monthly warning line (80%) reached, refresh slowed down"
    override fun sharedRateLimitHint() =
        "CoinGecko shared rate limit hit, backing off; register a free personal key for a dedicated quota"
    override fun backupReminder(days: Long) = "Backup recommended: " + days + " days since last backup"
    override fun networkOffline() = "Network offline"
    override fun versionLabel(version: String) = version

    private fun sourceLabel(source: PriceSource): String = when (source) {
        PriceSource.COINGECKO -> "CoinGecko"
        PriceSource.COINMARKETCAP -> "CoinMarketCap (fallback)"
    }
}

val shellStrings: ShellStrings get() = if (I18n.isZh) ShellStringsZh else ShellStringsEn
