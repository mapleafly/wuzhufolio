package com.wuzhufolio.domain.catalog

/**
 * 币种消歧纯规则（T3.2，共享规范 §6 四级消歧；domain 无 IO，候选集/排名由调用方注入）。
 *
 * 候选池 = 目录中与目标 ticker 精确同名的币种行；[CatalogCoin.status] == ACTIVE 优先，
 * 无 ACTIVE 时退而取全部同名行（DELISTED/UNTRACKED 资产仍可显式映射，避免历史资产无法解析）。
 *
 * 规则链（对应共享规范 §6「消歧规则（按优先级）」）：
 * ① 交易对上下文约束（[ResolveContext.quoteCoinId] 已固定）：排除与 quote 侧**同一币种**的候选
 *    （防两腿同币种的退化对）；排除后唯一 -> [ResolveMethod.CONTEXT]；排除后为空 -> 退化对，[Resolution.NotFound]。
 * ② 合约地址精确匹配（[ResolveContext.contractAddress]）：命中任一平台合约（chain 指定时只查该链，
 *    不区分大小写）；**有地址但无候选命中 -> [Resolution.NotFound]**（宁可未知不猜测，防误配）；
 *    命中唯一 -> [ResolveMethod.CONTRACT]；命中多个 -> 收窄候选集后继续规则③/④。
 * ③ 市值排名最高（前 1000 名预缓存，[MarketRankProvider]）：存在**唯一最小排名** -> [ResolveMethod.RANK]；
 *    无排名候选或并列 -> 进入规则④（并列不会把其中一个当"最高"）。
 * ④ 仍歧义 -> [Resolution.Ambiguous]（候选供 UI 列出；选择后由调用方 [CoinCatalog.freezeMapping]
 *    固化 source=MANUAL——一次性决策、后续自动复用，共享规范 §6「映射冻结」）。
 *
 * 注：exchange_coin_map 既有映射的复用（FROZEN_MAP）在规则链之前由 [CoinCatalog.resolve] 完成。
 */
object CoinResolver {

    /** 单次解析输入：候选集 + 消歧上下文 + 排名取用函数。 */
    data class RuleInput(
        val candidates: List<CatalogCoin>,
        val quoteCoinId: Long? = null,
        val contractChain: String? = null,
        val contractAddress: String? = null,
        val rankOf: (CatalogCoin) -> Int? = { null },
    )

    @Suppress("ReturnCount") // 规则链每级一个明确出口（NotFound/Unique/Ambiguous 逐级收敛），拆分反而模糊语义
    fun resolve(input: RuleInput): Resolution {
        var pool = if (input.candidates.isEmpty()) {
            return Resolution.NotFound
        } else {
            val active = input.candidates.filter { it.status == CoinStatus.ACTIVE }
            active.ifEmpty { input.candidates }
        }

        // 规则①：quote 侧已知时排除同币种冲突项
        if (input.quoteCoinId != null) {
            val narrowed = pool.filter { it.id != input.quoteCoinId }
            if (narrowed.isEmpty()) return Resolution.NotFound // 两腿同币种 = 退化交易对（如归一后 USDT/USD）
            if (narrowed.size == 1 && narrowed.size < pool.size) {
                return Resolution.Unique(narrowed.first(), ResolveMethod.CONTEXT)
            }
            pool = narrowed
        }
        if (pool.size == 1) {
            return Resolution.Unique(pool.first(), ResolveMethod.EXACT_SYMBOL)
        }

        // 规则②：合约地址精确匹配（无命中 = 未知资产，不猜测降级）
        val address = input.contractAddress?.trim()?.lowercase()
        if (!address.isNullOrEmpty()) {
            val matched = pool.filter { coin -> matchesContract(coin, input.contractChain, address) }
            if (matched.isEmpty()) return Resolution.NotFound
            pool = matched
            if (pool.size == 1) {
                return Resolution.Unique(pool.first(), ResolveMethod.CONTRACT)
            }
        }

        // 规则③：市值排名唯一最小才成立
        val ranked = pool.mapNotNull { coin -> input.rankOf(coin)?.let { coin to it } }
        if (ranked.isNotEmpty()) {
            val minRank = ranked.minOf { it.second }
            val winners = ranked.filter { it.second == minRank }
            if (winners.size == 1) {
                return Resolution.Unique(winners.first().first, ResolveMethod.RANK)
            }
        }

        // 规则④：仍歧义 -> UI 候选（有排名的靠前升序，无排名殿后，同名按名称稳定排序）
        val ordered = pool.sortedWith(
            compareBy(
                { input.rankOf(it) == null },
                { input.rankOf(it) ?: Int.MAX_VALUE },
                { it.name },
            ),
        )
        return Resolution.Ambiguous(ordered)
    }

    private fun matchesContract(coin: CatalogCoin, chain: String?, addressLower: String): Boolean =
        coin.contracts.any { (platform, contract) ->
            val platformOk = chain == null || platform.equals(chain, ignoreCase = true)
            platformOk && contract.trim().lowercase() == addressLower
        }
}
