package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.engine.AnchorEvent
import com.wuzhufolio.domain.engine.FundEvent
import com.wuzhufolio.domain.engine.LedgerEvent

/**
 * 全量事件装配层（M8 · M4 遗留「事件构造层」资金半边落地）：
 * 把账户内三类记录（transactions / capital_flows / reconciliation_records）装配为引擎事件全集。
 *
 * 职责：
 * - 交易行 -> TradeEvent：委托 [TransactionEventBuilder]（M7 交易半边，折算价解析/PENDING 口径不变）；
 * - 资金行 -> FundEvent：FK -> cg_id 解析 + fiatValue 动态重建（resolveFiatValue 同源取数链；
 *   完全缺失 = 名义零折算 + estimated，保数量链，黄金用例 9 回填自动纠正）；
 * - 校准行 -> AnchorEvent：delta/deltaFiat 按**记录值固定**（M4 §5-7——差额账务是校准当时的快照，
 *   不随行情重解析）；estimated 恒 false（执行前提即市价可得）；
 * - 排序契约（M4 §5-9）：按 (at, seq) 升序 + **显式确定性决胜序**（seq 冲突时 类型序[资金0/交易1/锚点2]
 *   -> uuid）后再交给引擎——三类表各自独立自增 id，跨表同 seq 需要装配层保证输入序确定。
 */
class LedgerEventAssembler(
    private val catalog: CoinCatalog,
    private val tradeBuilder: TransactionEventBuilder,
) {

    /** 装配结果：引擎事件全集 + 交易行级标记（M7 列表「估算中」/排除行沿用）+ 资金行级估算标记。 */
    data class Assembled(
        val events: List<LedgerEvent>,
        val tradeBuild: TransactionEventBuilder.BuildResult,
        /** estimated = true 的资金行 id（动态估算路径）。 */
        val estimatedFundIds: Set<Long>,
    )

    suspend fun build(
        txRows: List<LedgerTxRow>,
        fundRows: List<FundFlowRow>,
        reconRows: List<ReconciliationRecordRow>,
        baseFiat: String,
    ): Assembled {
        val tradeBuild = tradeBuilder.build(txRows, baseFiat)
        val coinCache = HashMap<Long, CatalogCoin?>()
        suspend fun coinOf(id: Long): CatalogCoin? = coinCache.getOrPut(id) { catalog.getById(id) }

        val funds = ArrayList<FundEvent>(fundRows.size)
        val estimatedFunds = HashSet<Long>()
        for (row in fundRows) {
            val coin = coinOf(row.coinId) ?: continue // 目录缺失：防御性跳过（与交易行 excluded 同口径）
            val value = tradeBuilder.resolveFiatValue(coin, row.amount, baseFiat, row.time)
            funds += FundEvent(
                id = row.uuid,
                at = row.time,
                coin = coin.cgId,
                kind = row.kind,
                quantity = row.amount,
                fiatValue = value.fiat,
                seq = row.id,
                estimated = value.estimated,
            )
            if (value.estimated) estimatedFunds += row.id
        }

        val anchors = ArrayList<AnchorEvent>(reconRows.size)
        for (row in reconRows) {
            val coin = coinOf(row.coinId) ?: continue // 目录缺失：防御性跳过（同资金行口径）
            anchors += AnchorEvent(
                id = row.uuid,
                at = row.createdAt,
                coin = coin.cgId,
                exchangeQuantity = row.exchangeQuantity,
                delta = row.delta,
                deltaFiat = row.baseAmount,
                seq = row.id,
            )
        }

        // 确定性决胜序：类型序（资金 0 / 交易 1 / 锚点 2）-> uuid（M4 §5-9「输入序确定」的装配层落实）
        val events = (funds + tradeBuild.events + anchors).sortedWith(
            compareBy({ it.at }, { it.seq }, { typeRank(it) }, { it.id }),
        )
        return Assembled(events, tradeBuild, estimatedFunds)
    }

    /** 账户事件计数上界（新记录 seq = max+1：三类表合并计数，M7 §5-7 口径推广到三类事件）。 */
    fun nextSeq(
        txRows: List<LedgerTxRow>,
        fundRows: List<FundFlowRow>,
        reconRows: List<ReconciliationRecordRow>,
    ): Long =
        maxOf(
            txRows.maxOfOrNull { it.id } ?: 0L,
            fundRows.maxOfOrNull { it.id } ?: 0L,
            reconRows.maxOfOrNull { it.id } ?: 0L,
        ) + 1

    private fun typeRank(event: LedgerEvent): Int = when (event) {
        is FundEvent -> 0
        is com.wuzhufolio.domain.engine.TradeEvent -> 1
        is AnchorEvent -> 2
    }
}
