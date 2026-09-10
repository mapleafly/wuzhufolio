package com.wuzhufolio.ui.i18n

/**
 * 数据管理（备份 / 恢复 / CSV 明文导出）文案（M9 T9.4 · M12 T12.4 zh/en 双档）。
 *
 * 目录约定（与 [CommonStrings] 头部四条约定的备份模块落地）：
 * 1. 接口 + Zh/En 两实现 + `backupStrings` 访问器；
 * 2. 既有 `object BackupCopy` 常量一律改写为动态取值属性（`val X: String get() = backupStrings.x`），
 *    调用点不变；
 * 3. **前缀型**文案（`lastBackupPrefix` 等，zh 以「：」收尾）保留前缀成员，由调用点拼值——
 *    这是既有契约（`BackupViewModel` 侧同样按前缀拼装，不属本模块可改范围）；
 *    **整句计数**类文案（导入摘要 / 摘要条数 / 合并预览）改为接口函数，中英语序各自成立，
 *    调用点不再拼装字面量；
 * 4. 数值/时间一律走 [WzFormat]，本表只承载文字。
 *
 * 安全口径（D24「备份文件密码独立设置不回填」）：备份导出密码由用户**独立设置**——
 * 不回填当前账户密码、禁止空密码；[exportPwdNote] 是逐字安全说明，en 档必须逐条完整对应，
 * 不得简写、不得改回「默认填入账户密码」。恢复向导侧 [overwriteConfirmLabel] /
 * [modeOverwriteDesc]（全量覆盖二次确认）与 [exportSensitiveWarning]（敏感数据提示）同理。
 */
interface BackupStrings {

    // ---- 分区 ----
    val backupGroupTitle: String
    val restoreGroupTitle: String
    val csvGroupTitle: String
    val backupButton: String
    val restoreButton: String
    val csvTransactions: String
    val csvFunds: String
    val csvHoldings: String
    val lastBackupPrefix: String
    val lastRestorePrefix: String
    val neverDone: String
    val csvNote: String

    // ---- 备份弹窗 ----
    val exportTitle: String
    val exportPwdLabel: String
    val exportPwdConfirmLabel: String
    val exportPwdRule: String
    val exportSensitiveWarning: String
    val exportPwdNote: String
    val exportConfirm: String
    val exportBusy: String
    val exportErrorPwd: String
    val exportErrorMismatch: String
    val exportSuccessPrefix: String
    val exportFailedPrefix: String

    // ---- 恢复向导 ----
    val restoreTitle: String
    val restorePickButton: String
    val restorePickHint: String
    val summaryVersion: String
    val summaryApp: String
    val summaryExportedAt: String
    val summaryRange: String
    val summaryCountsLabel: String
    val countsTx: String
    val countsFlow: String
    val countsRecon: String
    val countsFee: String
    val countsKeys: String
    val countsSnapshots: String
    val restorePwdLabel: String
    val restoreUnlock: String
    val restoreVerifying: String
    val restoreNextMode: String
    val errWrongPassword: String
    val errUnsupported: String
    val errMalformed: String
    val errGeneric: String

    // ---- 模式选择 ----
    val modeTitle: String
    val modeMerge: String
    val modeMergeDesc: String
    val modeOverwrite: String
    val modeOverwriteDesc: String
    val overwriteConfirmLabel: String
    val mergePreviewPrefix: String
    val mergePreviewDup: String
    val mergePreviewMissing: String
    val restoreExecute: String
    val restoreRunning: String

    // ---- 结果 ----
    val resultTitle: String
    val resultImportedPrefix: String
    val resultSkippedPrefix: String
    val resultMissingPrefix: String
    val resultTempBackupPrefix: String
    val resultAnomalousPrefix: String
    val resultAnomalousExistingPrefix: String
    val resultAnomalousExistingNote: String
    val resultNone: String
    val resultDone: String
    val restoreSuccessToast: String

    // ---- CSV ----
    val csvSuccessPrefix: String
    val csvFailedPrefix: String

    // ---- 整句（计数/预览；调用点不拼装） ----

    /** 恢复结果导入计数整句（交易/资金/校准/费率/密钥/快照）。 */
    @Suppress("LongParameterList") // 六类计数一次传全（文案整句由目录决定语序）
    fun importedSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String

    /** 备份摘要「记录条数」整句（同一组计数，分隔符与结果页不同）。 */
    @Suppress("LongParameterList") // 同上：同一组计数
    fun recordCountsSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String

    /** 增量合并预览整句（将新增 N 条；已存在跳过 N 条；缺失币种跳过 N 条）。 */
    fun mergePreview(inserts: Int, duplicates: Int, missingCoins: Int): String
}

object BackupStringsZh : BackupStrings {
    // ---- 分区 ----
    override val backupGroupTitle = "备份"
    override val restoreGroupTitle = "恢复"
    override val csvGroupTitle = "明文导出（CSV，不含 API 密钥）"
    override val backupButton = "备份数据"
    override val restoreButton = "恢复数据"
    override val csvTransactions = "导出交易记录"
    override val csvFunds = "导出资金流水"
    override val csvHoldings = "导出持仓汇总"
    override val lastBackupPrefix = "上次备份："
    override val lastRestorePrefix = "上次恢复："
    override val neverDone = "—"
    override val csvNote = "CSV 为明文导出（数据主权），仅含交易 / 资金 / 持仓汇总，不含任何 API 密钥；" +
        "交易 CSV 与导入模板同列，可直接回导。"

    // ---- 备份弹窗 ----
    override val exportTitle = "备份数据"
    override val exportPwdLabel = "备份文件密码"
    override val exportPwdConfirmLabel = "确认备份文件密码"
    override val exportPwdRule = "至少 8 位且包含字母与数字（与账户密码同一最低门槛）"
    override val exportSensitiveWarning =
        "备份文件包含 API 密钥等敏感数据，请妥善保管。"
    override val exportPwdNote =
        "为保护安全，应用不会回填或存储您的账户密码，请为此备份设置独立密码并妥善保管；" +
            "任何设备导入该备份只需此文件密码（与账户密码无关）。"
    override val exportConfirm = "选择保存位置并生成"
    override val exportBusy = "生成中…"
    override val exportErrorPwd = "备份密码须至少 8 位且包含字母与数字"
    override val exportErrorMismatch = "两次输入的密码不一致"
    override val exportSuccessPrefix = "备份完成："
    override val exportFailedPrefix = "备份失败："

    // ---- 恢复向导 ----
    override val restoreTitle = "恢复数据"
    override val restorePickButton = "选择 .cpro 文件"
    override val restorePickHint = "选择此前导出的 .cpro 备份文件（恢复只依赖备份文件密码）。"
    override val summaryVersion = "格式版本："
    override val summaryApp = "应用版本："
    override val summaryExportedAt = "导出时间："
    override val summaryRange = "数据时间范围："
    override val summaryCountsLabel = "记录条数："
    override val countsTx = "交易 "
    override val countsFlow = "资金 "
    override val countsRecon = "校准 "
    override val countsFee = "费率 "
    override val countsKeys = "API 密钥 "
    override val countsSnapshots = "快照 "
    override val restorePwdLabel = "备份文件密码"
    override val restoreUnlock = "验证密码"
    override val restoreVerifying = "验证中…"
    override val restoreNextMode = "下一步"
    override val errWrongPassword = "密码错误或文件损坏，请核对备份文件密码后重试。"
    override val errUnsupported = "备份格式版本较新，请升级应用后再导入。"
    override val errMalformed = "不是有效的 .cpro 备份文件（或文件已损坏）。"
    override val errGeneric = "恢复失败："

    // ---- 模式选择 ----
    override val modeTitle = "选择导入方式"
    override val modeMerge = "增量合并（默认）"
    override val modeMergeDesc = "按 记录 uuid → 交易所+订单号 → 快照幂等 合并，不删除现有记录。"
    override val modeOverwrite = "全量覆盖"
    override val modeOverwriteDesc = "清空当前账户的业务数据后导入；全局行情缓存保留；覆盖前自动生成临时备份。"
    override val overwriteConfirmLabel = "我已知晓全量覆盖将替换当前账户的全部业务数据"
    override val mergePreviewPrefix = "将新增："
    override val mergePreviewDup = "条；已存在跳过："
    override val mergePreviewMissing = "条；缺失币种跳过："
    override val restoreExecute = "开始恢复"
    override val restoreRunning = "恢复中…"

    // ---- 结果 ----
    override val resultTitle = "恢复完成"
    override val resultImportedPrefix = "已导入："
    override val resultSkippedPrefix = "重复跳过："
    override val resultMissingPrefix = "缺失币种跳过："
    override val resultTempBackupPrefix = "覆盖前临时备份："
    override val resultAnomalousPrefix = "本次导入新产生持仓异常（导入路径例外，可补录增资/校准消除）："
    override val resultAnomalousExistingPrefix = "账户既存持仓异常（非本次导入造成，可补录增资/校准消除）："
    override val resultAnomalousExistingNote =
        "既存异常来自恢复前的账本状态（如历史导入负持仓），本次恢复未改变；明细见资金列表「持仓异常」标记。"
    override val resultNone = "无"
    override val resultDone = "完成"
    override val restoreSuccessToast = "恢复完成，数据已重算。"

    // ---- CSV ----
    override val csvSuccessPrefix = "已导出："
    override val csvFailedPrefix = "导出失败："

    // ---- 整句 ----
    override fun importedSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = resultImportedPrefix +
        countsTx + transactions + " / " +
        countsFlow + capitalFlows + " / " +
        countsRecon + reconciliationRecords + " / " +
        countsFee + feeRules + " / " +
        countsKeys + apiKeys + " / " +
        countsSnapshots + priceSnapshots

    override fun recordCountsSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = summaryCountsLabel +
        countsTx + transactions + " · " +
        countsFlow + capitalFlows + " · " +
        countsRecon + reconciliationRecords + " · " +
        countsFee + feeRules + " · " +
        countsKeys + apiKeys + " · " +
        countsSnapshots + priceSnapshots

    override fun mergePreview(inserts: Int, duplicates: Int, missingCoins: Int): String =
        mergePreviewPrefix + inserts + mergePreviewDup + duplicates + mergePreviewMissing + missingCoins + "条"
}

object BackupStringsEn : BackupStrings {
    // ---- Sections ----
    override val backupGroupTitle = "Backup"
    override val restoreGroupTitle = "Restore"
    override val csvGroupTitle = "Plain-text export (CSV, no API keys)"
    override val backupButton = "Back up data"
    override val restoreButton = "Restore data"
    override val csvTransactions = "Export transactions"
    override val csvFunds = "Export capital flows"
    override val csvHoldings = "Export holdings summary"
    override val lastBackupPrefix = "Last backup: "
    override val lastRestorePrefix = "Last restore: "
    override val neverDone = "—"
    override val csvNote = "CSV files are plain-text exports (data sovereignty) covering only transactions / " +
        "capital flows / holdings summary, and never any API keys; the transactions CSV uses the same columns " +
        "as the import template and can be re-imported directly."

    // ---- Backup dialog ----
    override val exportTitle = "Back up data"
    override val exportPwdLabel = "Backup file password"
    override val exportPwdConfirmLabel = "Confirm backup file password"
    override val exportPwdRule = "At least 8 characters, containing both letters and digits (same minimum as the account password)"
    override val exportSensitiveWarning =
        "The backup file contains sensitive data such as API keys — keep it safe."
    // D24 逐字对应：不回填、不存储账户密码；备份密码独立设置；跨设备导入只依赖文件密码（与账户密码无关）。
    override val exportPwdNote =
        "For your security, the app neither pre-fills nor stores your account password. Set an independent " +
            "password for this backup and keep it safe; importing this backup on any device requires only that " +
            "file password (it is unrelated to your account password)."
    override val exportConfirm = "Choose a location and generate"
    override val exportBusy = "Generating…"
    override val exportErrorPwd = "The backup password must be at least 8 characters and contain both letters and digits"
    override val exportErrorMismatch = "The two passwords do not match"
    override val exportSuccessPrefix = "Backup complete: "
    override val exportFailedPrefix = "Backup failed: "

    // ---- Restore wizard ----
    override val restoreTitle = "Restore data"
    override val restorePickButton = "Choose a .cpro file"
    override val restorePickHint = "Choose a .cpro backup file exported earlier (a restore depends only on the backup file password)."
    override val summaryVersion = "Format version: "
    override val summaryApp = "App version: "
    override val summaryExportedAt = "Exported at: "
    override val summaryRange = "Data time range: "
    override val summaryCountsLabel = "Record counts: "
    override val countsTx = "Transactions "
    override val countsFlow = "Capital flows "
    override val countsRecon = "Reconciliations "
    override val countsFee = "Fee rules "
    override val countsKeys = "API keys "
    override val countsSnapshots = "Snapshots "
    override val restorePwdLabel = "Backup file password"
    override val restoreUnlock = "Verify password"
    override val restoreVerifying = "Verifying…"
    override val restoreNextMode = "Next"
    override val errWrongPassword = "Wrong password or corrupted file — check the backup file password and try again."
    override val errUnsupported = "This backup uses a newer format version — update the app before importing."
    override val errMalformed = "Not a valid .cpro backup file (or the file is corrupted)."
    override val errGeneric = "Restore failed: "

    // ---- Import mode ----
    override val modeTitle = "Choose an import mode"
    override val modeMerge = "Incremental merge (default)"
    override val modeMergeDesc = "Merges idempotently by record uuid → exchange + order id → snapshot; existing records are never deleted."
    override val modeOverwrite = "Full overwrite"
    override val modeOverwriteDesc = "Clears all business data in the current account and then imports; the global price cache is kept; a temporary backup is generated automatically before overwriting."
    override val overwriteConfirmLabel = "I understand that a full overwrite replaces all business data in the current account"
    override val mergePreviewPrefix = "Will add: "
    override val mergePreviewDup = "; skipped (already exists): "
    override val mergePreviewMissing = "; skipped (missing coin): "
    override val restoreExecute = "Start restore"
    override val restoreRunning = "Restoring…"

    // ---- Result ----
    override val resultTitle = "Restore complete"
    override val resultImportedPrefix = "Imported: "
    override val resultSkippedPrefix = "Duplicates skipped: "
    override val resultMissingPrefix = "Missing coins skipped: "
    override val resultTempBackupPrefix = "Temporary backup before overwrite: "
    override val resultAnomalousPrefix = "Holdings anomalies newly produced by this import (import-path exception; can be cleared by adding a capital injection or a reconciliation): "
    override val resultAnomalousExistingPrefix = "Pre-existing holdings anomalies in the account (not caused by this import; can be cleared by adding a capital injection or a reconciliation): "
    override val resultAnomalousExistingNote =
        "The pre-existing anomalies come from the ledger state before this restore (for example a negative holding " +
            "left by an earlier import) and were not changed by this restore; details are marked as \"holdings " +
            "anomaly\" in the funds list."
    override val resultNone = "None"
    override val resultDone = "Done"
    override val restoreSuccessToast = "Restore complete — data recalculated."

    // ---- CSV ----
    override val csvSuccessPrefix = "Exported: "
    override val csvFailedPrefix = "Export failed: "

    // ---- Whole sentences ----
    override fun importedSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = resultImportedPrefix +
        count(transactions, "transaction") + " / " +
        count(capitalFlows, "capital flow") + " / " +
        count(reconciliationRecords, "reconciliation") + " / " +
        count(feeRules, "fee rule") + " / " +
        count(apiKeys, "API key") + " / " +
        count(priceSnapshots, "snapshot")

    override fun recordCountsSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = summaryCountsLabel +
        count(transactions, "transaction") + " · " +
        count(capitalFlows, "capital flow") + " · " +
        count(reconciliationRecords, "reconciliation") + " · " +
        count(feeRules, "fee rule") + " · " +
        count(apiKeys, "API key") + " · " +
        count(priceSnapshots, "snapshot")

    override fun mergePreview(inserts: Int, duplicates: Int, missingCoins: Int): String =
        mergePreviewPrefix + inserts + mergePreviewDup + duplicates + mergePreviewMissing + missingCoins

    /** 英语单复数（0 与 >1 用复数；仅 en 档需要，zh 档量词无变化）。 */
    private fun count(n: Int, singular: String): String =
        n.toString() + " " + if (n == 1) singular else singular + "s"
}

val backupStrings: BackupStrings get() = if (I18n.isZh) BackupStringsZh else BackupStringsEn
