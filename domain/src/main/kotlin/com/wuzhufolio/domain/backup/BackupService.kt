package com.wuzhufolio.domain.backup

import java.nio.file.Path
import java.time.Instant

/**
 * 备份恢复用例契约（M9 · task-breakdown T9.1–T9.4 · api-contracts §3 BackupService 细化落地 ·
 * ADR-005 / 共享规范 §8 / PRD 故事 5.2、§9.9、flows §6）。
 *
 * 编排语义（实现在 data/backup/DefaultBackupService）：
 * - 导出 = 读当前账户业务数据 → 凭证解密入载荷 → Argon2id(备份密码) + AES-256-GCM 编码 → 原子写文件；
 * - 预览 = 仅解析明文头部（无需密码，PRD 5.2-5）；
 * - 恢复 = 密码解密 → 规划（[BackupMergePlanner]）→ 单写事务应用 → 全量重放（LENIENT）出异常币种清单；
 * - CSV 明文导出 = 交易/资金流水/持仓汇总，不含任何密钥（PRD §9.9 数据主权）。
 */
interface BackupService {

    /** 备份/恢复元数据（上次备份/恢复时刻，账户级 settings 记录）。 */
    suspend fun backupMetadata(): BackupMetadata

    /**
     * 导出 .cpro（T9.1）。[password] = 备份文件密码（须满足账户密码同强度最低门槛，PRD 5.2-3，
     * 由实现校验）；成功返回摘要（含文件大小）。敏感提示文案在 UI 层先行展示（PRD 5.2-4）。
     */
    suspend fun exportBackup(path: Path, password: CharArray): BackupExportSummary

    /** 解析明文头部（恢复第一步：摘要预览；密码错误/文件损坏不在此步暴露）。 */
    suspend fun previewBackup(path: Path): BackupPreview

    /**
     * 恢复准备（T9.3）：密码验证 + 解密 + 合并规划（预览将新增/跳过条数与缺失币种）。
     * 不落库；[CproDecodeException] 密码错误/损坏在此步上浮。
     */
    suspend fun prepareRestore(path: Path, password: CharArray): RestorePreview

    /**
     * 执行恢复（T9.2/T9.3）。[mode] = 增量合并（默认）/ 全量覆盖（二次确认在 UI 层；
     * 覆盖前自动生成临时 .cpro，路径随摘要返回）。完成后摘要含重放「持仓异常」币种清单。
     */
    suspend fun restoreBackup(path: Path, password: CharArray, mode: RestoreMode): RestoreSummary

    /** CSV 明文导出（T9.3：交易 / 资金流水 / 持仓汇总；不含 API 密钥，PRD §9.9）。 */
    suspend fun exportCsv(kind: CsvExportKind, path: Path)
}

/** 恢复导入方式（PRD 5.2-6）。 */
enum class RestoreMode {
    /** 增量合并（默认）：三级去重 + 幂等合并。 */
    MERGE,

    /** 全量覆盖：清空当前账户账户级业务数据后导入（临时备份 + 不清空全局公共表）。 */
    FULL_OVERWRITE,
}

/** CSV 明文导出种类（PRD §9.9）。 */
enum class CsvExportKind(val fileNameHint: String) {
    TRANSACTIONS("transactions"),
    FUNDS("funds"),
    HOLDINGS("holdings"),
}

/** 上次备份/恢复时刻（账户级 settings：backup.last_at / restore.last_at）。 */
data class BackupMetadata(val lastBackupAt: Instant?, val lastRestoreAt: Instant?)

/** 导出结果摘要。 */
data class BackupExportSummary(
    val path: Path,
    val counts: CproCounts,
    val exportedAt: Instant,
    val sizeBytes: Long,
)

/** 头部预览（明文头部透传 + 解析出的时间范围）。 */
data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: Instant?,
    val counts: CproCounts,
    val rangeMin: Instant?,
    val rangeMax: Instant?,
    val cipher: String,
)

/** 恢复准备（密码验证通过后的合并规划预览）。 */
data class RestorePreview(
    val header: CproHeader,
    /** 合并规划（[mode] = MERGE 时按本地既有键判定；FULL_OVERWRITE 预览按全量插入口径）。 */
    val plan: BackupMergePlanner.MergePlan,
    val mode: RestoreMode,
)

/** 恢复执行结果（各表写入计数 + 重放异常币种 + 临时备份路径）。 */
data class RestoreSummary(
    val mode: RestoreMode,
    val imported: RestoreTableCounts,
    val duplicateSkipped: Int,
    val missingCoinSkipped: Int,
    val missingCoinIds: List<String>,
    /**
     * **本次导入新产生**的「持仓异常」币种符号（恢复后重放[LENIENT]异常集 − 恢复前既存异常集；
     * 同账户增量回导等场景导入未改变账本 → 为空，既存异常见 [preExistingAnomalousCoins]）。
     */
    val anomalousCoins: List<String>,

    /** 恢复前即存在的「持仓异常」币种（恢复后重放异常集 ∩ 恢复前异常集；非本次导入造成）。 */
    val preExistingAnomalousCoins: List<String>,
    /** 全量覆盖前的自动临时备份路径（仅 FULL_OVERWRITE 非空）。 */
    val tempBackupPath: Path?,
    val completedAt: Instant,
)

/** 各表写入计数。 */
data class RestoreTableCounts(
    val transactions: Int,
    val capitalFlows: Int,
    val reconciliationRecords: Int,
    val feeRules: Int,
    val apiKeys: Int,
    val settings: Int,
    val priceSnapshots: Int,
)
