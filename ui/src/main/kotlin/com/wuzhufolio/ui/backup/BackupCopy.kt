package com.wuzhufolio.ui.backup

/**
 * 数据管理（备份恢复）文案单源（M9 · T9.4 · ia.md §2.15 / PRD §9.9 / interaction §3 / flows §6）。
 * 文案口径与原型数据管理走查一致；错误文案映射服务层异常（CproDecodeException 三态）。
 */
object BackupCopy {

    // ---- 分区 ----
    const val BACKUP_GROUP_TITLE = "备份"
    const val RESTORE_GROUP_TITLE = "恢复"
    const val CSV_GROUP_TITLE = "明文导出（CSV，不含 API 密钥）"
    const val BACKUP_BUTTON = "备份数据"
    const val RESTORE_BUTTON = "恢复数据"
    const val CSV_TRANSACTIONS = "导出交易记录"
    const val CSV_FUNDS = "导出资金流水"
    const val CSV_HOLDINGS = "导出持仓汇总"
    const val LAST_BACKUP_PREFIX = "上次备份："
    const val LAST_RESTORE_PREFIX = "上次恢复："
    const val NEVER_DONE = "—"
    const val CSV_NOTE = "CSV 为明文导出（数据主权），仅含交易 / 资金 / 持仓汇总，不含任何 API 密钥；" +
        "交易 CSV 与导入模板同列，可直接回导。"

    // ---- 备份弹窗 ----
    const val EXPORT_TITLE = "备份数据"
    const val EXPORT_PWD_LABEL = "备份文件密码"
    const val EXPORT_PWD_CONFIRM_LABEL = "确认备份文件密码"
    const val EXPORT_PWD_RULE = "至少 8 位且包含字母与数字（与账户密码同一最低门槛）"
    const val EXPORT_SENSITIVE_WARNING =
        "备份文件包含 API 密钥等敏感数据，请妥善保管。"
    const val EXPORT_PWD_NOTE =
        "为保护安全，应用不会回填或存储您的账户密码，请为此备份设置独立密码并妥善保管；" +
            "任何设备导入该备份只需此文件密码（与账户密码无关）。"
    const val EXPORT_CONFIRM = "选择保存位置并生成"
    const val EXPORT_ERROR_PWD = "备份密码须至少 8 位且包含字母与数字"
    const val EXPORT_ERROR_MISMATCH = "两次输入的密码不一致"
    const val EXPORT_SUCCESS_PREFIX = "备份完成："
    const val EXPORT_FAILED_PREFIX = "备份失败："

    // ---- 恢复向导 ----
    const val RESTORE_TITLE = "恢复数据"
    const val RESTORE_PICK_BUTTON = "选择 .cpro 文件"
    const val RESTORE_PICK_HINT = "选择此前导出的 .cpro 备份文件（恢复只依赖备份文件密码）。"
    const val SUMMARY_VERSION = "格式版本："
    const val SUMMARY_APP = "应用版本："
    const val SUMMARY_EXPORTED_AT = "导出时间："
    const val SUMMARY_RANGE = "数据时间范围："
    const val SUMMARY_COUNTS = "记录条数："
    const val COUNTS_TX = "交易 "
    const val COUNTS_FLOW = "资金 "
    const val COUNTS_RECON = "校准 "
    const val COUNTS_FEE = "费率 "
    const val COUNTS_KEYS = "API 密钥 "
    const val COUNTS_SNAPSHOTS = "快照 "
    const val RESTORE_PWD_LABEL = "备份文件密码"
    const val RESTORE_UNLOCK = "验证密码"
    const val RESTORE_NEXT_MODE = "下一步"
    const val ERR_WRONG_PASSWORD = "密码错误或文件损坏，请核对备份文件密码后重试。"
    const val ERR_UNSUPPORTED = "备份格式版本较新，请升级应用后再导入。"
    const val ERR_MALFORMED = "不是有效的 .cpro 备份文件（或文件已损坏）。"
    const val ERR_GENERIC = "恢复失败："

    // ---- 模式选择 ----
    const val MODE_TITLE = "选择导入方式"
    const val MODE_MERGE = "增量合并（默认）"
    const val MODE_MERGE_DESC = "按 记录 uuid → 交易所+订单号 → 快照幂等 合并，不删除现有记录。"
    const val MODE_OVERWRITE = "全量覆盖"
    const val MODE_OVERWRITE_DESC = "清空当前账户的业务数据后导入；全局行情缓存保留；覆盖前自动生成临时备份。"
    const val OVERWRITE_CONFIRM_LABEL = "我已知晓全量覆盖将替换当前账户的全部业务数据"
    const val MERGE_PREVIEW_PREFIX = "将新增："
    const val MERGE_PREVIEW_DUP = "条；已存在跳过："
    const val MERGE_PREVIEW_MISSING = "条；缺失币种跳过："
    const val RESTORE_EXECUTE = "开始恢复"

    // ---- 结果 ----
    const val RESULT_TITLE = "恢复完成"
    const val RESULT_IMPORTED_PREFIX = "已导入："
    const val RESULT_SKIPPED_PREFIX = "重复跳过："
    const val RESULT_MISSING_PREFIX = "缺失币种跳过："
    const val RESULT_TEMP_BACKUP_PREFIX = "覆盖前临时备份："
    const val RESULT_ANOMALOUS_PREFIX = "本次导入新产生持仓异常（导入路径例外，可补录增资/校准消除）："
    const val RESULT_ANOMALOUS_EXISTING_PREFIX = "账户既存持仓异常（非本次导入造成，可补录增资/校准消除）："
    const val RESULT_ANOMALOUS_EXISTING_NOTE =
        "既存异常来自恢复前的账本状态（如历史导入负持仓），本次恢复未改变；明细见资金列表「持仓异常」标记。"
    const val RESULT_NONE = "无"
    const val RESULT_DONE = "完成"
    const val RESTORE_SUCCESS_TOAST = "恢复完成，数据已重算。"

    // ---- CSV ----
    const val CSV_SUCCESS_PREFIX = "已导出："
    const val CSV_FAILED_PREFIX = "导出失败："

    /** CproDecodeException → 文案（flows §7「密码错误或文件损坏」/「未知更高版本提示升级」）。 */
    fun decodeErrorCopy(reason: com.wuzhufolio.domain.backup.CproDecodeException.Reason): String = when (reason) {
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED -> ERR_WRONG_PASSWORD
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.UNSUPPORTED_FORMAT -> ERR_UNSUPPORTED
        com.wuzhufolio.domain.backup.CproDecodeException.Reason.MALFORMED_FILE -> ERR_MALFORMED
    }
}
