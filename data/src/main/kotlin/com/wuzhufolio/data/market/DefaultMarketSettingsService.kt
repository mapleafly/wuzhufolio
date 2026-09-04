package com.wuzhufolio.data.market

import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.RefreshFrequency

/**
 * 行情设置实现（T5.5 · ADR-002 §2.1 方案甲：设备密钥加密存 settings 全局行、不进 .cpro 备份；
 * 保存即生效——编排层每次刷新现读密文，无需重启）。
 */
class DefaultMarketSettingsService(
    private val store: DeviceSecretStore,
    private val settings: SettingsRepository,
) : MarketSettingsService {

    override suspend fun keyStatus(): MarketKeyStatus = MarketKeyStatus(
        cgConfigured = store.configured(MarketConfig.KEY_CG),
        cmcConfigured = store.configured(MarketConfig.KEY_CMC),
    )

    override suspend fun saveCgKey(key: String): MarketKeyStatus {
        val trimmed = key.trim()
        if (trimmed.isNotEmpty()) {
            store.put(MarketConfig.KEY_CG, MarketConfig.PURPOSE_CG, trimmed)
        }
        return keyStatus()
    }

    override suspend fun removeCgKey(): MarketKeyStatus {
        store.remove(MarketConfig.KEY_CG)
        return keyStatus()
    }

    override suspend fun saveCmcKey(key: String): MarketKeyStatus {
        val trimmed = key.trim()
        if (trimmed.isNotEmpty()) {
            store.put(MarketConfig.KEY_CMC, MarketConfig.PURPOSE_CMC, trimmed)
        }
        return keyStatus()
    }

    override suspend fun removeCmcKey(): MarketKeyStatus {
        store.remove(MarketConfig.KEY_CMC)
        return keyStatus()
    }

    override suspend fun refreshFrequencyMinutes(): Int {
        val raw = settings.getGlobal(MarketConfig.KEY_REFRESH_MINUTES) ?: return RefreshFrequency.MIN5.minutes
        return raw.toIntOrNull()?.let { minutes ->
            RefreshFrequency.entries.firstOrNull { it.minutes == minutes }?.minutes
        } ?: RefreshFrequency.MIN5.minutes
    }

    override suspend fun saveRefreshFrequencyMinutes(minutes: Int) {
        require(RefreshFrequency.entries.any { it.minutes == minutes }) {
            "refresh frequency must be one of 5/15/30"
        }
        settings.putGlobal(MarketConfig.KEY_REFRESH_MINUTES, minutes.toString())
    }
}
