package com.wuzhufolio.data.logging

import com.wuzhufolio.domain.redaction.LogRedactor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 本地日志文件轮转（M10 T10.2）：条数裁剪 / 90 天删档 / 导出与查看脱敏。 */
class LogRotatorTest {

    private fun tempDir(): Path = Files.createTempDirectory("wuzhufolio-logrot")

    @Test
    fun `active log trimmed to last max lines`() {
        val dir = tempDir()
        val log = dir.resolve("wuzhufolio.log")
        Files.write(log, (1..12).map { "2026-09-10 00:00:00.000 INFO [main] w - line-" + it })
        val now = Instant.parse("2026-09-10T01:00:00Z")
        // maxLines 注入小值验证裁剪语义（生产默认 = LogRotationPolicy.MAX_LINES 1 万条）
        val summary = LogRotator.rotate(dir, now, maxLines = 10)
        assertEquals(1, summary.trimmedFiles)
        assertEquals(2, summary.removedLines)
        val lines = Files.readAllLines(log)
        assertEquals(10, lines.size)
        assertEquals("2026-09-10 00:00:00.000 INFO [main] w - line-3", lines.first())
        assertEquals("2026-09-10 00:00:00.000 INFO [main] w - line-12", lines.last())
    }

    @Test
    fun `expired files deleted by 90 days rule`() {
        val dir = tempDir()
        val stale = dir.resolve("wuzhufolio.2026-01-01.0.log.gz")
        val fresh = dir.resolve("wuzhufolio.log")
        Files.write(stale, byteArrayOf(1))
        Files.write(fresh, "2026-09-10 00:00:00.000 INFO [main] w - ok".toByteArray())
        val now = Instant.parse("2026-09-10T01:00:00Z")
        Files.setLastModifiedTime(stale, FileTime.from(now.minusSeconds(91L * 24 * 3600)))
        Files.setLastModifiedTime(fresh, FileTime.from(now))
        val summary = LogRotator.rotate(dir, now)
        assertEquals(1, summary.deletedFiles)
        assertFalse(Files.exists(stale))
        assertTrue(Files.exists(fresh))
    }

    @Test
    fun `missing directory is a no-op`() {
        val summary = LogRotator.rotate(tempDir().resolve("none"), Instant.now())
        assertEquals(0, summary.deletedFiles)
        assertEquals(0, summary.trimmedFiles)
        assertEquals(0, summary.removedLines)
    }

    @Test
    fun `tailLines reads last N lines and exportTo writes redacted copy`() {
        val dir = tempDir()
        val log = dir.resolve("wuzhufolio.log")
        Files.write(
            log,
            listOf(
                "2026-09-10 00:00:00.000 INFO [main] w - hello-chain ok",
                "2026-09-10 00:00:01.000 INFO [main] w - market_api_key=CG-DEMO-0123456789abcdef0123456789abcdef",
            ),
        )
        val access = FileLogAccess(dir)
        val tail = access.tailLines(2)
        assertEquals(2, tail.size)
        assertTrue("hello-chain ok" in tail.first())

        val target = dir.resolve("export").resolve("wuzhufolio-export.log")
        val count = access.exportTo(target.toString())
        assertEquals(2, count)
        val exported = Files.readAllLines(target)
        assertEquals(2, exported.size)
        assertTrue("CG-DEMO-0123456789abcdef0123456789abcdef" !in exported.joinToString("\n"), "导出内容必须脱敏")
        assertTrue(LogRedactor.MASK in exported.joinToString("\n"))
    }

    @Test
    fun `exportTo missing log throws`() {
        val access = FileLogAccess(tempDir())
        val target = Files.createTempDirectory("wuzhufolio-exp").resolve("out.log")
        kotlin.test.assertFailsWith<java.io.IOException> { access.exportTo(target.toString()) }
    }
}
