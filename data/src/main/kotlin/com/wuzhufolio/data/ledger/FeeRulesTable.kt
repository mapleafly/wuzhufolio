package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.engine.FeeRule
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.Instant

/**
 * fee_rules 表映射（DDL = M010；data-model §2.4 / PRD §10-7；账户级）。
 *
 * M7 只读消费（交易表单「自动计算手续费」，T7.2）；写入面仅仓库级 upsert/delete 供测试与
 * M10 费率 CRUD 复用（task-breakdown T10.1，拆分登记见模块记录 M7 §5）。
 * rate 存 TEXT 十进制串（百分比，如 0.1 = 0.1%）——勘误链同 transactions.price（模块记录 M5 §5）。
 */
object FeeRulesTable : Table("fee_rules") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    /** 交易所（空串 = 全局默认；存储大写，与 transactions.exchange 同口径）。 */
    val exchange = varchar("exchange", 24).default("")
    val buyRate = text("buy_rate")
    val sellRate = text("sell_rate")
    val createdAt = varchar("created_at", 40)

    override val primaryKey = PrimaryKey(id)
}

/** 费率规则行（含真实行 id——设置页删除/编辑定位用）。 */
data class FeeRuleEntry(
    val id: Int,
    val exchange: String?,
    val buyRatePercent: BigDecimal,
    val sellRatePercent: BigDecimal,
)

/** 费率规则仓库（M7 只读 + 最小写入面；完整 CRUD UI 归 M10）。 */
class FeeRuleRepository(private val gate: DbGate) {

    /** 账户全部费率规则（含行 id；交易所规则在前、全局规则殿后——FeeRateResolver 匹配序稳定）。 */
    suspend fun listEntries(accountId: Int): List<FeeRuleEntry> = gate.read {
        FeeRulesTable.selectAll()
            .where { FeeRulesTable.accountId eq accountId }
            .toList()
            .map { row ->
                FeeRuleEntry(
                    id = row[FeeRulesTable.id],
                    exchange = row[FeeRulesTable.exchange].takeIf { it.isNotBlank() },
                    buyRatePercent = BigDecimal(row[FeeRulesTable.buyRate]),
                    sellRatePercent = BigDecimal(row[FeeRulesTable.sellRate]),
                )
            }
            .sortedWith(compareBy({ it.exchange == null }, { it.exchange ?: "" }))
    }

    /** 账户全部费率规则（引擎匹配输入）。 */
    suspend fun list(accountId: Int): List<FeeRule> = listEntries(accountId).map {
        FeeRule(
            exchange = it.exchange,
            buyRatePercent = it.buyRatePercent,
            sellRatePercent = it.sellRatePercent,
        )
    }

    /** upsert（唯一键 (account_id, exchange)；exchange 空串 = 全局默认）。返回行 id。 */
    suspend fun upsert(accountId: Int, exchange: String, buyRate: BigDecimal, sellRate: BigDecimal): Int =
        gate.write {
            val key = exchange.trim().uppercase()
            val existing = FeeRulesTable.selectAll()
                .where { (FeeRulesTable.accountId eq accountId) and (FeeRulesTable.exchange eq key) }
                .singleOrNull()
            if (existing == null) {
                FeeRulesTable.insert {
                    it[FeeRulesTable.accountId] = accountId
                    it[FeeRulesTable.exchange] = key
                    it[FeeRulesTable.buyRate] = buyRate.toPlainString()
                    it[FeeRulesTable.sellRate] = sellRate.toPlainString()
                    it[FeeRulesTable.createdAt] = Instant.now().toString()
                } get FeeRulesTable.id
            } else {
                FeeRulesTable.update({ FeeRulesTable.id eq existing[FeeRulesTable.id] }) {
                    it[FeeRulesTable.buyRate] = buyRate.toPlainString()
                    it[FeeRulesTable.sellRate] = sellRate.toPlainString()
                }
                existing[FeeRulesTable.id]
            }
        }

    /** 删除规则（M10 CRUD 复用；幂等）。 */
    suspend fun delete(accountId: Int, id: Int) = gate.write {
        FeeRulesTable.deleteWhere {
            (FeeRulesTable.id eq id) and (FeeRulesTable.accountId eq accountId)
        }
    }
}
