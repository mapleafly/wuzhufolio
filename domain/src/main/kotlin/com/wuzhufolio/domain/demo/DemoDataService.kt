package com.wuzhufolio.domain.demo

/**
 * 示例数据用例契约（B 迭代 **B1** · 决策档 `D46` · task-breakdown **T15.3**）。
 *
 * **口径（三条不变量，任何实现都不得违反）**：
 * 1. **仅在空账户可加载**：加载前检查当前账户是否已有**非示例**交易/资金记录（无 [DemoData.MARKER]
 *    前缀者）；有则**拒绝**（[DemoLoadResult.Rejected]），一条都不写——避免示例数据与真实账本混淆；
 * 2. **幂等**：账户内已存在带标记行 ⇒ 返回 [DemoLoadResult.AlreadyLoaded]，不重复写入；
 * 3. **清除只删带标记行**：真实记录（无标记）永不删除；清除后账户回到「可重新加载」状态。
 *
 * **账户级作用域**：由既有会话服务保证（`ActiveSessionStore` + 既有
 * `TransactionLedgerService` / `FundService` 的 `requireActive()`）——未登录时返回
 * [DemoLoadResult.Failed]（[DemoFailReason.NO_SESSION]）/ `clear()` = 0 / `hasDemoData()` = false，
 * **不抛未类型化异常**。
 *
 * 落库与清除一律**经既有账本用例**（`saveTransaction` / `saveFund` / `deleteTransactions` /
 * `deleteFunds`）⇒ 复用既有 STRICT 相对校验、事件构造与折算链，**零 schema 变更、零新表、
 * 零 `.cpro` 格式变更**（实现见 `data` 模块 `DefaultDemoDataService`）。
 */
interface DemoDataService {

    /**
     * 加载示例数据集（[DemoPortfolio]）。返回**可区分**的结果，不抛未类型化异常：
     * - [DemoLoadResult.Loaded]：已写入的条数（交易 / 资金）；
     * - [DemoLoadResult.AlreadyLoaded]：幂等命中；
     * - [DemoLoadResult.Rejected]：当前账户已有真实记录（仅 [DemoRejectReason.ACCOUNT_NOT_EMPTY]）；
     * - [DemoLoadResult.Failed]：未登录 / 币种目录暂缺示例所需币种 / 数据集与账本冲突 / 其他落库失败
     *   （失败时**回滚**本次已写入的示例行，账户保持「无示例数据」状态）。
     */
    suspend fun load(): DemoLoadResult

    /**
     * 清除示例数据：只删带 [DemoData.MARKER] 标记的交易/资金记录，返回**实际删除**的行数
     * （无示例数据 / 未登录 ⇒ 0）。
     *
     * 边界（`D46 §2`）：若真实记录**依赖**示例增资（清掉增资会让真实记录转负），既有相对校验会拦住
     * 资金行删除 ⇒ 本方法返回已删条数且**保留**该示例行（`hasDemoData()` 仍为 true），
     * UI 据此提示「剩余示例记录被真实记录依赖」；**任何真实记录都不会被删除**。
     */
    suspend fun clear(): Int

    /** 当前账户是否存在示例数据（未登录 / 无示例 ⇒ false）。 */
    suspend fun hasDemoData(): Boolean
}

/** 示例数据加载结果（可区分，UI 逐态映射文案；api-contracts §4「错误码→文案」同口径）。 */
sealed interface DemoLoadResult {

    /** 已加载（[transactions] / [funds] = 本次写入条数）。 */
    data class Loaded(val transactions: Int, val funds: Int) : DemoLoadResult

    /** 幂等命中：账户内已存在带标记的示例数据，未重复写入。 */
    data object AlreadyLoaded : DemoLoadResult

    /** 被拒绝（未写入任何记录）。 */
    data class Rejected(val reason: DemoRejectReason) : DemoLoadResult

    /** 失败（未写入任何记录；本次部分写入已回滚）。[detail] 仅供日志/诊断，不直接进 UI 文案。 */
    data class Failed(val reason: DemoFailReason, val detail: String? = null) : DemoLoadResult
}

/** 加载被拒绝的原因（「空账户」是唯一可加载前提）。 */
enum class DemoRejectReason {
    /** 当前账户已有真实交易/资金记录 ⇒ 不加载（示例数据只在空账户可加载）。 */
    ACCOUNT_NOT_EMPTY,
}

/** 加载失败的原因（UI 逐态映射可操作提示）。 */
enum class DemoFailReason {
    /** 未登录（会话锁定）——账户级作用域由既有会话服务保证。 */
    NO_SESSION,

    /** 币种目录暂缺示例数据所需币种（如离线且从未刷新过目录）。 */
    COIN_UNAVAILABLE,

    /** 示例数据集与当前账本冲突（被既有重放校验拦截；属数据集自洽性回归信号）。 */
    DATASET_INCONSISTENT,

    /** 其他落库失败（详见日志）。 */
    LOAD_FAILED,
}
