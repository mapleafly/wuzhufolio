package com.wuzhufolio.data.market

import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.CoinStatus
import com.wuzhufolio.domain.market.CmcMapCoin
import com.wuzhufolio.domain.market.MarketRank
import com.wuzhufolio.domain.catalog.MarketRankProvider

/**
 * CMC map 对齐（M3 遗留 3 落地 · 每日缓存）：CMC /cryptocurrency/map 条目按
 * symbol 精确 →（同名多候选时）name 相等 对齐到 CG 目录 cg_id，产出 {cg_id: cmc_id} 喂
 * [CoinCatalog.refreshCmcIds]。候选多义仍无法唯一 → 跳过（该币 cmc_id 空缺，兜底不可用——
 * 不影响主源；登记语义见模块记录 M5 §5）。
 */
class CmcIdAligner(private val catalog: CoinCatalog) {

    suspend fun align(mapEntries: List<CmcMapCoin>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (entry in mapEntries) {
            val cgId = resolveCgId(entry) ?: continue
            out.putIfAbsent(cgId, entry.cmcId.toString())
        }
        return out
    }

    private suspend fun resolveCgId(entry: CmcMapCoin): String? {
        val candidates = catalog.getBySymbol(entry.symbol)
        val active = candidates.filter { it.status == CoinStatus.ACTIVE }
        val pool = if (active.isNotEmpty()) active else candidates
        if (pool.size == 1) return pool.single().cgId
        val byName = pool.filter { it.name.equals(entry.name, ignoreCase = true) }
        return when (byName.size) {
            1 -> byName.single().cgId
            else -> null
        }
    }
}

/**
 * 市值排名缓存（M3 遗留 4 落地 · /coins/markets 前 1000 名预缓存）：编排层目录刷新时
 * 拉 4×250 页喂 [update]，[MarketRankProvider.rankOf] 只读快照——线程安全（volatile 不可变 map）。
 * 未入前 1000 → null（该候选不参与排名消歧，交由第四级候选/用户选择，共享规范 §6）。
 */
class RefreshableRankProvider : MarketRankProvider {

    @Volatile
    private var ranks: Map<String, Int> = emptyMap()

    fun update(entries: List<MarketRank>) {
        ranks = entries.associate { it.cgId to it.rank }
    }

    fun size(): Int = ranks.size

    override fun rankOf(cgId: String): Int? = ranks[cgId]
}
