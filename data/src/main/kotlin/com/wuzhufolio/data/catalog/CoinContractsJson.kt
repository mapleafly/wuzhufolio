package com.wuzhufolio.data.catalog

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * coins.contracts 列编解码（平台 -> 合约地址 JSON 载荷；CoinGecko /coins/list include_platform=true）。
 * 载荷损坏按空映射容忍——身份消歧退化为其余三级（上下文/排名/用户），原始数据仍可由每日目录刷新恢复。
 */
internal object CoinContractsJson {
    private val json = Json
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(contracts: Map<String, String>): String = json.encodeToString(mapSerializer, contracts)

    fun decode(text: String?): Map<String, String> =
        if (text.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString(mapSerializer, text) }.getOrDefault(emptyMap())
        }
}
