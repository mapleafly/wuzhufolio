package com.wuzhufolio.data.catalog

import org.jetbrains.exposed.v1.core.Table

/** exchange_coin_map 表映射（DDL = M005；data-model §2.10）。键（exchange, exchange_asset）大写归一存储。
 * Kotlin 属性名 mappingSource（"source" 遮蔽 ColumnSet.source 成员）；DB 列名保持 source。 */
object ExchangeCoinMapTable : Table("exchange_coin_map") {
    val id = integer("id").autoIncrement()
    val exchange = varchar("exchange", 64)
    val exchangeAsset = varchar("exchange_asset", 64)
    val coinId = integer("coin_id")
    val mappingSource = varchar("source", 8)
    val createdAt = varchar("created_at", 40)
    val updatedAt = varchar("updated_at", 40)

    override val primaryKey = PrimaryKey(id)
}
