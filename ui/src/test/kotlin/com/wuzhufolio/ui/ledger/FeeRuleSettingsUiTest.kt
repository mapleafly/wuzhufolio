package com.wuzhufolio.ui.ledger

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.ledger.FeeRuleRow
import com.wuzhufolio.domain.ledger.FeeRuleService
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 设置页 · 手续费费率分组 UI 走查（M7 GUI 走查修复轮）：全局保存 / 交易所添加 / 删除 / 校验。
 */
@OptIn(ExperimentalTestApi::class)
class FeeRuleSettingsUiTest {

    private class FakeFeeRuleService : FeeRuleService {
        val rules: MutableList<FeeRuleRow> = mutableListOf()
        var globalSaved: Pair<BigDecimal, BigDecimal>? = null
        var exchangeSaved: Triple<String, BigDecimal, BigDecimal>? = null
        var exchangeEditSaved: Quad<Long, String, BigDecimal, BigDecimal>? = null
        var removedId: Long? = null

        /** 四元组（编辑用；Kotlin 无内建 Quad）。 */
        data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        override suspend fun listRules(): List<FeeRuleRow> = rules.toList()

        override suspend fun saveGlobal(buyPercent: BigDecimal, sellPercent: BigDecimal) {
            globalSaved = buyPercent to sellPercent
            rules.removeAll { it.exchange == null }
            rules.add(FeeRuleRow(100L, null, buyPercent, sellPercent))
        }

        override suspend fun saveExchange(exchange: String, buyPercent: BigDecimal, sellPercent: BigDecimal) {
            exchangeSaved = Triple(exchange, buyPercent, sellPercent)
            rules.removeAll { it.exchange == exchange.uppercase() }
            rules.add(FeeRuleRow(200L, exchange.uppercase(), buyPercent, sellPercent))
        }

        override suspend fun saveExchangeEdit(
            id: Long,
            exchange: String,
            buyPercent: BigDecimal,
            sellPercent: BigDecimal,
        ) {
            exchangeEditSaved = Quad(id, exchange, buyPercent, sellPercent)
            rules.removeAll { it.id == id }
            rules.removeAll { it.exchange == exchange.uppercase() }
            rules.add(FeeRuleRow(201L, exchange.uppercase(), buyPercent, sellPercent))
        }

        override suspend fun removeRule(id: Long) {
            // 先移除再置位（测试 waitUntil 观察 removedId——顺序反了会有竞态）
            rules.removeAll { it.id == id }
            removedId = id
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String, substring: Boolean = true): Int =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    @Test
    fun globalRatesSaveAndPersist() = runComposeUiTest {
        val svc = FakeFeeRuleService()
        setContent { FeeRuleSettingsSection(svc) }
        onNodeWithTag("fee-rule-settings").assertIsDisplayed()
        onNodeWithTag("fee-global-buy").performTextInput("0.1")
        onNodeWithTag("fee-global-sell").performTextInput("0.2")
        onNodeWithTag("fee-global-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.globalSaved != null }
        assertEquals(0, BigDecimal("0.1").compareTo(svc.globalSaved!!.first))
        waitUntil(timeoutMillis = 2_000) { textCount("全局默认费率已保存", substring = true) >= 1 }
    }

    @Test
    fun exchangeRuleAddAndRemove() = runComposeUiTest {
        val svc = FakeFeeRuleService()
        setContent { FeeRuleSettingsSection(svc) }
        onNodeWithTag("fee-exchange-name").performTextInput("BINANCE")
        onNodeWithTag("fee-exchange-buy").performTextInput("0.05")
        onNodeWithTag("fee-exchange-sell").performTextInput("0.05")
        onNodeWithTag("fee-exchange-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.exchangeSaved != null }
        assertEquals("BINANCE", svc.exchangeSaved!!.first)
        waitUntil(timeoutMillis = 2_000) { svc.rules.any { it.exchange == "BINANCE" } }
        waitUntil(timeoutMillis = 2_000) { textCount("BINANCE") >= 1 }
        onNodeWithTag("fee-rule-remove-BINANCE").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.removedId == 200L }
        assertTrue(svc.rules.none { it.exchange == "BINANCE" })
    }

    @Test
    fun invalidRateShowsError() = runComposeUiTest {
        val svc = FakeFeeRuleService()
        setContent { FeeRuleSettingsSection(svc) }
        onNodeWithTag("fee-global-buy").performTextInput("-1")
        onNodeWithTag("fee-global-sell").performTextInput("0.1")
        onNodeWithTag("fee-global-save").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount("费率不能为负") >= 1 }
        assertEquals(null, svc.globalSaved, "非法费率不得触达服务")
    }

    @Test
    fun exchangeSuggestionClickFillsInput() = runComposeUiTest {
        // M10 修复轮：交易所 = 可搜索选择（输入过滤候选，点击填入；自由输入保留）
        val svc = FakeFeeRuleService()
        setContent { FeeRuleSettingsSection(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("候选交易所") >= 1 }
        onNodeWithTag("fee-exchange-suggest-BINANCE", useUnmergedTree = true).performClick()
        onNodeWithTag("fee-exchange-buy").performTextInput("0.05")
        onNodeWithTag("fee-exchange-sell").performTextInput("0.05")
        onNodeWithTag("fee-exchange-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.exchangeSaved != null }
        assertEquals("BINANCE", svc.exchangeSaved!!.first, "点击候选填入后走正常保存路由")
    }

    @Test
    fun exchangeSuggestionsFilterByTypedQuery() = runComposeUiTest {
        val svc = FakeFeeRuleService().apply {
            rules.add(FeeRuleRow(200L, "BINANCE", BigDecimal("0.05"), BigDecimal("0.05")))
        }
        setContent { FeeRuleSettingsSection(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("候选交易所") >= 1 }
        // 输入不匹配前缀 → 候选过滤为空（建议行消失）
        onNodeWithTag("fee-exchange-name").performTextInput("OKX")
        waitUntil(timeoutMillis = 2_000) { textCount("候选交易所") == 0 }
        // 自由输入仍可保存（不依赖候选）
        onNodeWithTag("fee-exchange-buy").performTextInput("0.02")
        onNodeWithTag("fee-exchange-sell").performTextInput("0.02")
        onNodeWithTag("fee-exchange-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.exchangeSaved != null }
        assertEquals("OKX", svc.exchangeSaved!!.first)
    }

    @Test
    fun exchangeRuleEditPrefillsAndRoutesToEdit() = runComposeUiTest {
        // M10 T10.1：编辑既有规则（表单预填 → 保存修改 → saveExchangeEdit 路由）
        val svc = FakeFeeRuleService().apply {
            rules.add(FeeRuleRow(200L, "BINANCE", BigDecimal("0.05"), BigDecimal("0.06")))
        }
        setContent { FeeRuleSettingsSection(svc) }
        waitUntil(timeoutMillis = 2_000) { textCount("BINANCE") >= 1 }
        onNodeWithTag("fee-rule-edit-BINANCE").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount("保存修改") >= 1 }
        onNodeWithTag("fee-exchange-buy").performTextClearance()
        onNodeWithTag("fee-exchange-buy").performTextInput("0.1")
        onNodeWithTag("fee-exchange-save").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.exchangeEditSaved != null }
        assertEquals(200L, svc.exchangeEditSaved!!.first)
        assertEquals("BINANCE", svc.exchangeEditSaved!!.second)
        assertEquals(0, BigDecimal("0.1").compareTo(svc.exchangeEditSaved!!.third))
        assertTrue(svc.exchangeSaved == null, "编辑不得走新增路由")
    }
}
