package com.wuzhufolio.app.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M13 T13.1 加固端到端守护：**已配置的 logback 模式**（`app/src/main/resources/logback.xml`）必须
 * 把每条消息经 [RedactingMessageConverter] 渲染——即「脱敏漏斗」在真实配置下生效，而非仅类本身可用。
 *
 * 断言对象是 FILE appender 的 PatternLayoutEncoder 输出（与落盘内容同源），因此可拦下
 * 「conversionRule 被删/写错类名/新增 appender 忘记套用模式」一类回归。
 */
class LogbackRedactionFunnelTest {

    @Test
    fun `configured logback pattern routes every message through the redactor`() {
        val logDir = Files.createTempDirectory("wuzhufolio-logback-funnel")
        val previous = System.getProperty("wuzhufolio.logdir")
        System.setProperty("wuzhufolio.logdir", logDir.toString())
        val context = LoggerContext()
        try {
            val configurator = JoranConfigurator()
            configurator.context = context
            context.reset()
            configurator.doConfigure(javaClass.getResource("/logback.xml"))

            val root: Logger = context.getLogger(Logger.ROOT_LOGGER_NAME)
            val appender = root.getAppender("FILE")
            assertNotNull(appender, "logback.xml 应配置 FILE appender")
            val encoder = (appender as OutputStreamAppender<ILoggingEvent>).encoder as PatternLayoutEncoder

            val secret = "CG-DEMO-0123456789abcdef0123456789abcdef"
            val event = LoggingEvent(
                "test",
                context.getLogger("funnel"),
                Level.INFO,
                "market.coingecko_key=" + secret + " | refresh ok",
                null,
                null,
            )
            val rendered = String(encoder.encode(event), Charsets.UTF_8)
            assertFalse(
                rendered.contains(secret),
                "已配置的日志模式必须经脱敏转换器渲染（%msg 覆盖）——实际输出：$rendered",
            )
            assertTrue(rendered.contains("market.coingecko_key=****"), "键值形态遮蔽：" + rendered)
            assertTrue(rendered.contains("refresh ok"), "非敏感正文保留：" + rendered)
        } finally {
            context.stop()
            if (previous == null) {
                System.clearProperty("wuzhufolio.logdir")
            } else {
                System.setProperty("wuzhufolio.logdir", previous)
            }
        }
    }
}
