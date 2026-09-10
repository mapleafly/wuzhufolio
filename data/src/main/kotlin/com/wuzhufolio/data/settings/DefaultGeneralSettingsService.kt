package com.wuzhufolio.data.settings

import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.BASE_FIAT_OPTIONS
import com.wuzhufolio.domain.settings.CASH_COIN_DEFAULTS
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.GeneralSettingsView
import com.wuzhufolio.domain.settings.PrecisionPreset
import java.math.BigDecimal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** settings 键（通用设置；全局行 account_id NULL——口径见 domain/settings/GeneralSettings.kt 头注）。 */
object GeneralSettingsKeys {
    const val BASE_FIAT = "fiat" // 与 M7/M8 折算链既有键同源（DefaultTransactionLedgerService.SETTING_FIAT）
    const val PRECISION = "display.precision"
    const val USERNAME_ENUM = "login.username.enum" // M2 起登录页读取
    const val CASH_COINS = "cash.coins"
    const val SMALL_THRESHOLD = "small.threshold"
    const val PROXY_ENABLED = "network.proxy.enabled"
    // M12 T12.4：界面语言——复用 M002 迁移已种下的既有键 locale（原值 zh-CN），不再另立新键
    val LANGUAGE = AppLanguage.SETTINGS_KEY
}

/**
 * 通用设置用例实现（M10 · T10.1）：settings 全局行读写 + 白名单 JSON 编解码（损坏自愈——按空扩展处理）。
 * 现金类币种白名单运行期读取器 [cashCoinIds] 供引擎/资金总览注入（默认 ∪ 扩展）。
 */
@Suppress("TooManyFunctions") // 每个设置项一个读/写入口（含 M12 T12.4 语言档），用例面固有
class DefaultGeneralSettingsService(
    private val settings: SettingsRepository,
) : GeneralSettingsService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun view(): GeneralSettingsView = GeneralSettingsView(
        baseFiat = settings.getGlobal(GeneralSettingsKeys.BASE_FIAT)
            ?.takeIf { it.isNotBlank() } ?: "USD",
        precision = PrecisionPreset.fromStorage(settings.getGlobal(GeneralSettingsKeys.PRECISION)),
        usernameEnumOn = settings.getGlobal(GeneralSettingsKeys.USERNAME_ENUM)?.let { it != "off" } ?: true,
        cashCoinExtra = readCashCoinExtra(),
        smallThreshold = settings.getGlobal(GeneralSettingsKeys.SMALL_THRESHOLD)
            ?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        proxyEnabled = settings.getGlobal(GeneralSettingsKeys.PROXY_ENABLED)?.let { it != "off" } ?: true,
        language = AppLanguage.fromStorage(settings.getGlobal(GeneralSettingsKeys.LANGUAGE)),
    )

    override suspend fun setBaseFiat(code: String) {
        val normalized = code.trim().uppercase()
        require(normalized in BASE_FIAT_OPTIONS) { "不支持的基础法币：" + code }
        settings.putGlobal(GeneralSettingsKeys.BASE_FIAT, normalized)
    }

    override suspend fun setPrecision(preset: PrecisionPreset) {
        settings.putGlobal(GeneralSettingsKeys.PRECISION, preset.storageValue)
    }

    override suspend fun setUsernameEnum(on: Boolean) {
        settings.putGlobal(GeneralSettingsKeys.USERNAME_ENUM, if (on) "on" else "off")
    }

    override suspend fun addCashCoin(cgId: String) {
        val id = cgId.trim().lowercase()
        require(id.isNotEmpty()) { "请输入币种标识（如 usd-coin）" }
        if (id in CASH_COIN_DEFAULTS) return // 默认项幂等
        val extra = readCashCoinExtra().toMutableSet()
        extra += id
        settings.putGlobal(GeneralSettingsKeys.CASH_COINS, json.encodeToString(extra.toList().sorted()))
    }

    override suspend fun removeCashCoin(cgId: String) {
        val id = cgId.trim().lowercase()
        require(id !in CASH_COIN_DEFAULTS) { "默认白名单项不可移除" }
        val extra = readCashCoinExtra().toMutableSet()
        extra -= id
        settings.putGlobal(GeneralSettingsKeys.CASH_COINS, json.encodeToString(extra.toList().sorted()))
    }

    override suspend fun setSmallAmountThreshold(threshold: BigDecimal) {
        require(threshold.signum() >= 0) { "小额阈值不能为负（0 = 不启用）" }
        settings.putGlobal(GeneralSettingsKeys.SMALL_THRESHOLD, threshold.stripTrailingZeros().toPlainString())
    }

    override suspend fun setProxyEnabled(on: Boolean) {
        settings.putGlobal(GeneralSettingsKeys.PROXY_ENABLED, if (on) "on" else "off")
    }

    override suspend fun setLanguage(language: AppLanguage) {
        settings.putGlobal(GeneralSettingsKeys.LANGUAGE, language.storageValue)
    }

    /** 现金类币种全量集合（默认 ∪ 扩展；引擎/总览运行期读取入口）。 */
    fun cashCoinIds(): Set<String> = CASH_COIN_DEFAULTS + readCashCoinExtra()

    @Suppress("SwallowedException") // 扩展载荷损坏按空扩展处理（自愈式重建，先例 SettingsQuotaLedger）
    private fun readCashCoinExtra(): List<String> {
        val raw = settings.getGlobal(GeneralSettingsKeys.CASH_COINS) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && it !in CASH_COIN_DEFAULTS }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
