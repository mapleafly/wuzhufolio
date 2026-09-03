package com.wuzhufolio.ui.auth

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
import com.wuzhufolio.domain.accounts.UsernameTakenException
import com.wuzhufolio.domain.accounts.WeakPasswordException

/**
 * UI 测试用内存假服务（语义与 DefaultAccountService 对齐：A1 不暴露存在性、创建校验、记住我单槽）。
 * 所有操作即时返回（无真实 KDF），供 Compose 门控流程测试。
 */
class FakeAuthService(initialAccounts: List<Pair<String, String>> = emptyList()) : AccountService {

    private data class User(val id: Int, val username: String, var password: String)

    private val users = mutableListOf<User>()
    private var nextId = 1
    private var active: User? = null
    private var rememberedUserId: Int? = null

    init {
        initialAccounts.forEach { (name, pw) -> users.add(User(nextId++, name, pw)) }
    }

    private fun find(name: String): User? = users.firstOrNull { it.username == name }

    override suspend fun createAccount(req: CreateAccountReq): Session {
        val username = req.username.trim()
        require(AccountPolicy.isValidUsername(username))
        if (!AccountPolicy.meetsMinimum(String(req.password))) throw WeakPasswordException()
        if (find(username) != null) throw UsernameTakenException(username)
        val user = User(nextId++, username, String(req.password))
        users.add(user)
        active = user
        if (req.rememberMe) rememberedUserId = user.id else rememberedUserId = null
        return Session(AccountSummary(user.id, user.username))
    }

    override suspend fun login(req: LoginReq): Session {
        val user = find(req.username.trim()) ?: throw InvalidCredentialsException()
        if (user.password != String(req.password)) throw InvalidCredentialsException()
        active = user
        if (req.rememberMe) rememberedUserId = user.id else rememberedUserId = null
        return Session(AccountSummary(user.id, user.username))
    }

    override suspend fun logout() {
        active = null
        rememberedUserId = null
    }

    override suspend fun restoreSession(): Session? {
        val id = rememberedUserId ?: return null
        return users.firstOrNull { it.id == id }?.let { user ->
            active = user
            Session(AccountSummary(user.id, user.username))
        }
    }

    override suspend fun switchAccount(req: SwitchReq): Session {
        val user = users.firstOrNull { it.id == req.accountId } ?: throw InvalidCredentialsException()
        if (user.password != String(req.password)) throw PasswordMismatchException()
        active = user
        rememberedUserId = null // ADR：切换作废记住我
        return Session(AccountSummary(user.id, user.username))
    }

    override suspend fun changePassword(req: ChangePwdReq) {
        val user = active ?: return
        if (user.password != String(req.currentPassword)) throw OldPasswordMismatchException()
        if (!AccountPolicy.meetsMinimum(String(req.newPassword))) throw WeakPasswordException()
        user.password = String(req.newPassword)
        rememberedUserId = null
        active = null
    }

    override suspend fun listAccounts(): List<AccountSummary> = users.map { AccountSummary(it.id, it.username) }

    override fun hasRememberMe(): Boolean = rememberedUserId != null

    fun activeUsername(): String? = active?.username
}
