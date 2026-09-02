package com.wuzhufolio.data.db

import java.nio.file.Path

/**
 * T1.1：库文件无法用给定主密钥解锁——错钥、旧版明文库或文件头损坏（SQLCipher 报 SQLITE_NOTADB）。
 * 上层按 PRD 统一异常 B1 引导「从 .cpro 恢复」（interaction.md §2.1；文案在 UI 层）。
 */
class DatabaseKeyMismatchException(val dbPath: Path, cause: Throwable) :
    RuntimeException("database cannot be unlocked with the provided master key: " + dbPath, cause)

/** 打开失败（非密钥原因：权限/路径/IO 等）。 */
class DatabaseOpenException(val dbPath: Path, cause: Throwable) :
    RuntimeException("database open failed: " + dbPath, cause)
