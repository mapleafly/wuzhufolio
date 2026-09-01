package com.wuzhufolio.data

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.hello.HelloChain
import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T0.4 验收：hello 链路端到端（迁移 -> 读设置 -> 脱敏日志）。 */
class HelloChainTest {

    private lateinit var db: WzDatabase
    private lateinit var logbackLogger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("wuzhufolio-test")
        db = WzDatabase(dir.resolve("test.db"))
        logbackLogger = LoggerFactoryHolder.logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logbackLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        logbackLogger.detachAppender(appender)
        db.close()
    }

    @Test
    fun `hello chain migrates reads settings and logs masked line`() {
        val result = HelloChain(db, SettingsRepository(db), logbackLogger).run()

        assertEquals(2, result.schemaVersion)
        assertEquals(ThemeMode.LIGHT, result.theme)
        assertEquals(PnlColorScheme.GREEN_UP, result.pnlScheme)
        assertEquals("USD", result.settings["fiat"])

        val messages = appender.list.map { it.formattedMessage }
        assertTrue(messages.any { it.contains("hello-chain ok") }, "hello log line expected: " + messages)
        val line = messages.first { it.contains("hello-chain ok") }
        assertTrue(line.contains("market_api_key=****"), "key value must be masked: " + line)
        assertFalse(
            line.contains("0123456789abcdef0123456789abcdef"),
            "raw secret must never reach the log: " + line,
        )
    }

    @Test
    fun `hello chain is re-runnable against existing database`() {
        val repo = SettingsRepository(db)
        HelloChain(db, repo, logbackLogger).run()
        val second = HelloChain(db, repo, logbackLogger).run()
        assertEquals(2, second.schemaVersion)
        assertEquals(4, second.settings.size)
    }
}

private object LoggerFactoryHolder {
    val logger: Logger
        get() = org.slf4j.LoggerFactory.getLogger(HelloChain::class.java) as Logger
}
