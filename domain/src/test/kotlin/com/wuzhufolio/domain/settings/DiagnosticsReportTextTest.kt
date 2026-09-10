package com.wuzhufolio.domain.settings

import com.wuzhufolio.domain.market.QuotaCallKind
import com.wuzhufolio.domain.redaction.LogRedactor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/** 诊断报告文本渲染（M10 T10.3）：内容受限清单逐项呈现 + 不含密钥/响应体。 */
class DiagnosticsReportTextTest {

    private fun report(logTail: List<String>) = DiagnosticsReport(
        appVersion = "0.1.0-dev",
        osName = "Linux",
        osVersion = "6.6",
        osArch = "amd64",
        schemaVersion = 12,
        marketCalls = mapOf(QuotaCallKind.CURRENT to 12, QuotaCallKind.HISTORY to 3),
        syncLogCount = 7,
        lastSyncAt = Instant.parse("2026-09-10T08:00:00Z"),
        logTail = logTail,
        generatedAt = Instant.parse("2026-09-10T09:00:00Z"),
    )

    @Test
    fun `renders restricted content list fields`() {
        val text = DiagnosticsReportText.render(
            report(listOf("2026-09-10 08:00:00.000 INFO [main] w - bootstrap ok | schema=12")),
        )
        listOf(
            "应用版本：0.1.0-dev",
            "Linux 6.6 (amd64)",
            "数据库 schema 版本：12",
            "CURRENT=12",
            "HISTORY=3",
            "同步调用计数（累计记录）：7",
            "最近同步：2026-09-10T08:00:00Z",
            "bootstrap ok",
        ).forEach { fragment -> assertTrue(fragment in text, "应含：" + fragment) }
        assertTrue("内容受限清单" in text)
    }

    @Test
    fun `renders given log tail verbatim redaction belongs to service contract`() {
        // 渲染为纯文本透传（报告数据 logTail 由 DefaultDiagnosticsService 保证已脱敏，见 DiagnosticsServiceTest）
        val text = DiagnosticsReportText.render(
            report(
                listOf(
                    "2026-09-10 08:00:00.000 INFO [main] w - bootstrap ok | schema=12",
                    "2026-09-10 08:00:01.000 INFO [main] w - market_api_key=" + LogRedactor.MASK,
                ),
            ),
        )
        assertTrue("bootstrap ok | schema=12" in text)
        assertTrue("market_api_key=" + LogRedactor.MASK in text, "已脱敏行原样呈现")
    }

    @Test
    fun `empty log tail renders placeholder`() {
        val text = DiagnosticsReportText.render(report(emptyList()))
        assertTrue("（无日志）" in text)
    }
}
