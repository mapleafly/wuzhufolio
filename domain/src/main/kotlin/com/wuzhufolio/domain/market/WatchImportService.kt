package com.wuzhufolio.domain.market

/**
 * 自选清单**跨账户显式导入**契约（B 迭代 **B3** · 决策档 `D48` · task-breakdown **T15.5**）。
 *
 * **把「其它账户」的行情自选清单，经用户显式操作，一次性复制（导入）到当前账户。**
 *
 * **五条不变量（任何实现都不得违反；由 `data` 模块 `WatchImportServiceTest` 与 `ui` 模块
 * `WatchImportUiTest` 守护）**：
 *
 * 1. **仅复制自选清单本身**：唯一被读取与写入的数据 = **账户级** settings 行
 *    `settings(account_id, key='watch.coins')`（JSON 数组 `[cg_id]`，D41）。
 *    **绝不**读写交易、资金流水、API 密钥、账户资料、行情快照等任何其它表/行；
 * 2. **必须显式**：由用户**选择来源账户 + 二次确认**（确认前展示将导入的币种数量/列表摘要）触发；
 *    **不提供**隐式跨账户可见，**不提供**「全部账户聚合视图」——后者会实质改变 `§1.1-5` 的
 *    「账户数据互相隔离」语义，需另立决策（不在本项范围，见 `D48 §1.2`）；
 * 3. **单向**：只写**当前账户**；**来源账户的数据一字不改**（含**不得触发**来源账户的 D41 旧全局清单
 *    一次性认领——认领会写来源账户）；
 * 4. **异常 / 空 / 重复都是类型化结果且零写入**：未登录、来源账户不存在、来源为空、全量重复
 *    ⇒ [WatchImportResult.Failed] / [WatchImportResult.NoSource] / [WatchImportResult.AlreadyComplete]，
 *    **不写任何行**（`AlreadyComplete` 为**幂等**结果）；写入失败 ⇒ [WatchImportResult.Failed]
 *    （[WatchImportFailReason.WRITE_FAILED]），同样不留下部分写入；
 * 5. **账户级作用域**由既有 `ActiveSessionStore` 保证（未登录 ⇒ 无当前账户可写、摘要为空列表）；
 *    **不新增表、不改 `.cpro` 格式**——`watch.coins` 本就在账户级 settings 行内（D41），
 *    该行随账户级 settings 进入 `.cpro`（ADR-005 §3）⇒ **零 schema 变更、零备份格式变更**。
 *
 * **与 D41 的关系**：D41 定「自选为账户级、账户之间互不可见」；本契约是其**显式一次性复制的例外补充**——
 * 「用户显式选择 + 二次确认的一次性复制」**不等于**「账户间隐式可见」，隔离语义不变（论证见 `D48 §4`）。
 *
 * **合并语义**（与 D35 `addCoins` 同口径）：**保序去重追加**——当前账户自选为基
 * （**未写入过** ⇒ 默认种子），来源中**尚不存在**的 cg_id 按**来源序**追加在末尾；
 * 已存在的**跳过**且**不打乱**既有排列。
 */
interface WatchImportService {

    /**
     * 列出**其它账户**及其自选摘要（**只读**：不得改动任何账户的任何数据，含不得触发 D41 的旧全局清单认领）。
     *
     * **未登录 ⇒ 空列表**；本机只有当前一个账户 ⇒ 空列表（UI 据此**不渲染**导入入口）。
     * 返回顺序 = 账户 id 升序。
     */
    suspend fun listOtherAccounts(): List<AccountWatchSummary>

    /**
     * 把 [sourceAccountId] 账户的自选**一次性复制**（保序去重追加）到**当前账户**。
     *
     * 返回可区分的结果（**不抛未类型化异常**）；除 [WatchImportResult.Imported] 外**一律零写入**：
     * - [WatchImportResult.Imported]：已写入（[WatchImportResult.Imported.added] > 0）；
     * - [WatchImportResult.AlreadyComplete]：来源清单已全部在当前自选中（**幂等**，未做任何写入）；
     * - [WatchImportResult.NoSource]：来源账户自选为空（该账户显式清空过），未做任何写入；
     * - [WatchImportResult.Failed]：未登录 / 来源账户不存在（含把**当前账户**当作来源）/ 写入失败。
     */
    suspend fun importFrom(sourceAccountId: Int): WatchImportResult
}

/**
 * 来源账户的自选**只读**摘要（`D48 §3.1`）。
 *
 * [cgIds] = 该账户的**有效**自选清单（**存储序**；**归一**口径与 D35 `addCoins` 一致——trim / 去空串 / 去重，
 * 且**与导入共用同一归一** ⇒「摘要里看到的」= 「实际会导入的」；该账户**未写入过**自选 ⇒ 默认种子，
 * 此时 [defaultSeed] 为 true）；[displayNames] 与 [cgIds] **同序同长**
 * （coins 目录可解析 = symbol；缺行回退 cg_id 本身，不猜测、不丢弃）。
 */
data class AccountWatchSummary(
    val accountId: Int,
    val username: String,
    /** 该账户**有效**自选清单（**存储序**，归一后：trim / 去空串 / 去重；未写入过 ⇒ 默认种子）。 */
    val cgIds: List<String>,
    /** 与 [cgIds] **同序同长**的展示名（目录可解析 = symbol，缺行回退 cg_id）。 */
    val displayNames: List<String>,
    /** true = 该账户**尚未写入过**自选（当前为默认种子；导入即复制该种子）。 */
    val defaultSeed: Boolean,
) {
    /** 自选币种数量（摘要用）。 */
    val coinCount: Int get() = cgIds.size

    /** 该账户自选为空（显式清空过）——导入将返回 [WatchImportResult.NoSource]。 */
    val empty: Boolean get() = cgIds.isEmpty()
}

/** 跨账户导入结果（可区分，UI 逐态映射文案；同 `api-contracts §4`「错误码 → 文案」口径）。 */
sealed interface WatchImportResult {

    /**
     * 已导入：[added] 个币种按来源序**追加**在当前账户自选末尾，[skipped] 个因当前账户已存在而**跳过**。
     * [sourceUsername] 供结果文案回显（用户可核对来源）。
     */
    data class Imported(
        val added: Int,
        val skipped: Int,
        val sourceAccountId: Int,
        val sourceUsername: String,
    ) : WatchImportResult

    /** **全量重复**：来源清单已全部在当前自选中 ⇒ **幂等**，未做任何写入。 */
    data class AlreadyComplete(
        val sourceAccountId: Int,
        val sourceUsername: String,
    ) : WatchImportResult

    /** **来源为空**（该账户把自选清空过）⇒ 未做任何写入。 */
    data class NoSource(
        val sourceAccountId: Int,
        val sourceUsername: String,
    ) : WatchImportResult

    /** 失败（**未做任何写入**）。[detail] 仅供日志/诊断，不直接进 UI 文案。 */
    data class Failed(
        val reason: WatchImportFailReason,
        val detail: String? = null,
    ) : WatchImportResult
}

/** 导入失败的原因（UI 逐态映射可操作提示）。 */
enum class WatchImportFailReason {
    /** 未登录（会话锁定）——无当前账户可写；账户级作用域由既有会话服务保证。 */
    NO_SESSION,

    /** 来源账户不存在（含把**当前账户**自身当作来源；账户可能已被删除）。 */
    SOURCE_NOT_FOUND,

    /** 写入当前账户自选失败（详见日志；未留下部分写入）。 */
    WRITE_FAILED,
}
