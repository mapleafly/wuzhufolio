package com.wuzhufolio.domain.catalog

/**
 * M3 币种主数据模型（共享规范 §6 / data-model §2.9–§2.10 / PRD §10-10/11）。
 *
 * 内部唯一标识 = CoinGecko id（[CatalogCoin.cgId]）；symbol 仅作展示与检索，不作为标识依据
 * （symbol 不唯一、币种更名时 CG id 不变而交易所 ticker 会变，见「币种标识与行情源决策分析」C 节）。
 */

/** coins.status 取值（data-model §2.9）：ACTIVE / DELISTED / UNTRACKED。 */
enum class CoinStatus(val storageValue: String) {
    ACTIVE("ACTIVE"),
    DELISTED("DELISTED"),
    UNTRACKED("UNTRACKED"),
    ;

    companion object {
        fun fromStorage(value: String?): CoinStatus =
            entries.firstOrNull { it.storageValue == value } ?: ACTIVE
    }
}

/** exchange_coin_map.source（data-model §2.10）：AUTO=自动消歧固化；MANUAL=用户确认固化。 */
enum class MappingSource(val storageValue: String) {
    AUTO("AUTO"),
    MANUAL("MANUAL"),
    ;

    companion object {
        fun fromStorage(value: String?): MappingSource =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}

/**
 * coins 目录行（全局公共数据：各账户共享、不随 .cpro 备份导出、全量覆盖恢复不清空——共享规范 §7/§8）。
 * [contracts] = 平台 -> 合约地址（CoinGecko /coins/list include_platform=true 载荷；消歧规则② 用；
 * 桌面端 coins 表实际迁移增列 contracts，勘误登记见模块记录 M3.md §5）。
 */
data class CatalogCoin(
    val id: Long,
    val cgId: String,
    val cmcId: String?,
    val symbol: String,
    val name: String,
    val status: CoinStatus,
    val displayPrecision: Int = 8,
    val contracts: Map<String, String> = emptyMap(),
)

/** CoinGecko /coins/list 单条解析产物（T3.1 缓存输入；网络拉取归 M5 行情链路，本模块只做落库/检索）。 */
data class CoinDirectoryEntry(
    val cgId: String,
    val symbol: String,
    val name: String,
    val contracts: Map<String, String> = emptyMap(),
)

/** [CoinCatalog.refreshDirectory] 汇总：本次实际新增/变更行数。 */
data class DirectoryRefreshSummary(val added: Int, val updated: Int)
