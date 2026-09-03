package com.wuzhufolio.data.accounts

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.accounts.UsernameTakenException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder
import java.time.Instant

/** accounts 行（data-model §2.1；密码哈希/KDF 参数随行，无明文密码）。 */
data class AccountRecord(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val kdfSalt: String,
    val kdfParams: String,
    val wrappedDek: String,
    val createdAt: String,
)

/**
 * 账户持久化（M2 T2.1；经 DbGate 单写队列）。本仓库不接触任何密钥材料——
 * 加密字段（password_hash/wrapped_dek/kdf_*）由 AuthService 用 CryptoService 生成后落库。
 */
class AccountRepository(private val gate: DbGate) {

    /** 是否存在至少一个账户（决定首启走「创建」还是「登录」）。 */
    fun existsAny(): Boolean = gate.readBlocking {
        AccountsTable.selectAll().limit(1).any()
    }

    fun findByUsername(username: String): AccountRecord? = gate.readBlocking {
        AccountsTable.selectAll()
            .where { AccountsTable.username eq username }
            .singleOrNull()
            ?.toRecord()
    }

    fun findById(id: Int): AccountRecord? = gate.readBlocking {
        AccountsTable.selectAll()
            .where { AccountsTable.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    fun list(): List<AccountRecord> = gate.readBlocking {
        AccountsTable.selectAll()
            .orderBy(AccountsTable.id to SortOrder.ASC)
            .map { it.toRecord() }
    }

    /**
     * 原子创建账户：单写事务内 插入 → 用真实账户 id 计算 wrapped_dek（AAD=account_id+"wrapped_dek"，
     * ADR-002 §2）→ 回写 → 回读。用户名冲突抛 [UsernameTakenException]。
     * @param wrapWrappedDek 接收真实 id、返回 wrapped_dek 存储串（在事务线程内执行，可做 KDF/GCM 计算）。
     */
    fun createWrapped(
        username: String,
        passwordHash: String,
        kdfSalt: String,
        kdfParams: String,
        wrapWrappedDek: (accountId: Int) -> String,
    ): AccountRecord {
        return gate.writeBlocking {
            try {
                val id = AccountsTable.insert {
                    it[AccountsTable.username] = username
                    it[AccountsTable.passwordHash] = passwordHash
                    it[AccountsTable.kdfSalt] = kdfSalt
                    it[AccountsTable.kdfParams] = kdfParams
                    it[AccountsTable.wrappedDek] = ""
                    it[AccountsTable.createdAt] = Instant.now().toString()
                } get AccountsTable.id
                val wrapped = wrapWrappedDek(id)
                AccountsTable.update(where = { AccountsTable.id eq id }) {
                    it[AccountsTable.wrappedDek] = wrapped
                }
                AccountsTable.selectAll().where { AccountsTable.id eq id }.single().toRecord()
            } catch (e: Exception) {
                var cursor: Throwable? = e
                while (cursor != null) {
                    if (cursor.message.orEmpty().contains("UNIQUE constraint failed: accounts.username")) {
                        throw UsernameTakenException(username)
                    }
                    cursor = cursor.cause
                }
                throw e
            }
        }
    }

    /** 改密落库：新 KEK 对应字段重写（DEK 不变，wrapped_dek 重包）。 */
    fun updateCredentials(
        id: Int,
        passwordHash: String,
        kdfSalt: String,
        kdfParams: String,
        wrappedDek: String,
    ) {
        gate.writeBlocking {
            AccountsTable.update(where = { AccountsTable.id eq id }) {
                it[AccountsTable.passwordHash] = passwordHash
                it[AccountsTable.kdfSalt] = kdfSalt
                it[AccountsTable.kdfParams] = kdfParams
                it[AccountsTable.wrappedDek] = wrappedDek
            }
        }
    }

    private fun ResultRow.toRecord(): AccountRecord =
        AccountRecord(
            id = this[AccountsTable.id],
            username = this[AccountsTable.username],
            passwordHash = this[AccountsTable.passwordHash],
            kdfSalt = this[AccountsTable.kdfSalt],
            kdfParams = this[AccountsTable.kdfParams],
            wrappedDek = this[AccountsTable.wrappedDek],
            createdAt = this[AccountsTable.createdAt],
        )
}
