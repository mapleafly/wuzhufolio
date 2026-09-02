package com.wuzhufolio.app.di

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.settings.SettingsRepository
import org.koin.dsl.module

/** DI 图（ADR-001：Koin）。M1 登记基础设施单例（SQLCipher 库 + 单写队列闸门 + 设置仓库）；用例/服务随 M2-M10 追加。 */
fun appModule(db: WzDatabase, gate: DbGate, settings: SettingsRepository) = module {
    single { db }
    single { gate }
    single { settings }
}
