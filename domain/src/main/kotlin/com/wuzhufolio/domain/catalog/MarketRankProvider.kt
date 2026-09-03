package com.wuzhufolio.domain.catalog

/**
 * 市值排名数据源边界（消歧第三级「市值排名最高（预缓存前 1000 名）」，共享规范 §6 / 决策分析 E.3）。
 *
 * M5 行情链路落地 /coins/markets 前 1000 名预缓存后注入真实实现；返回 null = 未入前 1000
 * （该候选不参与排名消歧，排名无法区分时交由第四级候选/用户选择）。
 */
fun interface MarketRankProvider {
    fun rankOf(cgId: String): Int?
}

/** 排名数据未就绪时的空实现（M5 前默认）：第三级自动跳过，不阻断解析。 */
object NoopMarketRankProvider : MarketRankProvider {
    override fun rankOf(cgId: String): Int? = null
}
