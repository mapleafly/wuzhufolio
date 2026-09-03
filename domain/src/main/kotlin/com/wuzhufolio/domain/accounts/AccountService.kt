package com.wuzhufolio.domain.accounts

/**
 * 账户会话用例契约（api-contracts §3 落地；M2 自定请求/响应占位类型，回溯 PRD 9.1/9.2/flow1）。
 * 命名 域.动作；错误 = 类型化异常（UI 映射 api-contracts §4 错误码文案），不暴露细节。
 */
interface AccountService {

    suspend fun createAccount(req: CreateAccountReq): Session

    suspend fun login(req: LoginReq): Session

    suspend fun logout()

    /** 启动自动恢复（记住我令牌解包 DEK，全程不触密码）；无有效条目返回 null。 */
    suspend fun restoreSession(): Session?

    suspend fun switchAccount(req: SwitchReq): Session

    suspend fun changePassword(req: ChangePwdReq)

    suspend fun listAccounts(): List<AccountSummary>

    /** 同步布尔：钥匙串是否存在可恢复会话（UI 启动判定，勿阻塞主线程超时）。 */
    fun hasRememberMe(): Boolean
}

data class CreateAccountReq(val username: String, val password: CharArray, val rememberMe: Boolean)
data class LoginReq(val username: String, val password: CharArray, val rememberMe: Boolean)
data class SwitchReq(val accountId: Int, val password: CharArray)
data class ChangePwdReq(val currentPassword: CharArray, val newPassword: CharArray)

data class AccountSummary(val id: Int, val username: String)

/** 会话摘要（UI 身份用；DEK 由实现层会话持有器管理，不跨层流转）。 */
data class Session(val account: AccountSummary)
