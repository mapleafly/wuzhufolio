package com.wuzhufolio.domain.settings

import java.math.BigDecimal

/**
 * 通用设置用例契约（M10 · T10.1 · task-breakdown §3 M10；ia.md §2.12「通用」分组；PRD §7.2 模块 6.1）。
 *
 * 存储口径（settings 全局行，key-value；账户级设置行自 M9 起由 BackupSettingsStore 承载
 * backup.last_at/restore.last_at 并随 .cpro 备份范围导出——PRD 7.2-6.1 各项为设备级偏好，存全局行）：
 * - fiat                = 基础法币（ISO 代码，默认 "USD"；与 M7 起交易/资金折算读取的既有键同源）；
 * - display.precision   = 默认精度档（[PrecisionPreset.storageValue]，M12 数字格式化消费）；
 * - login.username.enum = 登录页用户名枚举开关（"on"/"off"，默认 on；M2 起登录页读取）；
 * - cash.coins          = 稳定币白名单用户扩展（JSON 字符串数组，cg_id；默认白名单固定于
 *                         [CASH_COIN_DEFAULTS]，扩展部分可移除——PRD「查看并扩展」语义）；
 * - small.threshold     = 小额币种阈值（基础法币数值串，>= 0；**0 = 不启用**小额归并；仪表盘
 *                         「其他」归并，M12 消费。自由数值——用户规模差异大（总额百元级到百万元级），
 *                         不设预设档、不强加默认值，由用户按自身规模设定（M10 走查反馈修复轮重设计））；
 * - network.proxy.enabled = 系统代理开关（"on"/"off"，默认 on；检测/指示行为随 M11 T11.3）；
 * - locale              = 界面语言（[AppLanguage] 的 storageValue，默认 "zh-CN"；键名见
 *                         [AppLanguage.SETTINGS_KEY]——M002 迁移已种下，M12 T12.4 起为其消费方：
 *                         全量文案与日期时间格式随此档切换，PRD §6 I18N）。
 *
 * 主题/盈亏配色（theme / pnl_scheme）沿用启动链既有键：设置页经 ShellViewModel 写入
 * （顶栏与设置双向同步 + 持久化），不经本用例（保持「状态在 ViewModel、持久化在引导层」单一通路）。
 */

/** 稳定币白名单默认值（PRD §7.2 模块 6.1：USDT/USDC/DAI/TUSD；与引擎 [PortfolioCalculator] 现金口径同源）。 */
val CASH_COIN_DEFAULTS: Set<String> = setOf("tether", "usd-coin", "dai", "true-usd")

/** 基础法币候选（原型设置页下拉口径：USD/EUR/CNY）。 */
val BASE_FIAT_OPTIONS: List<String> = listOf("USD", "EUR", "CNY")

/** 默认精度档（PRD：金额/价格默认 8 位，市值/盈亏/百分比默认 2 位）。 */
enum class PrecisionPreset(val storageValue: String, val label: String) {
    DEFAULT("8/2/2", "金额/价格 8 位 · 其余 2 位（默认）"),
    SIMPLIFIED("2/2/2", "统一 2 位"),
    ;

    companion object {
        fun fromStorage(value: String?): PrecisionPreset =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** 通用设置视图（ui 直接消费；扩展白名单 = [CASH_COIN_DEFAULTS] + [cashCoinExtra]）。 */
data class GeneralSettingsView(
    val baseFiat: String = "USD",
    val precision: PrecisionPreset = PrecisionPreset.DEFAULT,
    val usernameEnumOn: Boolean = true,
    /** 用户扩展的现金类币种（cg_id；默认白名单固定，扩展项可移除）。 */
    val cashCoinExtra: List<String> = emptyList(),
    /** 小额币种阈值（基础法币；0 = 不启用归并）。 */
    val smallThreshold: BigDecimal = BigDecimal.ZERO,
    val proxyEnabled: Boolean = true,
    /** 界面语言（M12 T12.4；默认中文）。 */
    val language: AppLanguage = AppLanguage.ZH,
) {
    /** 全量现金类币种（默认 ∪ 扩展；计「可用现金」口径消费，接线见模块记录 M10 §1）。 */
    val cashCoinIds: Set<String> = CASH_COIN_DEFAULTS + cashCoinExtra
}

/**
 * 通用设置用例（T10.1）：视图读取 + 单项写入（写后视图下次读取生效；「切换即时生效」由
 * 各消费点的运行期读取保证——法币折算每次按设置取值，白名单经注入 provider 运行期读取）。
 */
interface GeneralSettingsService {

    /** 当前通用设置视图。 */
    suspend fun view(): GeneralSettingsView

    /** 设置基础法币（计价与折算基准；候选 = [BASE_FIAT_OPTIONS]）。 */
    suspend fun setBaseFiat(code: String)

    /** 设置默认精度档。 */
    suspend fun setPrecision(preset: PrecisionPreset)

    /** 登录页用户名枚举开关（关闭后登录页用户名改为纯手动输入）。 */
    suspend fun setUsernameEnum(on: Boolean)

    /** 扩展稳定币白名单（cg_id，去重幂等；如 "usd-coin"）。 */
    suspend fun addCashCoin(cgId: String)

    /** 移除白名单扩展项（默认白名单项不可移除——PRD「扩展」语义；幂等）。 */
    suspend fun removeCashCoin(cgId: String)

    /** 设置小额币种阈值（基础法币，>= 0；0 = 不启用归并；非法值/负值抛 IllegalArgumentException）。 */
    suspend fun setSmallAmountThreshold(threshold: BigDecimal)

    /** 系统代理开关（默认开；检测与指示行为随 M11）。 */
    suspend fun setProxyEnabled(on: Boolean)

    /** 设置界面语言（M12 T12.4：全量文案 + 日期时间格式即时切换并持久化）。 */
    suspend fun setLanguage(language: AppLanguage)
}
