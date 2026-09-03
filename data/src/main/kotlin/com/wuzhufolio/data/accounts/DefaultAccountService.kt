package com.wuzhufolio.data.accounts

import com.wuzhufolio.data.security.KeychainUnavailableException
import com.wuzhufolio.data.security.RememberMeEntry
import com.wuzhufolio.data.security.RememberMeStore
import com.wuzhufolio.domain.accounts.AccountPolicy
import com.wuzhufolio.domain.accounts.AccountService
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.accounts.ChangePwdReq
import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.InvalidCredentialsException
import com.wuzhufolio.domain.accounts.LoginReq
import com.wuzhufolio.domain.accounts.OldPasswordMismatchException
import com.wuzhufolio.domain.accounts.PasswordMismatchException
import com.wuzhufolio.domain.accounts.Session
import com.wuzhufolio.domain.accounts.SwitchReq
import com.wuzhufolio.domain.accounts.WeakPasswordException
import com.wuzhufolio.domain.security.AuthenticationFailedException
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.security.KdfParams
import com.wuzhufolio.domain.security.Zeroization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 账户会话用例实现（M2 T2.1–T2.4；契约 api-contracts §3 AccountService）。
 *
 * 语义（ADR-002 §3）：
 * - 创建：随机 salt+DEK → Argon2id 派生 KEK → wrapped_dek（AAD=账户id+"wrapped_dek"，单写事务内先插后包）；
 *   password_hash = SHA-256(KEK ‖ "verify")（快速判错，不落第二趟 KDF）。
 * - 登录：单次 KDF → password_hash 快速判错 → GCM 解包（认证即密码校验）→ DEK 驻 ActiveSessionStore。
 * - 记住我：随机 256-bit 令牌 + wrapped_dek_session（AAD=账户id+"session"）入钥匙串；登出/改密/切换删除条目。
 * - kdf_salt 存储形态：32 位小写 hex（16 字节盐）。
 */
@Suppress("TooManyFunctions", "SwallowedException") // 契约 8 动作 + 内部帮助函数；异常统一映射为面向用户的固定文案，原始异常不对外
class DefaultAccountService(
    private val repository: AccountRepository,
    private val rememberStore: RememberMeStore,
    private val sessions: ActiveSessionStore,
    private val crypto: CryptoService = CryptoService(),
    private val logger: Logger = LoggerFactory.getLogger(DefaultAccountService::class.java),
) : AccountService {

    override suspend fun createAccount(req: CreateAccountReq): Session = withContext(Dispatchers.Default) {
        val username = req.username.trim()
        try {
            require(AccountPolicy.isValidUsername(username)) { "invalid username" }
            if (!AccountPolicy.meetsMinimum(String(req.password))) throw WeakPasswordException()

            val salt = crypto.newSalt()
            val dek = crypto.newDek()
            val kek = crypto.deriveKek(req.password, salt)
            val record = try {
                repository.createWrapped(
                    username = username,
                    passwordHash = crypto.kekVerifyHash(kek),
                    kdfSalt = saltHex(salt),
                    kdfParams = crypto.defaultParams.toStorageString(),
                ) { accountId -> crypto.wrapDek(dek, kek, accountId.toString()) }
            } finally {
                Zeroization.wipe(kek)
            }
            val session = ActiveSession(AccountSummary(record.id, record.username), dek)
            sessions.set(session)
            if (req.rememberMe) persistRemember(record.id, record.username, dek)
            Session(session.account)
        } finally {
            Zeroization.wipe(req.password)
        }
    }

    override suspend fun login(req: LoginReq): Session = withContext(Dispatchers.Default) {
        try {
            val username = req.username.trim()
            val record = repository.findByUsername(username)
                ?: throw InvalidCredentialsException()
            val dek = unlockWithPassword(record, req.password)
            val session = ActiveSession(AccountSummary(record.id, record.username), dek)
            sessions.set(session)
            if (req.rememberMe) {
                persistRemember(record.id, record.username, dek)
            } else {
                clearRememberBestEffort("login without remember-me")
            }
            Session(session.account)
        } finally {
            Zeroization.wipe(req.password)
        }
    }

    override suspend fun restoreSession(): Session? = withContext(Dispatchers.Default) {
        val entry = try {
            rememberStore.load()
        } catch (e: KeychainUnavailableException) {
            logger.warn("remember-me store unavailable during restore: {}", e.message)
            null
        } ?: return@withContext null
        try {
            val record = repository.findById(entry.accountId)
            if (record == null) {
                clearRememberBestEffort("restore: account vanished")
                return@withContext null
            }
            val dek = try {
                crypto.unwrapDekForSession(entry.wrappedDekSession, entry.token, entry.accountId.toString())
            } catch (e: AuthenticationFailedException) {
                clearRememberBestEffort("restore: token invalid")
                return@withContext null
            } catch (e: IllegalArgumentException) {
                // 结构损坏（版本/载荷长度）：与令牌失效同语义——清除条目并走登录
                clearRememberBestEffort("restore: entry corrupt")
                return@withContext null
            }
            val session = ActiveSession(AccountSummary(record.id, record.username), dek)
            sessions.set(session)
            Session(session.account)
        } finally {
            entry.wipe()
        }
    }

    override suspend fun logout() {
        sessions.clear()
        clearRememberBestEffort("logout")
    }

    override suspend fun switchAccount(req: SwitchReq): Session = withContext(Dispatchers.Default) {
        try {
            val record = repository.findById(req.accountId)
                ?: throw InvalidCredentialsException()
            val dek = try {
                unlockWithPassword(record, req.password)
            } catch (e: InvalidCredentialsException) {
                throw PasswordMismatchException()
            }
            // ADR-002 §3：切换账户即删除钥匙串条目并作废令牌
            clearRememberBestEffort("switch account")
            val session = ActiveSession(AccountSummary(record.id, record.username), dek)
            sessions.set(session)
            Session(session.account)
        } finally {
            Zeroization.wipe(req.password)
        }
    }

    override suspend fun changePassword(req: ChangePwdReq) = withContext(Dispatchers.Default) {
        try {
            val current = sessions.get() ?: error("no active session")
            val record = repository.findById(current.account.id) ?: error("account vanished")
            val params = KdfParams.fromStorageString(record.kdfParams)
            val oldSalt = saltFromHex(record.kdfSalt)
            val oldKek = crypto.deriveKek(req.currentPassword, oldSalt, params)
            val oldDek = try {
                if (crypto.kekVerifyHash(oldKek) != record.passwordHash) throw OldPasswordMismatchException()
                try {
                    crypto.unwrapDek(record.wrappedDek, oldKek, record.id.toString())
                } catch (e: AuthenticationFailedException) {
                    throw OldPasswordMismatchException()
                }
            } finally {
                Zeroization.wipe(oldKek)
            }
            // 重包 DEK（数据不变）：新 salt + 新 KEK + 同 DEK
            val newSalt = crypto.newSalt()
            val newKek = crypto.deriveKek(req.newPassword, newSalt)
            try {
                val newHash = crypto.kekVerifyHash(newKek)
                val newWrapped = crypto.wrapDek(oldDek, newKek, record.id.toString())
                repository.updateCredentials(record.id, newHash, saltHex(newSalt), record.kdfParams, newWrapped)
            } finally {
                Zeroization.wipe(newKek)
            }
            // ADR-002 §3：改密即作废该账户「记住我」令牌；本次会话签出（UI 回登录页重新登录）
            clearRememberBestEffort("change password")
            sessions.clear()
            Zeroization.wipe(oldDek)
        } finally {
            Zeroization.wipe(req.currentPassword)
            Zeroization.wipe(req.newPassword)
        }
    }

    override suspend fun listAccounts(): List<AccountSummary> = withContext(Dispatchers.Default) {
        repository.list().map { AccountSummary(it.id, it.username) }
    }

    override fun hasRememberMe(): Boolean {
        val entry = try {
            rememberStore.load()
        } catch (e: KeychainUnavailableException) {
            logger.warn("remember-me store unavailable: {}", e.message)
            null
        } ?: return false
        entry.wipe()
        return true
    }

    // ---- internals ----

    /** 单次 KDF + 快速判错 + GCM 解包认证；失败统一 InvalidCredentials（不暴露存在性）。 */
    @Suppress("ThrowsCount") // 判错路径逐点收敛为同一 A1 语义异常（解析/校验/认证三步）
    private fun unlockWithPassword(record: AccountRecord, password: CharArray): ByteArray {
        val params = try {
            KdfParams.fromStorageString(record.kdfParams)
        } catch (e: IllegalArgumentException) {
            throw InvalidCredentialsException("corrupt kdf params")
        }
        val salt = try {
            saltFromHex(record.kdfSalt)
        } catch (e: IllegalArgumentException) {
            throw InvalidCredentialsException("corrupt kdf salt")
        }
        val kek = crypto.deriveKek(password, salt, params)
        try {
            if (!crypto.kekVerify(kek, record.passwordHash)) throw InvalidCredentialsException()
            return try {
                crypto.unwrapDek(record.wrappedDek, kek, record.id.toString())
            } catch (e: AuthenticationFailedException) {
                throw InvalidCredentialsException()
            }
        } finally {
            Zeroization.wipe(kek)
        }
    }

    private fun persistRemember(accountId: Int, username: String, dek: ByteArray) {
        val token = crypto.newSessionToken()
        try {
            val wrapped = crypto.wrapDekForSession(dek, token, accountId.toString())
            rememberStore.save(RememberMeEntry(accountId, token, wrapped))
            logger.debug("remember-me saved for account {}", username)
        } finally {
            Zeroization.wipe(token)
        }
    }

    private fun clearRememberBestEffort(reason: String) {
        try {
            rememberStore.clear()
        } catch (e: KeychainUnavailableException) {
            // 钥匙串不可用时条目可能残留：记录限制（威胁模型见 ADR-002 备注），登出语义仍以内存会话清除为主
            logger.warn("remember-me entry not cleared ({}): {}", reason, e.message)
        }
    }

    companion object {
        private const val HEX = "0123456789abcdef"

        private fun saltHex(salt: ByteArray): String = buildString(salt.size * 2) {
            for (b in salt) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }

        private fun saltFromHex(text: String): ByteArray {
            require(text.length == 32 && text.all { it in HEX }) { "kdf_salt must be 32 hex chars" }
            return ByteArray(16) { i ->
                ((Character.digit(text[i * 2], 16) shl 4) or Character.digit(text[i * 2 + 1], 16)).toByte()
            }
        }
    }
}
