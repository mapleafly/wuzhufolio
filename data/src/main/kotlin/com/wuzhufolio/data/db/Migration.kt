package com.wuzhufolio.data.db

import java.sql.Connection

/**
 * 单条 schema 迁移（T0.5，PRD §10 末尾「数据库版本管理」）。
 *
 * 版本号单调递增、只增不改；DDL 用原生 JDBC 执行，保证 M1 切换 SQLCipher 驱动时迁移语义不变（ADR-002）。
 */
interface Migration {
    /** 迁移版本号（从 1 起，严格递增）。 */
    val version: Int

    /** 人类可读描述，写入 schema_version。 */
    val description: String

    /** 在事务内执行迁移内容。 */
    fun migrate(connection: Connection)
}
