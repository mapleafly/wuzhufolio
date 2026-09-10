package com.wuzhufolio.data.logging

import com.wuzhufolio.domain.redaction.LogRotationPolicy
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

/**
 * 本地日志文件轮转执行器（M10 · T10.2 · interaction.md §2.6「日志轮转」）：
 *
 * - 条数：活动日志（`*.log`，非 `.gz`）超过 [LogRotationPolicy.MAX_LINES] 行时重写保留最新段
 *   （临时文件 + 原子替换，避免半写状态）；
 * - 时间：`*.log` / `*.log.gz` 最后修改早于 90 天的整档删除（logback maxHistory 同步 90，双保险）。
 *
 * 启动时执行一次（AppBootstrap），同步/手动触发可复用；删除与裁剪计数仅入日志摘要（不含日志原文）。
 */
object LogRotator {

    private val DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** 轮转结果摘要（日志化用；不含日志原文）。 */
    data class Summary(
        val deletedFiles: Int,
        val trimmedFiles: Int,
        val removedLines: Int,
    ) {
        override fun toString(): String =
            "deletedFiles=" + deletedFiles + " trimmedFiles=" + trimmedFiles + " removedLines=" + removedLines
    }

    /** 轮转 [logDir] 下的日志文件；目录不存在为空操作（首次启动）。[maxLines] 测试可注入（默认 PRD 上限）。 */
    fun rotate(
        logDir: Path,
        now: Instant = Instant.now(),
        maxLines: Int = LogRotationPolicy.MAX_LINES,
    ): Summary {
        if (!Files.isDirectory(logDir)) return Summary(0, 0, 0)
        var deleted = 0
        var trimmed = 0
        var removedLines = 0
        val cutoff = LogRotationPolicy.cutoff(now)
        Files.list(logDir).use { stream: Stream<Path> ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".log") || it.fileName.toString().endsWith(".log.gz") }
                .forEach { file ->
                    val modified = Files.getLastModifiedTime(file).toInstant()
                    if (LogRotationPolicy.isExpired(modified, now)) {
                        runCatching { Files.deleteIfExists(file) }
                            .onSuccess { if (it) deleted++ }
                        return@forEach
                    }
                    if (file.fileName.toString().endsWith(".gz")) return@forEach // 压缩档不做行级裁剪
                    val result = trimIfNeeded(file, cutoff, maxLines)
                    if (result != null) {
                        trimmed++
                        removedLines += result
                    }
                }
        }
        return Summary(deletedFiles = deleted, trimmedFiles = trimmed, removedLines = removedLines)
    }

    /**
     * 超上限时原子重写保留最新段；返回删除行数（未超限返回 null）。
     * IO 失败按「本次不裁剪」处理（下次启动再试；logback 分卷护栏兜底）——异常吞没为既定语义。
     */
    @Suppress("ReturnCount", "SwallowedException") // 早退（读失败/未超限）+ IO 失败自愈语义（吞异常不中断启动）
    private fun trimIfNeeded(file: Path, cutoff: Instant, maxLines: Int): Int? {
        val lines = try {
            Files.readAllLines(file)
        } catch (e: IOException) {
            return null
        }
        // 时间口径（行级）：解析失败/无时间戳的行不删（宁多勿丢）；带 UTC 时间戳且早于 cutoff 的行删除
        val recent = lines.filter { line -> !isOldLine(line, cutoff) }
        val removedByAge = lines.size - recent.size
        val kept = LogRotationPolicy.keepLastLines(recent, maxLines)
        val removedByCount = recent.size - kept.size
        val removed = removedByAge + removedByCount
        if (removed <= 0) return null
        return try {
            val tmp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
            Files.write(tmp, kept)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            removed
        } catch (e: IOException) {
            null // 裁剪失败不影响启动（下次再试；logback 分卷护栏兜底）
        }
    }

    /** 行首带时间戳（logback pattern `yyyy-MM-dd ...`）且早于截止时刻；解析失败按「不删」处理（吞异常为既定语义）。 */
    @Suppress("SwallowedException") // 时间戳解析失败 = 非日志正文行，按「不删」处理（宁多勿丢）
    private fun isOldLine(line: String, cutoff: Instant): Boolean {
        if (line.length < 19) return false
        val head = line.take(19)
        return try {
            java.time.LocalDateTime.parse(head, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .toInstant(ZoneOffset.UTC) < cutoff
        } catch (e: Exception) {
            false
        }
    }

    /** 最近 [maxLines] 行（诊断报告/查看日志共用读取口径；文件缺失/IO 失败返回空）。 */
    @Suppress("SwallowedException") // 读失败按空列表处理（UI 显示「暂无日志」，不中断）
    fun tailLines(file: Path, maxLines: Int): List<String> {
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            LogRotationPolicy.keepLastLines(Files.readAllLines(file), maxLines)
        } catch (e: IOException) {
            emptyList()
        }
    }

    /** UTC 显示格式（摘要日志用）。 */
    fun format(at: Instant): String = DATETIME.format(at)
}
