package com.wuzhufolio.app

import com.wuzhufolio.domain.security.FilePermissions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** 应用目录：数据全部存用户设备本地（PRD §1.1 硬约束 1），可用 -Dwuzhufolio.dataDir 覆盖（开发/测试）。 */
object AppDirs {

    fun dataDir(): Path =
        (System.getenv("WUZHUFOLIO_DATA_DIR") ?: System.getProperty("wuzhufolio.dataDir"))
            ?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".wuzhufolio")

    fun dbPath(): Path = dataDir().resolve("wuzhufolio.db")

    fun logDir(): Path = dataDir().resolve("logs")

    /**
     * 确保数据目录与日志目录存在并收紧为 **0700**（M13 T13.1 安全自查加固）。
     * 业务数据（加密库、日志、备份、降级密钥文件）全部落在本目录下，目录级 0700 即同机其他用户不可遍历；
     * 启动最早期调用（早于日志与数据库初始化），幂等。非 POSIX 平台静默跳过（见 [FilePermissions]）。
     */
    fun ensureDataDirs(): Path {
        val dir = dataDir()
        FilePermissions.ensureOwnerOnlyDirectory(dir)
        FilePermissions.ensureOwnerOnlyDirectory(logDir())
        Files.createDirectories(dir.resolve("backups"))
        FilePermissions.restrictDirectory(dir.resolve("backups"))
        return dir
    }
}
