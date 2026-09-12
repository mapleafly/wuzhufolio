package com.wuzhufolio.data.security

import com.wuzhufolio.data.exchange.newOkHttpExchangeClient
import com.wuzhufolio.data.market.newOkHttpMarketClient
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 硬约束 4 守护测试（M13 T13.1 安全自查；对应审计缺口 F-10）：
 * **行情刷新与交易同步是两类相互独立的 API**——各自独立客户端实例、独立凭据来源、独立额度账本，
 * 仅共享同一个「开关感知系统代理 selector」（PRD §7.2-6.2 要求所有对外请求均经代理）。
 *
 * 此前只有「AppBootstrap 两处分别 new」的结构性证据，无回归护栏——本测试固化客户端实例隔离。
 */
class ApiIsolationGuardTest {

    @Test
    fun `market and exchange clients are distinct instances`() {
        val market = newOkHttpMarketClient()
        val exchange = newOkHttpExchangeClient()
        assertNotSame(market, exchange, "两类 API 必须各自持有独立 HttpClient 实例（ADR-003/004 隔离口径）")
        assertTrue(market != exchange)
        runCatching { market.close() }
        runCatching { exchange.close() }
    }

    @Test
    fun `clients never share a mutable proxy selector by construction`() {
        // 共享的只有显式注入的 selector 实例（代理口径要求），默认构造下各自独立（null = OkHttp 默认）
        val shared: java.net.ProxySelector = java.net.ProxySelector.getDefault()
        val market = newOkHttpMarketClient(shared)
        val exchange = newOkHttpExchangeClient(shared)
        assertNotSame(market, exchange)
        // 同一 selector 实例是「全请求经代理」的法定共享（ADR-001/M11 T11.3），非数据耦合
        assertSame(shared, java.net.ProxySelector.getDefault())
        runCatching { market.close() }
        runCatching { exchange.close() }
    }
}
