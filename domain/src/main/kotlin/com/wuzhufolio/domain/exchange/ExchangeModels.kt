package com.wuzhufolio.domain.exchange

import java.math.BigDecimal
import java.time.Instant

/**
 * 交易所同步领域模型（M6 · task-breakdown T6.1–T6.3 · ADR-004 / api-contracts §2 / data-model §2.2/§2.5/§2.8）。
 *
 * 与行情链路（domain/market）严格隔离：不同客户端、不同 Key 存储、不同错误模型、不同日志
 * （两类 API 独立，PRD 名词解释「交易数据同步」/「行情数据刷新」）。
 *
 * 记账口径衔接 M4 引擎（LedgerModels）：本层产出「交易所成交」与「映射结果」，
 * 由上层（M6 sync 服务 + M7 事件构造层）转换成 transactions 行/引擎事件。
 */

/** 交易所成交（Binance myTrades 单行解析产物；MVP 收敛 Binance，后续交易所逐个评审接入）。 */
data class ExchangeTrade(
    /** 成交 id（Binance myTrades.id，单调递增——增量游标 fromId 的语义基础）。 */
    val id: Long,
    /** 订单 id（同一订单可分多笔成交，id 唯一而 orderId 可能重复——去重以 id 为准）。 */
    val orderId: Long,
    /** 交易对符号（如 BTCUSDT；切分依赖 exchangeInfo 注册表，不靠字符串猜测）。 */
    val symbol: String,
    val side: TradeSide,
    /** 成交价（报价币计价）。 */
    val price: BigDecimal,
    /** 成交数量（基础币）。 */
    val qty: BigDecimal,
    /** 手续费数量（commissionAsset 计价）。 */
    val fee: BigDecimal,
    /** 手续费币种（base/quote 或第三币种如 BNB）。 */
    val feeAsset: String,
    /** 成交时间（UTC，Binance 毫秒时间戳）。 */
    val time: Instant,
)

/** 成交方向（Binance isBuyer 映射；transactions.type 枚举 BUY/SELL）。 */
enum class TradeSide(val storageValue: String) {
    BUY("BUY"),
    SELL("SELL"),
    ;

    companion object {
        fun fromStorage(value: String): TradeSide = entries.firstOrNull { it.storageValue == value } ?: BUY
    }
}

/** 交易所资产余额（fetchBalances 返回的账户内各资产 free/locked）。 */
data class Balance(val asset: String, val free: BigDecimal, val locked: BigDecimal) {
    val total: BigDecimal get() = free + locked
}

/** 交易所 pair 注册表条目（exchangeInfo 解析产物；symbol/base/quote 切分唯一可信来源）。 */
data class PairInfo(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val status: String,
)

/** 交易所 API 凭证（Binance api_key + secret_key；只在调用瞬间驻留内存、用毕擦除）。 */
data class ExchangeCredentials(val apiKey: String, val secretKey: String)


// ---------------------------------------------------------------------------
// API 密钥管理 & 同步结果模型（M6 · data-model §2.2/§2.8 的 domain 视图）
// ---------------------------------------------------------------------------

/** api_keys 行摘要（API 管理页列表；不含任何明文密钥，展示只落掩码）。 */
data class ApiKeyInfo(
    val id: Long,
    val accountId: Long,
    val name: String,
    val exchangeName: String,
    val lastSyncTime: java.time.Instant?,
    val status: String,
    val configured: Boolean,
) {
    /** 掩码展示（原型 API Key 行部分隐藏；安全优先口径：明文/尾号不驻留 UI，同 M5 §5-6）。 */
    val maskedKey: String get() = "••••••••" + if (configured) name.takeLast(1) else ""
}

/** 新增/覆盖 API 密钥输入（凭证只在该动作瞬间驻留；data 层字段级加密落库）。 */
data class ApiKeyInput(
    val name: String,
    val exchangeName: String,
    val apiKey: String,
    val secretKey: String,
)

/** 凭证校验结果（保存前「测试请求」= 只校验不落库；保存 = 校验通过后落库 + 立即首次同步）。 */
sealed interface CredentialValidation {
    data object Ok : CredentialValidation
    data class Failed(val kind: ExchangeError) : CredentialValidation
}

/** 单次同步（单个 API 密钥）结果（T6.3：状态 + 新增/去重计数 + 脱敏 message）。 */
data class ApiKeySyncResult(
    val apiKeyId: Long,
    val apiKeyName: String,
    val status: SyncStatus,
    val newTrades: Int,
    val duplicatesSkipped: Int,
    val unresolvedSkipped: Int,
    val partial: Boolean,
    val queuedSymbols: Int,
    val error: ExchangeError?,
    val message: String,
    val at: java.time.Instant,
)

/** 同步状态（api_keys.status / sync_logs.status 存储值）。 */
enum class SyncStatus(val storageValue: String) {
    OK("OK"),
    FAILED("FAILED"),
    ;

    companion object {
        fun fromStorage(value: String?): SyncStatus = entries.firstOrNull { it.storageValue == value } ?: FAILED
    }
}

/** sync_logs 行（data-model §2.8；message 已脱敏）。 */
data class SyncLogRow(
    val id: Long,
    val accountId: Long,
    val apiKeyId: Long?,
    val syncTime: java.time.Instant,
    val status: SyncStatus,
    val newTradesCount: Int,
    val message: String,
)
