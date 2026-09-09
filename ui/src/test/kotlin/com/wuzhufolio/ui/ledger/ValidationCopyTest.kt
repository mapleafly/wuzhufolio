package com.wuzhufolio.ui.ledger

import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.LedgerErrorCode
import com.wuzhufolio.domain.ledger.LedgerValidationException
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 违例文案映射（api-contracts §4 / interaction V5/V7 同构/V9）。
 * 2026-09-08 修复轮：REPLAY_CONFLICT 文案须定位到具体冲突记录（时间/方向/数量/交易对）。
 */
class ValidationCopyTest {

    @Test
    fun insufficientBalanceCopyIsActionable() {
        val copy = TransactionCopy.validationCopy(
            LedgerValidationException(LedgerErrorCode.INSUFFICIENT_BALANCE, "USDT", BigDecimal("5000")),
        )
        assertTrue(copy.contains("USDT 余额不足"))
        assertTrue(copy.contains("导入交易 (CSV)"))
    }

    @Test
    fun replayConflictCopyNamesTheConflictingRecord() {
        val error = LedgerValidationException(
            code = LedgerErrorCode.REPLAY_CONFLICT,
            coinSymbol = "BTC",
            shortage = BigDecimal("0.1"),
            conflictAt = Instant.parse("2026-06-01T09:00:00Z"),
            conflictSide = Side.SELL,
            conflictPair = "BTC/USDT",
            conflictQuantity = BigDecimal("0.1"),
        )
        val copy = TransactionCopy.validationCopy(error)
        assertTrue(copy.contains("BTC 持仓为负"))
        assertTrue(copy.contains("卖出"), copy)
        assertTrue(copy.contains("0.1"))
        assertTrue(copy.contains("BTC/USDT"))
        assertTrue(copy.contains("请先调整或删除该冲突记录"), copy)
    }

    @Test
    fun replayConflictWithoutContextFallsBackToGenericCopy() {
        val copy = TransactionCopy.validationCopy(
            LedgerValidationException(LedgerErrorCode.REPLAY_CONFLICT, "BTC"),
        )
        assertEquals("该操作将导致 BTC 持仓为负（与已有记录冲突），请先调整或删除该冲突记录", copy)
    }
}
