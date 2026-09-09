package com.wuzhufolio.domain.catalog

/**
 * 币种主数据用例契约（T3.1–T3.3；M3 契约补录 api-contracts §3，登记见模块记录 M3.md §5）。
 *
 * 消费方：M5（行情 /coins/list 刷新喂 [refreshDirectory]、CMC map 喂 [refreshCmcIds]）、
 * M6/M7（[search]/[resolve]/[freezeMapping]/[fiatQuoteLeg]）。全部走 DbGate 单写队列（写）/WAL 读。
 */
@Suppress("TooManyFunctions") // 目录/消歧/法币三域共 11 动作（api-contracts §3 补录登记），拆分反损内聚
interface CoinCatalog {

    // ---------- T3.1 coins 目录：检索（共享规范 §6「输入归一化」） ----------

    /** 归一检索：symbol 精确 > symbol 前缀 > 名称包含（大小写归一，如 "usdt" 命中 USDT）；按相关度返回。 */
    suspend fun search(query: String, limit: Int = 20): List<CatalogCoin>

    /** 精确同名候选（大小写不敏感；消歧候选源，含非 ACTIVE 行）。 */
    suspend fun getBySymbol(symbol: String): List<CatalogCoin>

    suspend fun getByCgId(cgId: String): CatalogCoin?

    suspend fun getById(id: Long): CatalogCoin?

    // ---------- T3.1 coins 目录：每日缓存写入（/coins/list + CMC /cryptocurrency/map） ----------

    /** 全量目录 upsert（/coins/list 解析产物；按 cg_id 幂等，UNIQUE(cg_id) 守护）；返回实际新增/变更数。 */
    suspend fun refreshDirectory(entries: List<CoinDirectoryEntry>): DirectoryRefreshSummary

    /** cmc_id 每日缓存（CMC map 解析产物按 cg_id 对齐后落库）；返回实际变更行数，未知 cg_id 跳过。 */
    suspend fun refreshCmcIds(cmcByCgId: Map<String, String>): Int

    // ---------- T3.2 消歧归一 + 映射冻结（exchange_coin_map） ----------

    /**
     * 四级消歧（共享规范 §6）：先查 [exchange]/[asset] 既有映射（存在即复用，FROZEN_MAP）；
     * 无则按 CoinResolver 规则链消歧；自动消歧唯一结果且 [exchange] 非空时固化 source=AUTO（后续自动复用）；
     * 仍歧义返回 [Resolution.Ambiguous] 候选，由调用方 UI 让用户选择后调 [freezeMapping] 固化 MANUAL。
     */
    suspend fun resolve(
        exchange: String?,
        asset: String,
        context: ResolveContext = ResolveContext(),
    ): Resolution

    /** 用户选择固化（source=MANUAL，一次性决策后续自动复用；已存在映射时覆盖为 MANUAL + 新币种）。 */
    suspend fun freezeMapping(exchange: String, asset: String, coinId: Long)

    /** 只读查询既有映射（不存在返回 null）。 */
    suspend fun mappingFor(exchange: String, asset: String): CatalogCoin?

    /**
     * 反向映射查询（M8 持仓校准：把 coins.id 映射回交易所资产符号，用于在 fetchBalances
     * 结果中定位该币种的余额行）；该交易所在映射表中无此币种时返回 null（调用方按 symbol 兜底）。
     */
    suspend fun exchangeAssetFor(exchange: String, coinId: Long): String?

    // ---------- T3.3 法币归一（共享规范 §3） ----------

    /**
     * 计价/费用侧法币归一分诊：法币且孪生映射成立 -> [FiatLeg.TwinStable]（1:1 联动，USD->USDT 币）；
     * 已知法币无孪生映射（或孪生稳定币不在目录）-> [FiatLeg.ThirdCurrency]（第三币种语义）；
     * 非法币 -> null（普通币种走目录解析）。BTC/USD -> quote=USDT；EUR -> EURC。
     */
    suspend fun fiatQuoteLeg(quoteSymbol: String): FiatLeg?
}

/** 消歧上下文（四级的上下文/合约输入）。 */
data class ResolveContext(
    /** 已固定的 quote 侧 coins.id（交易对上下文约束用）。 */
    val quoteCoinId: Long? = null,
    /** 合约平台键（CoinGecko platforms 名，如 "ethereum"）；空 = 任意平台。 */
    val contractChain: String? = null,
    /** 链上合约地址（精确匹配，不区分大小写）。 */
    val contractAddress: String? = null,
)

/** 消歧结果。 */
sealed interface Resolution {
    /** 解析命中（method 记录命中路径，审计用）。 */
    data class Unique(val coin: CatalogCoin, val method: ResolveMethod) : Resolution

    /** 仍歧义：候选列表（UI 呈现 名称/排名，用户选择后 freezeMapping）。 */
    data class Ambiguous(val candidates: List<CatalogCoin>) : Resolution

    /** 目录中无同名币种 / 合约地址无命中 / 两腿同币种退化对。 */
    data object NotFound : Resolution
}

/** 命中路径（FROZEN_MAP = exchange_coin_map 复用；其余对应共享规范 §6 规则 ①–③）。 */
enum class ResolveMethod(val storageValue: String) {
    FROZEN_MAP("frozen_map"),
    EXACT_SYMBOL("exact_symbol"),
    CONTEXT("context"),
    CONTRACT("contract"),
    RANK("rank"),
}

/** [CoinCatalog.fiatQuoteLeg] 的计价腿归一结果。 */
sealed interface FiatLeg {
    /** 1:1 孪生映射成立：USD->USDT 等（联动计价侧持仓与余额校验，共享规范 §3）。 */
    data class TwinStable(val fiatCode: String, val stableCoin: CatalogCoin) : FiatLeg

    /** 第三币种语义：无孪生映射或孪生稳定币暂缺（仅计入基础币种持仓与成本）。 */
    data class ThirdCurrency(val fiatCode: String) : FiatLeg
}
