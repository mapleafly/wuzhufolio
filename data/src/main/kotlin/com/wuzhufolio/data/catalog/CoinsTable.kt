package com.wuzhufolio.data.catalog

import org.jetbrains.exposed.v1.core.Table

/** coins 表映射（DDL = M004；data-model §2.9 + contracts 勘误列「各链合约地址」，见模块记录 M3.md §5）。 */
object CoinsTable : Table("coins") {
    val id = integer("id").autoIncrement()
    val cgId = varchar("cg_id", 128)
    val cmcId = varchar("cmc_id", 32).nullable()
    val symbol = varchar("symbol", 32)
    val name = text("name")
    val status = varchar("status", 16)
    val displayPrecision = integer("display_precision")
    val contracts = text("contracts")
    val updatedAt = varchar("updated_at", 40)

    override val primaryKey = PrimaryKey(id)
}
