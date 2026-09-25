package com.wuzhufolio.data.market

/**
 * 「有实际成交的币」接收口（**D35**：成交币自动进入行情自选）。
 *
 * 为什么抽成窄接口而不是让账本/同步服务直接依赖 [com.wuzhufolio.domain.market.MarketWatchService]：
 * ① 这两条写路径（账本、交易所同步）与行情自选分属不同领域，直连会把「展示偏好」拖进账本语义；
 * ② 装配层（AppBootstrap）可以把实现换成任意策略（含 no-op），单测无需行情栈即可跑；
 * ③ 口径明确：**实现必须幂等**（重复调用不得产生重复项），**调用方必须容忍失败**
 *    （自选维护失败绝不允许影响交易落账）。
 */
fun interface TradedCoinSink {

    /** 把 [cgIds] 并入行情自选（幂等；空集合 = 无操作）。 */
    suspend fun accept(cgIds: Collection<String>)

    companion object {
        /** 空实现（测试/未装配时的默认值：行为与 D35 之前完全一致）。 */
        val NONE: TradedCoinSink = TradedCoinSink { }
    }
}
