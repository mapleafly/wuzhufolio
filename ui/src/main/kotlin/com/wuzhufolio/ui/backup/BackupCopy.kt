package com.wuzhufolio.ui.backup

import com.wuzhufolio.ui.i18n.backupStrings

/**
 * 数据管理（备份恢复）文案单源（M9 · T9.4 · ia.md §2.15 / PRD §9.9 / interaction §3 / flows §6）。
 * 文案口径与原型数据管理走查一致；错误文案映射服务层异常（CproDecodeException 三态）。
 *
 * M12 T12.4 i18n：本对象降级为**模块取词门面**——成员一律动态取值
 * （`val X: String get() = backupStrings.x`，每次读取按当前语言解析），调用点不变；
 * zh/en 真源见 [com.wuzhufolio.ui.i18n.BackupStrings]。
 * 前缀型成员（`*_PREFIX`）保持原契约由调用点拼值；整句计数/预览改为函数，调用点不再拼字面量。
 */
@Suppress("TooManyFunctions") // 备份域文案门面（原 const 成员一一保留，调用点零改动）
object BackupCopy {

    // ---- 分区 ----
    val BACKUP_GROUP_TITLE: String get() = backupStrings.backupGroupTitle
    val RESTORE_GROUP_TITLE: String get() = backupStrings.restoreGroupTitle
    val CSV_GROUP_TITLE: String get() = backupStrings.csvGroupTitle
    val BACKUP_BUTTON: String get() = backupStrings.backupButton
    val RESTORE_BUTTON: String get() = backupStrings.restoreButton
    val CSV_TRANSACTIONS: String get() = backupStrings.csvTransactions
    val CSV_FUNDS: String get() = backupStrings.csvFunds
    val CSV_HOLDINGS: String get() = backupStrings.csvHoldings
    val LAST_BACKUP_PREFIX: String get() = backupStrings.lastBackupPrefix
    val LAST_RESTORE_PREFIX: String get() = backupStrings.lastRestorePrefix
    val NEVER_DONE: String get() = backupStrings.neverDone
    val CSV_NOTE: String get() = backupStrings.csvNote

    // ---- 备份弹窗 ----
    val EXPORT_TITLE: String get() = backupStrings.exportTitle
    val EXPORT_PWD_LABEL: String get() = backupStrings.exportPwdLabel
    val EXPORT_PWD_CONFIRM_LABEL: String get() = backupStrings.exportPwdConfirmLabel
    val EXPORT_PWD_RULE: String get() = backupStrings.exportPwdRule
    val EXPORT_SENSITIVE_WARNING: String get() = backupStrings.exportSensitiveWarning

    /** D24：备份密码独立设置（不回填账户密码、禁止空密码）——逐字安全说明。 */
    val EXPORT_PWD_NOTE: String get() = backupStrings.exportPwdNote
    val EXPORT_CONFIRM: String get() = backupStrings.exportConfirm
    val EXPORT_BUSY: String get() = backupStrings.exportBusy
    val EXPORT_ERROR_PWD: String get() = backupStrings.exportErrorPwd
    val EXPORT_ERROR_MISMATCH: String get() = backupStrings.exportErrorMismatch
    val EXPORT_SUCCESS_PREFIX: String get() = backupStrings.exportSuccessPrefix
    val EXPORT_FAILED_PREFIX: String get() = backupStrings.exportFailedPrefix

    // ---- 恢复向导 ----
    val RESTORE_TITLE: String get() = backupStrings.restoreTitle
    val RESTORE_PICK_BUTTON: String get() = backupStrings.restorePickButton
    val RESTORE_PICK_HINT: String get() = backupStrings.restorePickHint
    val SUMMARY_VERSION: String get() = backupStrings.summaryVersion
    val SUMMARY_APP: String get() = backupStrings.summaryApp
    val SUMMARY_EXPORTED_AT: String get() = backupStrings.summaryExportedAt
    val SUMMARY_RANGE: String get() = backupStrings.summaryRange
    val SUMMARY_COUNTS: String get() = backupStrings.summaryCountsLabel
    val COUNTS_TX: String get() = backupStrings.countsTx
    val COUNTS_FLOW: String get() = backupStrings.countsFlow
    val COUNTS_RECON: String get() = backupStrings.countsRecon
    val COUNTS_FEE: String get() = backupStrings.countsFee
    val COUNTS_KEYS: String get() = backupStrings.countsKeys
    val COUNTS_SNAPSHOTS: String get() = backupStrings.countsSnapshots
    val RESTORE_PWD_LABEL: String get() = backupStrings.restorePwdLabel
    val RESTORE_UNLOCK: String get() = backupStrings.restoreUnlock
    val RESTORE_VERIFYING: String get() = backupStrings.restoreVerifying
    val RESTORE_NEXT_MODE: String get() = backupStrings.restoreNextMode
    val ERR_WRONG_PASSWORD: String get() = backupStrings.errWrongPassword
    val ERR_UNSUPPORTED: String get() = backupStrings.errUnsupported
    val ERR_MALFORMED: String get() = backupStrings.errMalformed
    val ERR_GENERIC: String get() = backupStrings.errGeneric

    // ---- 模式选择 ----
    val MODE_TITLE: String get() = backupStrings.modeTitle
    val MODE_MERGE: String get() = backupStrings.modeMerge
    val MODE_MERGE_DESC: String get() = backupStrings.modeMergeDesc
    val MODE_OVERWRITE: String get() = backupStrings.modeOverwrite
    val MODE_OVERWRITE_DESC: String get() = backupStrings.modeOverwriteDesc
    val OVERWRITE_CONFIRM_LABEL: String get() = backupStrings.overwriteConfirmLabel
    val MERGE_PREVIEW_PREFIX: String get() = backupStrings.mergePreviewPrefix
    val MERGE_PREVIEW_DUP: String get() = backupStrings.mergePreviewDup
    val MERGE_PREVIEW_MISSING: String get() = backupStrings.mergePreviewMissing
    val RESTORE_EXECUTE: String get() = backupStrings.restoreExecute
    val RESTORE_RUNNING: String get() = backupStrings.restoreRunning

    // ---- 结果 ----
    val RESULT_TITLE: String get() = backupStrings.resultTitle
    val RESULT_IMPORTED_PREFIX: String get() = backupStrings.resultImportedPrefix
    val RESULT_SKIPPED_PREFIX: String get() = backupStrings.resultSkippedPrefix
    val RESULT_MISSING_PREFIX: String get() = backupStrings.resultMissingPrefix
    val RESULT_TEMP_BACKUP_PREFIX: String get() = backupStrings.resultTempBackupPrefix
    val RESULT_ANOMALOUS_PREFIX: String get() = backupStrings.resultAnomalousPrefix
    val RESULT_ANOMALOUS_EXISTING_PREFIX: String get() = backupStrings.resultAnomalousExistingPrefix
    val RESULT_ANOMALOUS_EXISTING_NOTE: String get() = backupStrings.resultAnomalousExistingNote
    val RESULT_NONE: String get() = backupStrings.resultNone
    val RESULT_DONE: String get() = backupStrings.resultDone
    val RESTORE_SUCCESS_TOAST: String get() = backupStrings.restoreSuccessToast

    // ---- CSV ----
    val CSV_SUCCESS_PREFIX: String get() = backupStrings.csvSuccessPrefix
    val CSV_FAILED_PREFIX: String get() = backupStrings.csvFailedPrefix

    // ---- 整句（计数/预览；调用点不拼装，语序由各语言目录决定） ----

    /** 恢复结果导入计数整句（交易 / 资金 / 校准 / 费率 / API 密钥 / 快照）。 */
    @Suppress("LongParameterList") // 六类记录计数一次传全（拆参数会让调用点自己拼句，违背 i18n 规则 3）
    fun importedSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = backupStrings.importedSummary(
        transactions = transactions,
        capitalFlows = capitalFlows,
        reconciliationRecords = reconciliationRecords,
        feeRules = feeRules,
        apiKeys = apiKeys,
        priceSnapshots = priceSnapshots,
    )

    /** 备份摘要「记录条数」整句（同一组计数）。 */
    @Suppress("LongParameterList") // 同上：同一组计数
    fun recordCountsSummary(
        transactions: Int,
        capitalFlows: Int,
        reconciliationRecords: Int,
        feeRules: Int,
        apiKeys: Int,
        priceSnapshots: Int,
    ): String = backupStrings.recordCountsSummary(
        transactions = transactions,
        capitalFlows = capitalFlows,
        reconciliationRecords = reconciliationRecords,
        feeRules = feeRules,
        apiKeys = apiKeys,
        priceSnapshots = priceSnapshots,
    )

    /** 增量合并预览整句（将新增 N 条；已存在跳过 N 条；缺失币种跳过 N 条）。 */
    fun mergePreview(inserts: Int, duplicates: Int, missingCoins: Int): String =
        backupStrings.mergePreview(inserts = inserts, duplicates = duplicates, missingCoins = missingCoins)

    /** CproDecodeException → 文案（flows §7「密码错误或文件损坏」/「未知更高版本提示升级」）。 */
    fun decodeErrorCopy(reason: com.wuzhufolio.domain.backup.CproDecodeException.Reason): String = when (reason) {
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED -> ERR_WRONG_PASSWORD
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.UNSUPPORTED_FORMAT -> ERR_UNSUPPORTED
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.MALFORMED_FILE -> ERR_MALFORMED
    }
}
