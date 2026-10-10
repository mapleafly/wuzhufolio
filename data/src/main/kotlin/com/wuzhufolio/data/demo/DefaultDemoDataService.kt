package com.wuzhufolio.data.demo

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.domain.demo.DemoData
import com.wuzhufolio.domain.demo.DemoDataService
import com.wuzhufolio.domain.demo.DemoFailReason
import com.wuzhufolio.domain.demo.DemoLoadResult
import com.wuzhufolio.domain.demo.DemoPortfolio
import com.wuzhufolio.domain.demo.DemoRejectReason
import com.wuzhufolio.domain.ledger.CoinResolutionException
import com.wuzhufolio.domain.ledger.FundEntryRow
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundService
import com.wuzhufolio.domain.ledger.LedgerValidationException
import com.wuzhufolio.domain.ledger.TransactionLedgerService
import com.wuzhufolio.domain.ledger.TransactionRow
import com.wuzhufolio.domain.ledger.TxFilter
import kotlinx.coroutines.CancellationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * 示例数据用例实现（B 迭代 **B1** · 决策档 `D46` · task-breakdown **T15.3**）。
 *
 * 编排口径（契约 KDoc 见 [DemoDataService]）：
 * - **落库与清除一律经既有账本用例**（[TransactionLedgerService.saveTransaction] /
 *   [FundService.saveFund] / [TransactionLedgerService.deleteTransactions] /
 *   [FundService.deleteFunds]）⇒ 复用既有目录解析、事件构造、折算链与 STRICT 相对校验，
 *   **零 schema 变更、零新表、零 `.cpro` 格式变更、零新增 settings 键**；
 * - **账户级作用域**由 [ActiveSessionStore] + 两个既有服务的 `requireActive()` 共同保证：
 *   未登录时 `load()` 返回类型化失败、`clear()` = 0、`hasDemoData()` = false（不抛未类型化异常）；
 * - **标记** = `notes` 行首 [DemoData.MARKER]（识别、幂等与清除的唯一依据）；
 * - **清除顺序 = 先交易后资金**：示例增资是示例买入的余额来源，先删增资会被相对校验拦住；
 *   反过来先删全部示例交易则不会制造负边界（正常空账户路径必然成功）。
 *   若真实记录**依赖**示例增资，资金行删除会被既有校验拦住 ⇒ 保留该行、返回已删条数（不误删真实数据）。
 */
class DefaultDemoDataService(
    private val sessions: ActiveSessionStore,
    private val transactions: TransactionLedgerService,
    private val funds: FundService,
    /** 示例时间轴基准（默认系统时钟；测试可注入固定时刻）。 */
    private val clock: () -> Instant = Instant::now,
) : DemoDataService {

    private val logger: Logger = LoggerFactory.getLogger(DefaultDemoDataService::class.java)

    @Suppress("ReturnCount") // 未登录 / 读取失败 / 幂等 / 拒绝 四前置判定，平铺早退比嵌套 when 更清晰
    override suspend fun load(): DemoLoadResult {
        if (sessions.get() == null) return DemoLoadResult.Failed(DemoFailReason.NO_SESSION)
        val current = runCatching { snapshot() }.getOrElse {
            logger.warn("demo load | snapshot failed ({})", it.javaClass.simpleName)
            return DemoLoadResult.Failed(DemoFailReason.LOAD_FAILED, describe(it))
        }
        if (current.hasDemo) return DemoLoadResult.AlreadyLoaded
        if (current.hasReal) return DemoLoadResult.Rejected(DemoRejectReason.ACCOUNT_NOT_EMPTY)
        return insertAll()
    }

    @Suppress("ReturnCount") // 未登录 / 读取失败 两前置早退
    override suspend fun clear(): Int {
        if (sessions.get() == null) return 0
        val current = runCatching { snapshot() }.getOrNull() ?: return 0
        var removed = 0
        if (current.demoTransactions.isNotEmpty()) {
            val ids = current.demoTransactions.map { it.id }
            removed += runCatching { transactions.deleteTransactions(ids) }
                .fold(onSuccess = { ids.size }, onFailure = { blocked("transactions", it) })
        }
        if (current.demoFunds.isNotEmpty()) {
            val uuids = current.demoFunds.map { it.uuid }
            removed += runCatching { funds.deleteFunds(uuids) }
                .fold(onSuccess = { uuids.size }, onFailure = { blocked("funds", it) })
        }
        return removed
    }

    override suspend fun hasDemoData(): Boolean {
        if (sessions.get() == null) return false
        return runCatching { snapshot().hasDemo }.getOrDefault(false)
    }

    // ---- 内部 ----

    /** 落库全部示例行（资金先于交易）；任一步失败则**回滚**本次已写入的标记行（只删标记行）。 */
    private suspend fun insertAll(): DemoLoadResult {
        val now = clock()
        var fundCount = 0
        var txCount = 0
        try {
            for (input in DemoPortfolio.fundInputs(now)) {
                funds.saveFund(input)
                fundCount += 1
            }
            for (input in DemoPortfolio.transactionInputs(now)) {
                transactions.saveTransaction(input)
                txCount += 1
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            logger.warn("demo load | failed after funds={} transactions={} ({})", fundCount, txCount, describe(t))
            runCatching { clear() }
            return DemoLoadResult.Failed(classify(t), describe(t))
        }
        return DemoLoadResult.Loaded(transactions = txCount, funds = fundCount)
    }

    /** 当前账户账本快照（按标记二分：示例行 / 真实行）。 */
    private suspend fun snapshot(): DemoSnapshot {
        val txRows = transactions.listTransactions(TxFilter())
        val fundRows = funds.listFunds(FundFilter()).rows
        val (demoTx, realTx) = txRows.partition { DemoData.isDemoNote(it.notes) }
        val (demoFunds, realFunds) = fundRows.partition { DemoData.isDemoNote(it.notes) }
        return DemoSnapshot(demoTx, realTx, demoFunds, realFunds)
    }

    private fun classify(t: Exception): DemoFailReason = when (t) {
        is CoinResolutionException -> DemoFailReason.COIN_UNAVAILABLE
        is LedgerValidationException -> DemoFailReason.DATASET_INCONSISTENT
        else -> DemoFailReason.LOAD_FAILED
    }

    private fun describe(t: Throwable): String =
        t.javaClass.simpleName + (t.message?.let { ": " + it } ?: "")

    private fun blocked(scope: String, t: Throwable): Int {
        logger.warn("demo clear | {} blocked by real records ({})", scope, describe(t))
        return 0
    }

    /** 账本快照（示例行 = 带标记；真实行 = 无标记）。 */
    private class DemoSnapshot(
        val demoTransactions: List<TransactionRow>,
        val realTransactions: List<TransactionRow>,
        val demoFunds: List<FundEntryRow>,
        val realFunds: List<FundEntryRow>,
    ) {
        val hasDemo: Boolean get() = demoTransactions.isNotEmpty() || demoFunds.isNotEmpty()
        val hasReal: Boolean get() = realTransactions.isNotEmpty() || realFunds.isNotEmpty()
    }
}
