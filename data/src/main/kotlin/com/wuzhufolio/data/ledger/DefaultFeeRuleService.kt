package com.wuzhufolio.data.ledger

import com.wuzhufolio.data.accounts.ActiveSessionStore
import com.wuzhufolio.domain.ledger.FeeRuleRow
import com.wuzhufolio.domain.ledger.FeeRuleService
import java.math.BigDecimal

/**
 * 费率规则设置实现（M7 GUI 走查修复轮 · 最小 CRUD；M10 T10.1 整页接管时迁移/扩展）。
 * 经 FeeRuleRepository（M010 fee_rules 表，rate TEXT 勘误链）；校验口径见 domain FeeRulePolicy。
 */
class DefaultFeeRuleService(
    private val sessions: ActiveSessionStore,
    private val repository: FeeRuleRepository,
) : FeeRuleService {

    override suspend fun listRules(): List<FeeRuleRow> {
        val accountId = sessions.get()?.account?.id ?: return emptyList()
        return repository.listEntries(accountId).map {
            FeeRuleRow(
                id = it.id.toLong(),
                exchange = it.exchange,
                buyPercent = it.buyRatePercent,
                sellPercent = it.sellRatePercent,
            )
        }
    }

    override suspend fun saveGlobal(buyPercent: BigDecimal, sellPercent: BigDecimal) {
        requirePercent(buyPercent)
        requirePercent(sellPercent)
        repository.upsert(requireAccount(), "", buyPercent, sellPercent)
    }

    override suspend fun saveExchange(exchange: String, buyPercent: BigDecimal, sellPercent: BigDecimal) {
        val key = exchange.trim().uppercase()
        require(key.isNotEmpty()) { "交易所名称不能为空" }
        requirePercent(buyPercent)
        requirePercent(sellPercent)
        repository.upsert(requireAccount(), key, buyPercent, sellPercent)
    }

    override suspend fun saveExchangeEdit(
        id: Long,
        exchange: String,
        buyPercent: BigDecimal,
        sellPercent: BigDecimal,
    ) {
        val accountId = requireAccount()
        val key = exchange.trim().uppercase()
        require(key.isNotEmpty()) { "交易所名称不能为空" }
        requirePercent(buyPercent)
        requirePercent(sellPercent)
        val current = repository.listEntries(accountId).firstOrNull { it.id.toLong() == id }
            ?: throw IllegalArgumentException("费率规则不存在或已删除")
        require(current.exchange != null) { "全局默认费率请在「全局默认费率」行编辑" }
        if (current.exchange == key) {
            repository.upsert(accountId, key, buyPercent, sellPercent) // 同键覆盖（编辑费率）
        } else {
            // 改名 = 键迁移：新键 upsert + 旧行删除（fee_rules 备份去重键 account+exchange 联动，M10 T10.1）
            repository.upsert(accountId, key, buyPercent, sellPercent)
            repository.delete(accountId, id.toInt())
        }
    }

    override suspend fun removeRule(id: Long) {
        repository.delete(requireAccount(), id.toInt())
    }

    private fun requireAccount(): Int =
        sessions.get()?.account?.id ?: error("未登录：请先登录账户")

    private fun requirePercent(percent: BigDecimal) {
        require(percent.signum() >= 0 && percent <= BigDecimal(100)) { "费率需在 0–100 之间（百分比）" }
    }
}
