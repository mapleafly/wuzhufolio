package com.wuzhufolio.data.accounts

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.randomDbKey
import com.wuzhufolio.data.security.FileRememberMeStore
import com.wuzhufolio.data.security.RememberMeStore
import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.accounts.ChangePwdReq
import com.wuzhufolio.domain.accounts.CreateAccountReq
import com.wuzhufolio.domain.accounts.InvalidCredentialsException
import com.wuzhufolio.domain.accounts.LoginReq
import com.wuzhufolio.domain.accounts.OldPasswordMismatchException
import com.wuzhufolio.domain.accounts.PasswordMismatchException
import com.wuzhufolio.domain.accounts.SwitchReq
import com.wuzhufolio.domain.accounts.WeakPasswordException
import com.wuzhufolio.domain.security.CryptoService
import com.wuzhufolio.domain.security.KdfParams
import com.wuzhufolio.domain.accounts.UsernameTakenException
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AuthService 全链路（T2.1–T2.4 验收核心）：创建→登录（A1 不暴露存在性）→记住我→恢复→登出清除→
 * 切换（严格模式密码，A 错误文案）→改密（旧令牌失效/旧密码拒/新密码可登）。
 * KDF 用 OWASP 下限参数保证测试时长（生产默认 64MiB/t3/p1，DefaultAccountService 构造可注入）。
 */
class DefaultAccountServiceTest {

    private val dir: Path = Files.createTempDirectory("wuzhufolio-auth")
    private val db: WzDatabase = WzDatabase(dir.resolve("auth.db"), randomDbKey())
    private val gate = DbGate(db)
    private val repo = AccountRepository(gate)
    private val rememberStore: RememberMeStore = FileRememberMeStore(dir.resolve("remember.dat"))
    private val sessions = ActiveSessionStore()
    private val crypto = CryptoService(KdfParams.OWASP_MINIMUM)
    private val service = DefaultAccountService(repo, rememberStore, sessions, crypto)

    init {
        db.migrateToLatest()
    }

    @AfterTest
    fun tearDown() {
        rememberStore.clear()
        sessions.clear()
        db.close()
    }

    private fun pw(value: String) = value.toCharArray()

    private fun create(
        username: String = "alex",
        password: String = "password1A",
        remember: Boolean = false,
    ): AccountSummary =
        runBlocking {
            service.createAccount(CreateAccountReq(username, pw(password), remember)).account
        }

    @Test
    fun `create then login with correct password yields session`() = runBlocking {
        create("alex", "password1A")
        val session = service.login(LoginReq("alex", pw("password1A"), rememberMe = false))
        assertEquals("alex", session.account.username)
        assertTrue(!service.listAccounts().isEmpty())
        assertEquals(AccountSummary(session.account.id, "alex"), session.account)
    }

    @Test
    fun `login failure is unified and does not leak existence`() = runBlocking {
        create("alice", "password1A")
        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginReq("alice", pw("wrong-pass-1A"), rememberMe = false))
        }
        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginReq("nobody", pw("password1A"), rememberMe = false))
        }
    }

    @Test
    fun `duplicate username rejected and weak password rejected at service layer`() = runBlocking {
        create("bob", "password1A")
        assertFailsWith<UsernameTakenException> {
            service.createAccount(CreateAccountReq("bob", pw("password1A"), false))
        }
        assertFailsWith<WeakPasswordException> {
            service.createAccount(CreateAccountReq("bob2", pw("weakpw"), false))
        }
    }

    @Test
    fun `remember me roundtrip saves restores and logout clears`() = runBlocking {
        create("carol", "password1A", remember = true)
        assertTrue(service.hasRememberMe())
        // 模拟重启：清内存会话后 restore 免密进入
        sessions.clear()
        val restored = service.restoreSession()
        assertNotNull(restored)
        assertEquals("carol", restored.account.username)
        service.logout()
        assertNull(service.restoreSession())
        assertTrue(!service.hasRememberMe())
    }

    @Test
    fun `login without remember clears stale entry`() = runBlocking {
        create("dave", "password1A", remember = true)
        service.logout()
        service.login(LoginReq("dave", pw("password1A"), rememberMe = false))
        assertTrue(!service.hasRememberMe(), "未勾选记住我的登录必须清掉旧令牌（每次启动需重新输入密码）")
    }

    @Test
    fun `restore with invalid token clears entry and returns null`() = runBlocking {
        create("erin", "password1A", remember = true)
        // 伪造损坏条目：直接覆盖存储
        rememberStore.save(
            com.wuzhufolio.data.security.RememberMeEntry(
                accountId = sessions.get()!!.account.id,
                token = ByteArray(32),
                wrappedDekSession = "v1AAAAAAAA",
            ),
        )
        sessions.clear()
        assertNull(service.restoreSession())
        assertTrue(!service.hasRememberMe())
    }

    @Test
    fun `switch requires target password and wipes old session remember entry`() = runBlocking {
        create("alpha", "password1A", remember = true)
        create("beta", "password1A")
        val betaId = service.listAccounts().first { it.username == "beta" }.id
        // 错误密码 → PasswordMismatch
        assertFailsWith<PasswordMismatchException> {
            service.switchAccount(SwitchReq(betaId, pw("wrong-pass-1A")))
        }
        val switched = service.switchAccount(SwitchReq(betaId, pw("password1A")))
        assertEquals("beta", switched.account.username)
        assertTrue(!service.hasRememberMe(), "切换账户即作废原会话令牌（ADR-002 §3）")
        assertNull(service.restoreSession())
    }

    @Test
    fun `change password invalidates token and old password and rewraps same dek`() = runBlocking {
        create("frank", "password1A", remember = true)
        val sessionBefore = sessions.get()!!
        val oldWrapped = repo.findById(sessionBefore.account.id)!!.wrappedDek

        assertFailsWith<OldPasswordMismatchException> {
            service.changePassword(ChangePwdReq(pw("wrong-old-1A"), pw("newPassword2B")))
        }
        // 改密成功后：令牌作废 + 会话签出 + 旧密码拒绝 + 新密码可登
        service.changePassword(ChangePwdReq(pw("password1A"), pw("newPassword2B")))
        assertNull(sessions.get())
        assertTrue(!service.hasRememberMe())
        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginReq("frank", pw("password1A"), rememberMe = false))
        }
        val relogin = service.login(LoginReq("frank", pw("newPassword2B"), rememberMe = false))
        assertEquals("frank", relogin.account.username)
        // DEK 不变：仅重包（新旧 wrapped 不同但可解出同一 DEK——以改密后可正常解锁为准）
        val newWrapped = repo.findById(relogin.account.id)!!.wrappedDek
        assertTrue(newWrapped != oldWrapped)
    }
}
