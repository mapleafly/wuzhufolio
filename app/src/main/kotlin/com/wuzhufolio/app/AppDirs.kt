package com.wuzhufolio.app

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
}
