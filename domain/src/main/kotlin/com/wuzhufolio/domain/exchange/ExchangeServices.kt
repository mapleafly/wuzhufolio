package com.wuzhufolio.domain.exchange

import java.time.Instant

/**
 * 交易所同步用例契约（T6.2/T6.3 · api-contracts §3 SyncService 细化 + ADR-004 §3）。
 *
 * 编排语义：
 * - 触发 = 启动 / 定时 / 手动（API 管理页按钮 + 保存后首次同步）；API 同步间隔 15/30/60（默认 30）——
 *   **调度循环宿主（窗口可见性/托盘驻留）随 M11 桌面集成**（与 M5 调度宿主拆分同口径，模块记录 M5 §5-5）；
 * - 单飞：同一账户同一时刻只跑一次同步（重复触发合并，勿并发拉取）；
 * - 每次以 [ApiKeySyncResult] 返回 per-key 结果；同步编排内部写 sync_logs（脱敏）并更新
 *   api_keys.last_sync_time/status（OK/FAILED）。
 */
interface ExchangeSyncService {

    /** 当前账户全部 API 密钥（API 管理页列表；不含任何明文）。 */
    suspend fun listKeys(): List<ApiKeyInfo>

    /**
     * 新增 API 密钥（保存 = 测试请求验证通过后落库 + **立即首次同步**，PRD 流程图 3「保存后立即执行首次同步」）。
     * 校验失败抛 [CredentialValidationFailed]（含失败原因）；同步结果 = 该 key 首次同步结果。
     */
    suspend fun addAndSync(input: ApiKeyInput): ApiKeySyncResult

    /**
     * 编辑密钥（M6 验收修复轮）：[ApiKeyInput.name] 必填；API Key/Secret **均留空 = 仅更新别名**（不触发网络校验）；
     * 两者都提供 = 先测试请求验证再以账户 DEK 重包覆盖原行（row id 不变、AAD 稳定）；只填其一拒绝（V 系校验）。
     * 别名与该账户同交易所内唯一（冲突抛 [DuplicateApiKeyNameException]）。
     */
    suspend fun updateKey(apiKeyId: Long, input: ApiKeyInput)

    /** 移除密钥（同一写事务内清理其 sync_logs 后删除——M008 api_key_id FK，修复轮）。 */
    suspend fun removeKey(apiKeyId: Long)

    /** 仅测试凭证（不落库、不触发同步；API 管理页「测试请求」按钮）。 */
    suspend fun testCredentials(input: ApiKeyInput): CredentialValidation

    /** 立即同步：无参 = 当前账户全部已存 API 密钥；[apiKeyId] = 单个密钥。返回 per-key 结果。 */
    suspend fun syncNow(apiKeyId: Long? = null): List<ApiKeySyncResult>

    /** 最近同步记录（sync_logs，按时间倒序；API 管理页「同步记录展示」）。 */
    suspend fun recentSyncLogs(limit: Int): List<SyncLogRow>

    /** 当前 API 同步间隔档位（分钟；设置键 sync.interval_minutes，缺省 30，仅 15/30/60）。 */
    suspend fun syncIntervalMinutes(): Int

    /** 设置 API 同步间隔（限 15/30/60，其他值拒绝）。 */
    suspend fun saveSyncIntervalMinutes(minutes: Int)
}

/** 校验失败（凭证不可用；携带原因供 UI 映射 B2 等文案）。 */
class CredentialValidationFailed(val validation: CredentialValidation.Failed) : RuntimeException("credential invalid")

/** API 同步间隔档位规则（PRD 4.2：15/30/60，默认 30；存储键见 data 实现）。 */
object ExchangeCadence {
    val ALLOWED_MINUTES: List<Int> = listOf(15, 30, 60)
    const val DEFAULT_MINUTES: Int = 30
}
