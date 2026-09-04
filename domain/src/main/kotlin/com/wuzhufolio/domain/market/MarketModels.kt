package com.wuzhufolio.domain.market

import java.math.BigDecimal
import java.time.Instant

/**
 * 行情链路领域模型（M5 · task-breakdown T5.1–T5.4 · ADR-003 / api-contracts §1 / data-model §2.11）。
 *
 * 币键 = CoinGecko id（cg_id，与引擎/目录同键口径，见模块记录 M4 §5-1）；法币代码为 ISO 4217 大写（USD/EUR/CNY）。
 * 行情 Key 属应用级秘密（ADR-002 §2.1 方案甲）：不驻留本层模型，由设置服务层经设备密钥加解密存取。
 */

/** 价格来源/平台（= data-model price_snapshots.price_source；CG 主源 / CMC 兜底）。 */
enum class PriceSource(val storageValue: String) {
    COINGECKO("COINGECKO"),
    COINMARKETCAP("COINMARKETCAP"),
    ;

    companion object {
        fun fromStorage(value: String?): PriceSource =
            entries.firstOrNull { it.storageValue == value } ?: COINGECKO
    }
}

/** 单币单法币当前价（fetchCurrent 解析产物；at = 服务端返回时刻本地时间）。 */
data class MarketQuote(
    val coin: String,
    val fiat: String,
    val price: BigDecimal,
    val source: PriceSource,
    val at: Instant,
)

/** CMC 当前价（币键 = CMC id；编排层经 coins.cmc_id 桥接回 cg_id 后落快照）。 */
data class CmcQuote(
    val cmcId: Long,
    val fiat: String,
    val price: BigDecimal,
    val source: PriceSource = PriceSource.COINMARKETCAP,
    val at: Instant,
)

/** 历史价点（/market_chart/range 的 [ts, px] 序列；跨度 ≤90 天为小时级、更早为日级——CG 服务端口径）。 */
data class MarketCandle(
    val at: Instant,
    val price: BigDecimal,
)

/** CMC /cryptocurrency/map 条目（对齐 coins.cmc_id 的输入；对齐规则见模块记录 M5 §5）。 */
data class CmcMapCoin(
    val cmcId: Long,
    val symbol: String,
    val name: String,
)

/** 行情 Key 配置状态（T5.5 设置页展示；明文 Key 不驻留 UI/日志）。 */
data class MarketKeyStatus(
    val cgConfigured: Boolean,
    val cmcConfigured: Boolean,
)

/** 一次行情刷新的结果（T5.4 状态栏/提示的数据源；错误语义见 [MarketRefreshError]）。 */
data class MarketRefreshResult(
    /** 上次成功时刻（nil = 从未成功——保持上次价格并显示时间戳语义）。 */
    val at: Instant?,
    val source: PriceSource?,
    /** 本次成功落快照的币数。 */
    val refreshedCoins: Int,
    /** 本次请求但两个来源均无价（「无行情」）的币。 */
    val untracked: List<String>,
    /** 配置个人 Key 后的月度额度使用百分比（0–100；无 Key 模式 null）。 */
    val quotaPercentUsed: Int?,
    /** 非空 = 刷新失败原因（主源失败且无兜底/兜底失败时置位，UI 按文案映射）。 */
    val error: MarketRefreshError?,
    val cgConfigured: Boolean,
    val cmcConfigured: Boolean,
)
