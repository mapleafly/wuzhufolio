package com.wuzhufolio.app.integration

import com.wuzhufolio.domain.accounts.CreateAccountReq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** 基座自检：真实组合根可启动、库可迁移、账户可创建（其余用例见 CoreJourneyIntegrationTest）。 */
internal class IntegrationHarnessTest {

    @Test
    fun `harness boots real composition root and creates account`() {
        IntegrationHarness.boot().use { h ->
            val session = runBlocking {
                h.runtime.session.authService.createAccount(
                    CreateAccountReq("harness", "Passw0rd!".toCharArray(), rememberMe = false),
                )
            }
            assertEquals("harness", session.account.username)
            assertEquals("BTC", h.coin("BTC").symbol)
        }
    }
}
