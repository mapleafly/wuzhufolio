package com.wuzhufolio.data.accounts

import org.jetbrains.exposed.v1.core.Table

/** accounts 表映射（DDL = M003；data-model §2.1）。 */
object AccountsTable : Table("accounts") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 64)
    val passwordHash = text("password_hash")
    val kdfSalt = text("kdf_salt")
    val kdfParams = text("kdf_params")
    val wrappedDek = text("wrapped_dek")
    val createdAt = varchar("created_at", 40)

    override val primaryKey = PrimaryKey(id)
}
