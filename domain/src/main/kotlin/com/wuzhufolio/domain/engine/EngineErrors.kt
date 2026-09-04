package com.wuzhufolio.domain.engine

import java.math.BigDecimal
import java.time.Instant

/**
 * 严格重放负持仓违例（PRD 全局说明「重放校验规则」）：手动新增/编辑/删除交易或资金记录时，
 * 重放过程中任一时刻某币种持仓为负 → 阻止操作。UI 文案（如「该笔增资已被后续买入消耗，无法调小金额，
 * 请先调整后续交易」「XX 余额不足，请先记录转入（增资）」）由调用层按 [coinId]/[eventId] 映射
 * （PRD 故事 7.1 / interaction V 系，映射落 M7/M8）。
 */
class NegativePositionException(
    val coinId: String,
    val eventId: String,
    val at: Instant,
    val shortage: BigDecimal,
) : RuntimeException(
    "replay would drive $coinId negative at event $eventId ($at) by ${shortage.abs()}",
)

/** 持仓校准执行前提不满足：校准需校准时市价可得（快照或行情 API），行情完全不可用时不允许校准（PRD 全局说明）。 */
class CalibrationPriceUnavailableException(coinId: String) :
    RuntimeException("market price unavailable for reconciliation of $coinId")
