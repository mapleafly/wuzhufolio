package com.wuzhufolio.domain.ledger

import com.wuzhufolio.domain.engine.NegativeViolation
import com.wuzhufolio.domain.engine.RecordSource
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.engine.TradeEvent
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 重放违例分类（T7.1 · api-contracts §4：V5 / V7 同构 / V9 文案路由输入）。
 */
class ReplayConflictClassifierTest {

    private val now: Instant = Instant.parse("2026-08-30T08:00:00Z")

    private fun violation(coinId: String, eventId: String) = NegativeViolation(
        coinId = coinId,
        eventId = eventId,
        at = now,
        seq = 1L,
        shortage = BigDecimal("-100"),
    )

    private fun trade(
        id: String,
        base: String,
        quote: String,
        side: Side,
    ): TradeEvent = TradeEvent(
        id = id,
        at = now,
        baseCoin = base,
        quoteCoin = quote,
        side = side,
        price = BigDecimal("50000"),
        quantity = BigDecimal("1"),
        fee = BigDecimal.ZERO,
        feeCoin = quote,
        legFiat = BigDecimal("50000"),
        feeFiat = BigDecimal.ZERO,
        source = RecordSource.Manual,
        seq = 1L,
    )

    @Test
    fun buyViolatingQuoteCoinAtOwnEventMapsToInsufficientBalance() {
        val op = trade("e1", "bitcoin", "tether", Side.BUY)
        val code = ReplayConflictClassifier.classify(violation("tether", "e1"), op)
        assertEquals(LedgerErrorCode.INSUFFICIENT_BALANCE, code)
    }

    @Test
    fun sellViolatingBaseCoinAtOwnEventMapsToInsufficientPosition() {
        val op = trade("e1", "bitcoin", "tether", Side.SELL)
        val code = ReplayConflictClassifier.classify(violation("bitcoin", "e1"), op)
        assertEquals(LedgerErrorCode.INSUFFICIENT_POSITION, code)
    }

    @Test
    fun violationAtAnotherEventOrNonOwnRoleCoinMapsToReplayConflict() {
        val op = trade("e2", "bitcoin", "tether", Side.BUY)
        // 违例位点在操作事件但币种不是本笔计价腿（如编辑/删除影响其他事件）
        assertEquals(
            LedgerErrorCode.REPLAY_CONFLICT,
            ReplayConflictClassifier.classify(violation("ethereum", "e2"), op),
        )
        // 违例位点在另一事件
        assertEquals(
            LedgerErrorCode.REPLAY_CONFLICT,
            ReplayConflictClassifier.classify(violation("tether", "e1"), op),
        )
    }

    @Test
    fun deletionWithoutOperatedEventAlwaysMapsToReplayConflict() {
        assertEquals(
            LedgerErrorCode.REPLAY_CONFLICT,
            ReplayConflictClassifier.classify(violation("bitcoin", "e1"), null),
        )
    }
}
