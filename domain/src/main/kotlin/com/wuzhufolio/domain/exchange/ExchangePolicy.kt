package com.wuzhufolio.domain.exchange

import java.time.Instant

/**
 * 交易所适配器抽象（T6.1 · ADR-004 §1「统一抽象」/ api-contracts §2）。
 *
 * 每个 adapter 实例**绑定一份凭证**（data 层工厂按 api_keys 行构造，如 BinanceAdapter(credentials)）；
 * 生命周期 = 单次同步/校验调用期间，用毕即弃（凭证不驻留长生命周期对象）。HTTP 用 Ktor client
 * （OkHttp 引擎，系统代理 ProxySelector——ADR-001/003 口径）；调用失败抛 [ExchangeApiException]
 * （kind = InvalidKey/RateLimited/TimestampSkew/SignatureInvalid/Network/Http/Internal）。
 *
 * 同步语义（ADR-004 §3）：调用方（ExchangeSyncService）负责增量去重与 symbol 枚举收敛；
 * adapter 只做「凭证校验 + 余额 + 成交分页 + pair 注册表」四件事。
 */
interface ExchangeAdapter {

    /** 交易所名（= exchange_coin_map.exchange / transactions.exchange 存储值，MVP "BINANCE"）。 */
    val exchangeName: String

    /**
     * 校验凭证（GET /api/v3/account 签名请求；PRD §9.10「保存时测试请求验证」）。
     * 成功 = 密钥有效（只读权限足够取到账户信息）；失败抛 [ExchangeApiException]。
     */
    suspend fun validateCredentials()

    /** 账户余额（GET /api/v3/account；非零资产 = 同步 symbol 枚举的「余额推导」输入）。 */
    suspend fun fetchBalances(): List<Balance>

    /**
     * 单 symbol 增量成交（GET /api/v3/myTrades，签名单 symbol 必传）。
     * [sinceId] = 已同步的最大成交 id（null = 从最新拉一页；非 null = id > sinceId 的增量，limit ≤ 500）。
     * 返回按 id 升序的成交列表；调用次数受权重预算约束（见 ExchangeSyncPolicy，单次 ≤120 次）。
     */
    suspend fun fetchTrades(symbol: String, sinceId: Long?, limit: Int): List<ExchangeTrade>

    /** pair 注册表（GET /api/v3/exchangeInfo，公开端点，symbol/base/quote 切分唯一可信来源）。 */
    suspend fun fetchPairs(): List<PairInfo>
}

/** 适配器层常量（Binance 权重/限额口径，ADR-004 §3.1 引用）。 */
object ExchangeLimits {
    /** myTrades 单次同步权重预算（≤1200；约 120 次调用/分钟窗口）。 */
    const val WEIGHT_BUDGET = 1200

    /** myTrades 单次调用权重（Binance 官方 myTrades weight=10）。 */
    const val MY_TRADES_WEIGHT = 10

    /** 单次同步 myTrades 调用上限 = 1200 / 10 = 120。 */
    const val MAX_MY_TRADES_CALLS = WEIGHT_BUDGET / MY_TRADES_WEIGHT

    /** myTrades 单页条数上限（ADR-004 §2：limit ≤ 500；更早历史需 CSV 补录，PRD 故事 4.1-6）。 */
    const val TRADES_PAGE_LIMIT = 500
}

/** 单 symbol 增量分页游标（sync 编排层按 symbol 独立维护：fromId 已同步最大 id，无则 null 拉最新一页）。 */
data class SymbolCursor(val symbol: String, val sinceId: Long?)

/** 单次同步调度计划（T6.2 · ADR-004 §3.1）：候选 symbol 收敛 + 预算内分批。 */
data class SyncPlan(
    val cursors: List<SymbolCursor>,
    /** 预算不足未能排入本轮的 symbol 数（>0 = 部分同步，下轮续传）。 */
    val queuedSymbols: Int,
)

/**
 * 交易所同步纯规则（T6.2 · domain 无 IO 可单测）：
 *
 * 1. **symbol 枚举收敛**（ADR-004 §3.1 / api-contracts §2.2）：候选 symbol = 余额推导 pair ∪ 历史已同步 pair
 *    ∪（手动指定，MVP 可不暴露）；全部来自 exchangeInfo 注册表（不靠字符串猜测切分）。
 * 2. **预算分批**：单次同步权重 ≤1200（≈120 次 myTrades 调用），超额 queue 到下一轮并在 sync_logs 注明。
 */
object ExchangeSyncPolicy {

    /**
     * 余额推导 pair：非零余额资产（free+locked>0）× exchangeInfo 中该资产可组成的 TRADING 状态 pair。
     * 只取「该资产为基础币」的 pair（买入资产侧——Binance myTrades 逐 symbol 必传，卖出的计价资产
     * 会经由「历史已同步 pair」在后续轮次覆盖；首轮以持有资产为主，全量历史依赖 CSV 补录，PRD 故事 4.1-6）。
     */
    fun balanceDerivedSymbols(balances: List<Balance>, pairs: List<PairInfo>): Set<String> {
        val held = balances.filter { it.total.signum() > 0 }.map { it.asset }.toSet()
        return pairs.asSequence()
            .filter { it.status == "TRADING" }
            .filter { it.baseAsset in held }
            .map { it.symbol }
            .toSet()
    }

    /**
     * 历史已同步 pair（本地 transactions 表已存在 (exchange, pair) 的去重 symbol 集——由同步服务查询后传入，
     * 本函数只做归一化与排序）。历史上卖出后不再持有的资产由此保留在同步范围内。
     */
    fun syncedSymbolsSet(raw: Collection<String>): Set<String> = raw.map { it.trim().uppercase() }.toSet()

    /** 收敛候选 = 余额推导 ∪ 历史已同步（∪ 手动指定集合）。 */
    fun candidateSymbols(
        balanceDerived: Set<String>,
        synced: Set<String>,
        manual: Set<String> = emptySet(),
    ): List<String> = (balanceDerived + synced + manual).sorted()

    /**
     * 预算内分批：按 symbol 顺序逐个分配 1 次 myTrades 调用（单 symbol 单页 ≤500 条；首轮即拉最近 500 条
     * 历史——超 500 条属 CSV 补录域）。预算用尽后剩余 symbol 排队下轮。
     *
     * @return 本轮应请求的 cursor 列表 + 排队数。
     */
    fun plan(candidates: List<String>, syncedIds: Map<String, Long?>): SyncPlan {
        val budget = ExchangeLimits.MAX_MY_TRADES_CALLS
        val cursors = candidates.take(budget).map { SymbolCursor(it, syncedIds[it]) }
        return SyncPlan(cursors = cursors, queuedSymbols = (candidates.size - budget).coerceAtLeast(0))
    }
}
