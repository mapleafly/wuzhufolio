package com.wuzhufolio.data.market

import com.wuzhufolio.data.accounts.AccountRecord
import com.wuzhufolio.data.accounts.AccountRepository
import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.market.AccountWatchSummary
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.WatchImportFailReason
import com.wuzhufolio.domain.market.WatchImportResult
import com.wuzhufolio.domain.market.WatchImportService
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 自选清单**跨账户显式导入**实现（B 迭代 **B3** · 决策档 `D48` · task-breakdown **T15.5**）。
 *
 * 口径与五条不变量见 [WatchImportService] 的 KDoc。实现要点：
 *
 * - **唯一写入 = 一行**：**写入**的数据仅 = **账户级** settings 行 `settings(account_id, key='watch.coins')`
 *   （[MarketWatchService.SETTINGS_KEY]）——交易/资金流水/API 密钥/行情快照一概不涉；
 *   **只读**另涉 `accounts.username`（摘要展示名）与 `coins` 目录表（symbol 解析），
 *   且目标侧沿用 D41 认领语义（当前账户尚无自选行时会物化该账户行 + 全局 `watch.migrated`）
 *   （**零 schema 变更、零新增表、零新增 settings 键、零 `.cpro` 格式变更**）；
 * - **来源侧刻意裸读**（[readSourceIds]）：**不走** `SettingsMarketWatchService.readStoredIds` ——
 *   后者会触发 **D41 的旧全局清单一次性认领**（复制旧全局行到来源账户 + 置位全局 `watch.migrated`），
 *   会**写来源账户**，违反不变量 3「来源账户数据一字不改」；
 * - **目标侧复用既有自选服务**（[SettingsMarketWatchService.currentStoredIdsOrNull] /
 *   [SettingsMarketWatchService.persistCurrent]）：目标账户的 D41 认领语义、JSON 编码与数组保序
 *   与行情页**完全同源**（不另起一套读写，避免语义漂移）；
 * - **保序去重追加**：当前账户清单为基（未写入过 ⇒ 默认种子），来源中尚不存在的 cg_id 按来源序追加末尾；
 *   来源侧按 D35 `addCoins` **同口径归一**（trim → 去空串 → 去重）后才参与合并；全量重复 ⇒
 *   [WatchImportResult.AlreadyComplete]（**幂等，不写**）；
 * - **失败一律零写入**：未登录 / 来源不存在 / 来源为空 / 写入失败均为类型化结果，**不抛未类型化异常**。
 */
class SettingsWatchImportService(
    private val settings: SettingsRepository,
    private val catalog: CoinCatalog,
    private val sessions: ActiveSessionStore,
    private val accounts: AccountRepository,
    /** 当前账户侧的自选服务（D41 语义同源：认领 / 编码 / 保序写回）。 */
    private val watch: SettingsMarketWatchService,
    private val logger: Logger = LoggerFactory.getLogger(SettingsWatchImportService::class.java),
) : WatchImportService {

    override suspend fun listOtherAccounts(): List<AccountWatchSummary> {
        val current = currentAccountId() ?: return emptyList()
        return accounts.list().filter { it.id != current }.map { summaryOf(it) }
    }

    override suspend fun importFrom(sourceAccountId: Int): WatchImportResult =
        when (val plan = planImport(sourceAccountId)) {
            is ImportPlan.Reject -> plan.result
            is ImportPlan.Ready -> executeImport(plan)
        }

    /** 当前账户 id（未登录 = null ⇒ 无目标可写、摘要为空列表）。 */
    private fun currentAccountId(): Int? = sessions.get()?.account?.id

    /**
     * 计划阶段（**纯读**）：把「结果分支」与「待写入的合并清单」一次算清，避免多出口函数
     * （detekt `ReturnCount ≤ 2`）并让「零写入」路径可被一眼看清。
     */
    private suspend fun planImport(sourceAccountId: Int): ImportPlan {
        val current = currentAccountId()
        // 来源必须是**其它**账户（把当前账户当来源 ⇒ 视为来源不存在）
        val record = current?.let { id -> accounts.findById(sourceAccountId)?.takeIf { it.id != id } }
        val source = record?.let { summaryOf(it) }
        val target = current?.let { watch.currentStoredIdsOrNull() ?: MarketConfig.DEFAULT_WATCH_SEED }.orEmpty()
        // 来源清单已在 summaryOf 归一（trim → 去空串 → 去重，与 D35 `addCoins` 同口径）⇒
        // 「摘要里看到的」与「实际会导入的」严格一致
        val fresh = source?.cgIds?.filterNot { it in target }.orEmpty()
        return when {
            current == null -> ImportPlan.Reject(WatchImportResult.Failed(WatchImportFailReason.NO_SESSION))
            source == null -> ImportPlan.Reject(WatchImportResult.Failed(WatchImportFailReason.SOURCE_NOT_FOUND))
            source.cgIds.isEmpty() ->
                ImportPlan.Reject(WatchImportResult.NoSource(source.accountId, source.username))
            fresh.isEmpty() ->
                ImportPlan.Reject(WatchImportResult.AlreadyComplete(source.accountId, source.username))
            else -> ImportPlan.Ready(current, source, target + fresh, fresh.size, source.cgIds.size)
        }
    }

    /** 执行阶段（**唯一写入点**）：只写当前账户的 `watch.coins` 一行；失败 ⇒ 类型化结果（不抛）。 */
    private suspend fun executeImport(plan: ImportPlan.Ready): WatchImportResult {
        val skipped = plan.sourceTotal - plan.added
        return runCatching { watch.persistCurrent(plan.merged) }.fold(
            onSuccess = {
                logger.info(
                    "watch import applied | source={} target={} added={} skipped={}",
                    plan.source.accountId,
                    plan.targetAccountId,
                    plan.added,
                    skipped,
                )
                WatchImportResult.Imported(
                    added = plan.added,
                    skipped = skipped,
                    sourceAccountId = plan.source.accountId,
                    sourceUsername = plan.source.username,
                )
            },
            onFailure = { error ->
                logger.warn("watch import failed | source={} ({})", plan.source.accountId, error.message)
                WatchImportResult.Failed(WatchImportFailReason.WRITE_FAILED, error.message)
            },
        )
    }

    /** 账户自选**只读**摘要（有效清单 = 存储序**归一后**；未写入过 ⇒ 默认种子，见 `D48 §3.1`）。 */
    private suspend fun summaryOf(record: AccountRecord): AccountWatchSummary {
        val stored = readSourceIds(record.id)
        val ids = normalize(stored ?: MarketConfig.DEFAULT_WATCH_SEED)
        return AccountWatchSummary(
            accountId = record.id,
            username = record.username,
            cgIds = ids,
            displayNames = ids.map { cgId -> catalog.getByCgId(cgId)?.symbol ?: cgId },
            defaultSeed = stored == null,
        )
    }

    /**
     * 归一（**与 D35 `addCoins` 同口径**：trim → 去空串 → 去重；**保序**）。
     *
     * 摘要与导入**共用**本函数 ⇒「弹层里看到的清单」与「实际会导入的清单」严格一致；
     * 来源行理论上已由自选服务归一，此处兜住**手工改库 / 异常载荷**（不把空串或重复项带进当前账户）。
     */
    private fun normalize(ids: List<String>): List<String> =
        ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * 来源账户自选的**原始存储序**（只读）：直接读账户级行并解码，**不触发** D41 的一次性认领
     * （认领会写来源账户 ⇒ 破坏「来源一字不改」）。未写入过 = null；损坏载荷回退默认种子（与行情页同口径）。
     */
    private fun readSourceIds(accountId: Int): List<String>? =
        settings.getAccount(accountId, MarketWatchService.SETTINGS_KEY)?.let(::decode)

    private fun decode(raw: String): List<String>? =
        runCatching { importJson.decodeFromString<List<String>>(raw) }.getOrElse {
            logger.warn("source watch payload corrupt ({}); treat as default seed", it.message)
            null
        }

    /** 导入计划（结果分支 / 待写入清单）——把「零写入」与「唯一写入点」在类型上分开。 */
    private sealed interface ImportPlan {
        /** 直接返回该结果，**不做任何写入**。 */
        data class Reject(val result: WatchImportResult) : ImportPlan

        /** 待执行写入：目标账户 + 来源摘要 + 合并后清单 + 新增条数 + 来源**归一后**条数。 */
        data class Ready(
            val targetAccountId: Int,
            val source: AccountWatchSummary,
            val merged: List<String>,
            val added: Int,
            /** 来源归一（trim/去空/去重）后的条数——`skipped = sourceTotal - added`。 */
            val sourceTotal: Int,
        ) : ImportPlan
    }

    private companion object {
        val importJson: Json = Json { ignoreUnknownKeys = true }
    }
}
