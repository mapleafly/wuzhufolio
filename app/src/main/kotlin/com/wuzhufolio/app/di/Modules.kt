package com.wuzhufolio.app.di

import com.wuzhufolio.data.db.WzDatabase
import com.wuzhufolio.data.settings.SettingsRepository
import org.koin.dsl.module

/** DI 图（ADR-001：Koin）。M0 登记基础设施单例；用例/服务随 M2-M10 追加。 */
fun appModule(db: WzDatabase, settings: SettingsRepository) = module {
    single { db }
    single { settings }
}
