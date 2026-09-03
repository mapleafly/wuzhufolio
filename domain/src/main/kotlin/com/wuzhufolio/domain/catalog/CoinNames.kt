package com.wuzhufolio.domain.catalog

/**
 * 代码归一工具（共享规范 §6「输入归一化」：基于 coins 目录做大小写归一，如 "usdt" -> USDT；
 * 消除 "usdt"/"USDT" 大小写分裂）。
 *
 * 别名归一（如 Kraken 的 XBT=Bitcoin、交易所专有资产 BTCB/BETH 映射自身 CG 资产）不由字符串猜测，
 * 由 exchange_coin_map 显式映射承担（共享规范 §6 / 决策分析 C-3）。
 */
object CoinNames {

    /** 币种 ticker 归一：去首尾空白 + 大写（USDT/USDC 等）。 */
    fun normalizeTicker(raw: String): String = raw.trim().uppercase()

    /** 交易所/资产键归一：与 [normalizeTicker] 同语义（exchange_coin_map 键一律大写存储）。 */
    fun normalizeCode(raw: String): String = raw.trim().uppercase()

    /** CoinGecko id 归一：小写 slug（render-token 等）。 */
    fun normalizeCgId(raw: String): String = raw.trim().lowercase()
}
