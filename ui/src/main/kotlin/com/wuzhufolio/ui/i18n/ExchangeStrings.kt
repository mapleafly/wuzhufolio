package com.wuzhufolio.ui.i18n

/**
 * 交易所 / API 管理模块文案（T6.4 页面 → M12 T12.4 zh/en 双档）。
 *
 * 口径与来源（zh 档逐字基准 = P1 原型 wuzhufolio-light.html「API 管理」页与添加弹窗 + PRD 4.1）：
 * - 安全文案逐字保留、**不得改写**：只读权限提示 [hintReadOnly]、字段级加密提示 [hintEncrypted]、
 *   关联当前账户提示 [hintAccount]、保存后清理剪贴板提示 [saveAndSyncToast]、
 *   密钥失效 B2 文案 [errKeyInvalidB2]（PRD「统一异常处理」B2）；
 * - 参数化文案一律走本接口的函数（`+` 拼接 / `String.format` 只在调用点外侧使用，见 CommonStrings 头注规则 3）；
 * - 三条既有 `%d` 模板（[firstSyncDoneTemplate] / [syncDoneTemplate] / [syncPartialTemplate]）保留为模板属性，
 *   以维持 `ApiCopy` 既有成员签名不变；其参数化函数 [firstSyncDone] / [syncDone] / [syncPartial] 为正式调用入口；
 * - 语言中立的字段名与掩码（[apiKeyLabel] / [secretLabel] / [keyMask] / [secretPlaceholder]）zh/en 同形，
 *   仅为「展示文案统一走目录」而入表，不要在调用点就地硬编码。
 *
 * 通用按钮（保存/移除/编辑）不另立词条，直接用 [CommonStrings]（跨模块公共文案）。
 */
interface ExchangeStrings {

    // ---- 设置页行 / 页面标题 ----

    val groupTitle: String
    val groupSub: String
    val pageSub: String

    /** API 管理分组内的已保存密钥列表标题（ApiManagementSection 就地文案）。 */
    val savedKeysTitle: String

    val statusConfigured: String
    val statusUnconfigured: String

    /** 密钥掩码（语言中立符号）。 */
    val keyMask: String

    val lastSyncNever: String
    val lastSyncPrefix: String

    // ---- 列表工具栏 / 页面级同步 ----

    val addButton: String
    val syncNow: String
    val syncing: String
    val syncAllButton: String
    val syncAllEmpty: String
    val emptyHint: String

    /** 页面级「立即同步（全部密钥）」部分失败汇总（`+` 拼接 → 函数）。 */
    fun syncAllPartial(failed: Int, newTrades: Int): String

    /** 页面级「立即同步（全部密钥）」全部成功汇总（`+` 拼接 → 函数）。 */
    fun syncAllDone(keys: Int, newTrades: Int): String

    // ---- 最近同步记录 ----

    val recentLogsTitle: String
    val noLogs: String

    /** 单条同步记录行：`时间  状态 · 新增 N  消息`（buildString → 函数）。 */
    fun syncLogLine(time: String, ok: Boolean, newTrades: Int, message: String): String

    // ---- 添加/编辑弹窗（原型 openApiAdd 逐字口径） ----

    val addTitle: String
    val editTitle: String
    val nameLabel: String
    val namePlaceholder: String
    val exchangeLabel: String

    /** 交易所取值（zh 用全角括号，en 用半角括号）。 */
    val exchangeValue: String

    val apiKeyLabel: String
    val apiKeyPlaceholder: String
    val secretLabel: String

    /** Secret 输入框掩码占位（语言中立符号）。 */
    val secretPlaceholder: String

    val hintReadOnly: String
    val hintEncrypted: String
    val hintAccount: String
    val testButton: String
    val testPassed: String
    val saveAndSyncToast: String

    /** 保存并首次同步完成的完整 toast（前段 [saveAndSyncToast] + 结果摘要合并，单条 toast）。 */
    fun saveAndSyncWithResult(message: String): String

    /** 「首次同步完成 · 新增 %d」模板（保留供 ApiCopy 既有成员读取）。 */
    val firstSyncDoneTemplate: String

    /** 首次同步完成（正式调用入口）。 */
    fun firstSyncDone(added: Int): String = firstSyncDoneTemplate.format(added)

    val removedToast: String
    val keyUpdatedToast: String

    /** 「同步完成 · 新增 %d · 去重跳过 %d」模板（保留供 ApiCopy 既有成员读取）。 */
    val syncDoneTemplate: String

    /** 单 key 同步完成（正式调用入口）。 */
    fun syncDone(newTrades: Int, duplicatesSkipped: Int): String =
        syncDoneTemplate.format(newTrades, duplicatesSkipped)

    /** 「同步完成（部分）· 新增 %d · 剩余 %d 个交易对下轮续传」模板（保留供 ApiCopy 既有成员读取）。 */
    val syncPartialTemplate: String

    /** 单 key 部分同步完成（正式调用入口）。 */
    fun syncPartial(newTrades: Int, queuedSymbols: Int): String =
        syncPartialTemplate.format(newTrades, queuedSymbols)

    // ---- 校验 ----

    val errNameEmpty: String
    val errKeyEmpty: String
    val errSecretEmpty: String
    val errUpdateCredsPair: String
    val errDuplicate: String
    val errGeneric: String

    // ---- 编辑回显（安全口径：密钥不回显明文/掩码，留空 = 保持不变） ----

    val editKeyPlaceholder: String
    val editSecretPlaceholder: String

    // ---- ExchangeError → 文案（api-contracts §4 错误码映射） ----

    /** 密钥失效（B2，PRD 逐字）。 */
    val errKeyInvalidB2: String
    val errRateLimited: String
    val errTimestampSkew: String
    val errNetwork: String

    /** HTTP 错误（含状态码）。 */
    fun errHttp(code: Int): String

    val errInternal: String
}

object ExchangeStringsZh : ExchangeStrings {
    override val groupTitle = "API 管理"
    override val groupSub = "Binance 只读密钥（字段级加密存储 · 自动增量同步）"
    override val pageSub = "添加交易所只读 API 后自动同步新成交（增量去重）· 仅返回最近 500 条，更早请 CSV 导入"
    override val savedKeysTitle = "已保存 API 密钥"
    override val statusConfigured = "已配置"
    override val statusUnconfigured = "未配置"
    override val keyMask = "••••••••"
    override val lastSyncNever = "从未同步"
    override val lastSyncPrefix = "最后同步"
    override val addButton = "＋ 添加 API（Binance 只读）"
    override val syncNow = "立即同步"
    override val syncing = "同步中…"
    override val syncAllButton = "立即同步（全部密钥）"
    override val syncAllEmpty = "尚未添加 API 密钥：请先添加只读密钥，再执行同步"
    override val emptyHint = "尚未添加 API 密钥 · 添加后每个密钥行内提供「立即同步」，也可用下方「立即同步（全部密钥）」"
    override fun syncAllPartial(failed: Int, newTrades: Int) =
        "同步完成（部分失败 " + failed + " 个密钥）· 新增 " + newTrades
    override fun syncAllDone(keys: Int, newTrades: Int) =
        "同步完成 · " + keys + " 个密钥 · 新增 " + newTrades
    override val recentLogsTitle = "最近同步记录"
    override val noLogs = "暂无同步记录"
    override fun syncLogLine(time: String, ok: Boolean, newTrades: Int, message: String) =
        time + "  " + (if (ok) "成功" else "失败") + " · 新增 " + newTrades + "  " + message
    override val addTitle = "添加 API（Binance 只读）"
    override val editTitle = "编辑 API（Binance 只读）"
    override val nameLabel = "别名"
    override val namePlaceholder = "如：币安主号"
    override val exchangeLabel = "交易所"
    override val exchangeValue = "Binance（MVP）"
    override val apiKeyLabel = "API Key"
    override val apiKeyPlaceholder = "输入只读 API Key"
    override val secretLabel = "Secret Key"
    override val secretPlaceholder = "••••••••"
    override val hintReadOnly = "· 仅需要只读权限（创建教程：交易所 API 管理页，浏览器打开）"
    override val hintEncrypted = "· 密钥字段级加密存储（AES-256-GCM），仅存本地、不上传"
    override val hintAccount = "· 该 API 将关联当前登录账户"
    override val testButton = "测试请求"
    override val testPassed = "测试请求通过 · 已用该密钥获取账户信息（只读）"
    override val saveAndSyncToast = "密钥已加密保存 · 请清理系统剪贴板 · 立即执行首次同步（增量去重）…"
    override fun saveAndSyncWithResult(message: String) = saveAndSyncToast + " 结果：" + message
    override val firstSyncDoneTemplate = "首次同步完成 · 新增 %d"
    override val removedToast = "已移除 API 密钥"
    override val keyUpdatedToast = "已更新 · 密钥已重新加密保存"
    override val syncDoneTemplate = "同步完成 · 新增 %d · 去重跳过 %d"
    override val syncPartialTemplate = "同步完成（部分）· 新增 %d · 剩余 %d 个交易对下轮续传"
    override val errNameEmpty = "请输入别名"
    override val errKeyEmpty = "请输入 API Key"
    override val errSecretEmpty = "请输入 Secret Key"
    override val errUpdateCredsPair = "换密钥需同时填写 API Key 与 Secret Key（留空 = 仅更新别名）"
    override val errDuplicate = "同名 API 已存在（同一交易所内别名需唯一）"
    override val errGeneric = "操作失败，请重试"
    override val editKeyPlaceholder = "留空 = 保持不变"
    override val editSecretPlaceholder = "留空 = 保持不变"
    override val errKeyInvalidB2 = "Binance API 密钥已失效，请检查或更新（B2）"
    override val errRateLimited = "Binance 限流触发，请稍后重试"
    override val errTimestampSkew = "Binance 时间偏差，请稍后重试"
    override val errNetwork = "网络不可达 Binance，请检查网络后重试"
    override fun errHttp(code: Int) = "Binance 请求失败（HTTP " + code + "）"
    override val errInternal = "内部错误：请查看日志后重试"
}

object ExchangeStringsEn : ExchangeStrings {
    override val groupTitle = "API management"
    override val groupSub = "Binance read-only keys (field-level encrypted storage · automatic incremental sync)"
    override val pageSub =
        "Read-only exchange APIs sync new fills automatically (incremental dedup) · only the latest 500 " +
            "are returned, import older ones via CSV"
    override val savedKeysTitle = "Saved API keys"
    override val statusConfigured = "Configured"
    override val statusUnconfigured = "Not configured"
    override val keyMask = "••••••••"
    override val lastSyncNever = "Never synced"
    override val lastSyncPrefix = "Last sync"
    override val addButton = "+ Add API (Binance read-only)"
    override val syncNow = "Sync now"
    override val syncing = "Syncing…"
    override val syncAllButton = "Sync now (all keys)"
    override val syncAllEmpty = "No API key yet: add a read-only key first, then sync"
    override val emptyHint =
        "No API key yet · once added, each key row offers “Sync now”; you can also use “Sync now (all keys)” below"
    override fun syncAllPartial(failed: Int, newTrades: Int) =
        "Sync complete (partial: " + failed + " key(s) failed) · " + newTrades + " new"
    override fun syncAllDone(keys: Int, newTrades: Int) =
        "Sync complete · " + keys + " key(s) · " + newTrades + " new"
    override val recentLogsTitle = "Recent sync logs"
    override val noLogs = "No sync logs"
    override fun syncLogLine(time: String, ok: Boolean, newTrades: Int, message: String) =
        time + "  " + (if (ok) "OK" else "FAILED") + " · " + newTrades + " new  " + message
    override val addTitle = "Add API (Binance read-only)"
    override val editTitle = "Edit API (Binance read-only)"
    override val nameLabel = "Alias"
    override val namePlaceholder = "e.g. Binance main account"
    override val exchangeLabel = "Exchange"
    override val exchangeValue = "Binance (MVP)"
    override val apiKeyLabel = "API Key"
    override val apiKeyPlaceholder = "Enter the read-only API Key"
    override val secretLabel = "Secret Key"
    override val secretPlaceholder = "••••••••"
    override val hintReadOnly =
        "· Read-only permission is all that is needed (how-to: open the exchange's API management " +
            "page in a browser)"
    override val hintEncrypted =
        "· Keys are stored with field-level encryption (AES-256-GCM), local only, never uploaded"
    override val hintAccount = "· This API will be linked to the account currently signed in"
    override val testButton = "Test request"
    override val testPassed = "Test request passed · account info was fetched with this key (read-only)"
    override val saveAndSyncToast =
        "Key saved with encryption · please clear the system clipboard · running the first sync now " +
            "(incremental dedup)…"
    override fun saveAndSyncWithResult(message: String) = saveAndSyncToast + " Result: " + message
    override val firstSyncDoneTemplate = "First sync complete · %d new"
    override val removedToast = "API key removed"
    override val keyUpdatedToast = "Updated · key re-saved with encryption"
    override val syncDoneTemplate = "Sync complete · %d new · %d duplicates skipped"
    override val syncPartialTemplate = "Sync complete (partial) · %d new · %d symbol(s) queued for the next run"
    override val errNameEmpty = "Enter an alias"
    override val errKeyEmpty = "Enter the API Key"
    override val errSecretEmpty = "Enter the Secret Key"
    override val errUpdateCredsPair =
        "Changing keys requires both the API Key and the Secret Key (leave blank = update the alias only)"
    override val errDuplicate = "An API with this alias already exists (aliases must be unique per exchange)"
    override val errGeneric = "Operation failed, please retry"
    override val editKeyPlaceholder = "Leave blank to keep unchanged"
    override val editSecretPlaceholder = "Leave blank to keep unchanged"
    override val errKeyInvalidB2 = "The Binance API key is no longer valid, please check or update it (B2)"
    override val errRateLimited = "Binance rate limit reached, please retry later"
    override val errTimestampSkew = "Binance timestamp skew detected, please retry later"
    override val errNetwork = "Binance is unreachable, check your network and retry"
    override fun errHttp(code: Int) = "Binance request failed (HTTP " + code + ")"
    override val errInternal = "Internal error: check the logs and retry"
}

val exchangeStrings: ExchangeStrings get() = if (I18n.isZh) ExchangeStringsZh else ExchangeStringsEn
