package com.wuzhufolio.data.logging

import com.wuzhufolio.domain.redaction.LogRedactor
import com.wuzhufolio.domain.security.FilePermissions
import com.wuzhufolio.domain.settings.LogAccess
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * 本地日志文件访问实现（M10 · T10.2）：活动日志 `wuzhufolio.log` 的尾部读取/导出。
 * 查看/导出均逐行经 LogRedactor 兜底（写入侧已脱敏——双保险，PRD §6「导出日志含脱敏」验收口径）。
 */
class FileLogAccess(
    private val logDir: Path,
    private val fileName: String = "wuzhufolio.log",
) : LogAccess {

    private val file: Path get() = logDir.resolve(fileName)

    override fun path(): String? = if (Files.isRegularFile(file)) file.toString() else null

    override fun tailLines(max: Int): List<String> =
        LogRotator.tailLines(file, max).map { LogRedactor.redact(it) }

    override fun exportTo(targetPath: String): Int {
        val lines = tailLines(Int.MAX_VALUE)
        if (lines.isEmpty() && !Files.isRegularFile(file)) throw IOException("日志文件不存在")
        val target = Path.of(targetPath)
        target.parent?.let { Files.createDirectories(it) }
        val tmp = Files.createTempFile(target.toAbsolutePath().parent, target.fileName.toString(), ".tmp")
        Files.write(tmp, lines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        // M13 T13.1 加固：日志导出物 0600（临时文件本身已受限，此处对目标路径显式兜底）
        FilePermissions.restrictFile(target)
        return lines.size
    }
}
