package com.wuzhufolio.domain.ledger

import java.math.BigDecimal

/**
 * 费率规则设置用例（M7 T7.2 端到端可用性补口 · 2026-09-07 GUI 走查修复轮）。
 *
 * 口径：fee_rules 表（M010）与 FeeRuleRepository 已随 M7 落地；完整设置页与 CRUD 归 M10（T10.1），
 * 但「自动计算手续费」在无任何规则时不可用——本接口提供**最小 CRUD**（全局默认 + 交易所规则增删），
 * 供 M7 表单端到端验证；M10 整页接管时迁移/扩展（登记见模块记录 M7 §8）。
 *
 * 费率 = 百分比数值（如 0.1 = 0.1%），与 fee_rules.buy_rate/sell_rate 存储口径一致。
 */
interface FeeRuleService {

    /** 当前账户费率规则（交易所规则在前、全局默认在后）。 */
    suspend fun listRules(): List<FeeRuleRow>

    /** 设置/覆盖全局默认费率（exchange = null）。 */
    suspend fun saveGlobal(buyPercent: BigDecimal, sellPercent: BigDecimal)

    /** 设置/覆盖交易所费率（[exchange] 非空，大写存储）。 */
    suspend fun saveExchange(exchange: String, buyPercent: BigDecimal, sellPercent: BigDecimal)

    /**
     * 编辑既有交易所费率（M10 T10.1 完整 CRUD）：按行 [id] 定位，写入新名称与新费率。
     * 交易所名变更 = 旧去重键行删除 + 新键行落库（fee_rules 备份去重键 = account+exchange，
     * 同账户内键迁移；PRD 5.2-6 去重语义不变）。[id] 不存在或非本账户行时抛 IllegalArgumentException。
     */
    suspend fun saveExchangeEdit(id: Long, exchange: String, buyPercent: BigDecimal, sellPercent: BigDecimal)

    /** 删除规则（幂等）。 */
    suspend fun removeRule(id: Long)
}

/** 费率规则视图行（[exchange] = null 表示全局默认）。 */
data class FeeRuleRow(
    val id: Long,
    val exchange: String?,
    val buyPercent: BigDecimal,
    val sellPercent: BigDecimal,
)

/**
 * 费率规则校验（百分比 0–100；供 UI 与服务共用）。
 *
 * M12 T12.4：返回**类型化**错误码而非中文文案——原实现返回中文字符串，en 档会漏出中文
 * （界面逻辑与展示文案绑在领域层）。文案映射见 ui/i18n/LedgerStrings.feeRuleInvalid。
 */
object FeeRulePolicy {
    val MAX_PERCENT: BigDecimal = BigDecimal(100)

    /** 校验失败原因（null = 通过）。 */
    enum class Error {
        /** 未填写。 */
        EMPTY,

        /** 负数。 */
        NEGATIVE,

        /** 超过 100%。 */
        TOO_LARGE,
    }

    /** 返回校验错误（null = 通过）。 */
    fun validate(percent: BigDecimal?): Error? = when {
        percent == null -> Error.EMPTY
        percent.signum() < 0 -> Error.NEGATIVE
        percent > MAX_PERCENT -> Error.TOO_LARGE
        else -> null
    }
}
