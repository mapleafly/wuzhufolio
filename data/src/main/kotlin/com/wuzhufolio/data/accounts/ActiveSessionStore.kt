package com.wuzhufolio.data.accounts

import com.wuzhufolio.domain.accounts.AccountSummary
import com.wuzhufolio.domain.security.Zeroization

/** 活动会话：DEK 驻内存（登录/创建/恢复后置入，登出/切换/改密/退出即擦除）。 */
data class ActiveSession(val account: AccountSummary, internal val dek: ByteArray) {
    fun wipe() {
        Zeroization.wipe(dek)
    }
}

/** 当前会话持有器（单写线程语义：UI 动作串行调用；读可并发）。 */
class ActiveSessionStore {
    @Volatile
    private var current: ActiveSession? = null

    fun get(): ActiveSession? = current

    fun set(session: ActiveSession) {
        current?.wipe()
        current = session
    }

    fun clear() {
        current?.wipe()
        current = null
    }
}
