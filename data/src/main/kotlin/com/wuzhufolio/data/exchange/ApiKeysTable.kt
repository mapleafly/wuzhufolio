package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.exchange.DuplicateApiKeyNameException
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/**
 * api_keys 表映射（DDL = M007；data-model §2.2 / PRD §10-2）。
 *
 * 加密边界（ADR-002 §2）：api_key/secret_key/passphrase/extra 四列按**账户 DEK** 字段级加密
 * （FieldCipher v1，AAD = account_id|api_key_id|column，见 domain/security/FieldCipher 注），
 * 库内只存密文；last_sync_time/status 为同步编排状态。去重键（备份/唯一）= (account_id, exchange_name, name)。
 */
object ApiKeysTable : Table("api_keys") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id")
    val name = varchar("name", 64)
    val exchangeName = varchar("exchange_name", 24)
    val apiKey = text("api_key")
    val secretKey = text("secret_key")
    val passphrase = text("passphrase").nullable()
    val extra = text("extra").nullable()
    val lastSyncTime = varchar("last_sync_time", 40).nullable()
    val status = varchar("status", 16)

    override val primaryKey = PrimaryKey(id)
}

/** api_keys 行（密文形态；明文只在调用方解密瞬时存在）。 */
data class ApiKeyRecord(
    val id: Int,
    val accountId: Int,
    val name: String,
    val exchangeName: String,
    val apiKeyCipher: String,
    val secretKeyCipher: String,
    val passphraseCipher: String?,
    val extraCipher: String?,
    val lastSyncTime: String?,
    val status: String,
) {
    fun toInfo(configured: Boolean = true): ApiKeyInfo = ApiKeyInfo(
        id = id.toLong(),
        accountId = accountId.toLong(),
        name = name,
        exchangeName = exchangeName,
        lastSyncTime = lastSyncTime?.let(Instant::parse),
        status = status,
        configured = configured,
    )
}

/**
 * API 密钥持久化（T6.1 · 经 DbGate 单写队列；M9 备份合并复用本表去重键）。
 * 本仓库不接触任何密钥材料：凭证列只存密文，加密在服务层（CryptoService.encryptField，
 * AAD 依赖行 id——入库走「先插后包」回调，同 AccountRepository.createWrapped 模式）。
 */
class ApiKeyRepository(private val gate: DbGate) {

    /** 是否存在至少一个账户（首启路由；与 accounts 同口径）。 */
    fun list(accountId: Int): List<ApiKeyRecord> = gate.readBlocking {
        ApiKeysTable.selectAll()
            .where { ApiKeysTable.accountId eq accountId }
            .orderBy(ApiKeysTable.id to org.jetbrains.exposed.v1.core.SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun findById(accountId: Int, id: Int): ApiKeyRecord? = gate.readBlocking {
        ApiKeysTable.selectAll()
            .where { (ApiKeysTable.accountId eq accountId) and (ApiKeysTable.id eq id) }
            .singleOrNull()
            ?.toRecord()
    }

    /**
     * 新增 API 密钥（先插后包：单写事务内 插入空列 → 用真实行 id 计算四列密文 → 回写）。
     * @param cipherFields 接收真实行 id，返回列密文（在事务线程内执行，可做 AES-GCM 计算）。
     */
    fun create(
        accountId: Int,
        name: String,
        exchangeName: String,
        cipherFields: (rowId: Int) -> ApiKeyCiphers,
    ): ApiKeyRecord = gate.writeBlocking {
        try {
            val id = ApiKeysTable.insert {
                it[ApiKeysTable.accountId] = accountId
                it[ApiKeysTable.name] = name
                it[ApiKeysTable.exchangeName] = exchangeName
                it[apiKey] = ""
                it[secretKey] = ""
                it[passphrase] = null
                it[extra] = null
                it[lastSyncTime] = null
                it[status] = "OK"
            } get ApiKeysTable.id
            val cipher = cipherFields(id)
            ApiKeysTable.update(where = { ApiKeysTable.id eq id }) {
                it[apiKey] = cipher.apiKey
                it[secretKey] = cipher.secretKey
                it[passphrase] = cipher.passphrase
                it[extra] = cipher.extra
            }
            ApiKeysTable.selectAll().where { ApiKeysTable.id eq id }.single().toRecord()
        } catch (e: Exception) {
            var cursor: Throwable? = e
            while (cursor != null) {
                val msg = cursor.message.orEmpty()
                if (isUniqueConstraint(msg)) {
                    throw DuplicateApiKeyNameException(name)
                }
                cursor = cursor.cause
            }
            throw e
        }
    }

    /** 覆盖密钥列密文（别名不变，仅换凭证）。 */
    fun updateCredentials(accountId: Int, id: Int, cipher: ApiKeyCiphers, name: String) {
        gate.writeBlocking {
            ApiKeysTable.update(where = { (ApiKeysTable.accountId eq accountId) and (ApiKeysTable.id eq id) }) {
                it[apiKey] = cipher.apiKey
                it[secretKey] = cipher.secretKey
                it[passphrase] = cipher.passphrase
                it[extra] = cipher.extra
                it[ApiKeysTable.name] = name
                it[status] = "OK"
                it[lastSyncTime] = null
            }
        }
    }

    /** 同步编排更新：最后成功时刻 + 状态。 */
    fun updateSyncState(accountId: Int, id: Int, lastSyncTime: Instant, status: String) {
        gate.writeBlocking {
            ApiKeysTable.update(where = { (ApiKeysTable.accountId eq accountId) and (ApiKeysTable.id eq id) }) {
                it[ApiKeysTable.lastSyncTime] = lastSyncTime.toString()
                it[ApiKeysTable.status] = status
            }
        }
    }

    /** 校验失败仅标记 FAILED（保留密文，供用户编辑换 Key）。 */
    fun markFailed(accountId: Int, id: Int) {
        gate.writeBlocking {
            ApiKeysTable.update(where = { (ApiKeysTable.accountId eq accountId) and (ApiKeysTable.id eq id) }) {
                it[status] = "FAILED"
            }
        }
    }

    /** 移除密钥（同步范围随之收缩）。 */
    fun remove(accountId: Int, id: Int) {
        gate.writeBlocking {
            ApiKeysTable.deleteWhere { (ApiKeysTable.accountId eq accountId) and (ApiKeysTable.id eq id) }
        }
    }

    private fun isUniqueConstraint(message: String): Boolean =
        message.contains("UNIQUE constraint failed: api_keys.account_id, api_keys.exchange_name, api_keys.name")

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRecord(): ApiKeyRecord = ApiKeyRecord(
        id = this[ApiKeysTable.id],
        accountId = this[ApiKeysTable.accountId],
        name = this[ApiKeysTable.name],
        exchangeName = this[ApiKeysTable.exchangeName],
        apiKeyCipher = this[ApiKeysTable.apiKey],
        secretKeyCipher = this[ApiKeysTable.secretKey],
        passphraseCipher = this[ApiKeysTable.passphrase],
        extraCipher = this[ApiKeysTable.extra],
        lastSyncTime = this[ApiKeysTable.lastSyncTime],
        status = this[ApiKeysTable.status],
    )
}

/** 凭证四列密文（FieldCipher 产物；passphrase/extra MVP 为空串 → 存 null）。 */
data class ApiKeyCiphers(
    val apiKey: String,
    val secretKey: String,
    val passphrase: String? = null,
    val extra: String? = null,
)
