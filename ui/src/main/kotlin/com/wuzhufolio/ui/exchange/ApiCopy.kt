package com.wuzhufolio.ui.exchange

import com.wuzhufolio.domain.exchange.ExchangeError
import com.wuzhufolio.ui.i18n.commonStrings
import com.wuzhufolio.ui.i18n.exchangeStrings

/**
 * API 管理页文案（T6.4 · 以 P1 原型 wuzhufolio-light.html API 管理/添加弹窗为逐字基准 + PRD 4.1）。
 *
 * M12 T12.4 i18n：成员一律为**动态取值属性**（`val X: String get() = exchangeStrings.x`），
 * 每次读取按当前语言解析（见 `i18n/I18n.kt`）——成员名与类型保持不变，调用点零改动；
 * 模块文案本体在 `ui/i18n/ExchangeStrings.kt`（zh 档与改版前逐字一致）。
 *
 * 两处例外，均为跨模块通用按钮（避免与公共词条重复）：[SAVE_BUTTON] / [REMOVE] 取 [commonStrings]。
 */
object ApiCopy {

    // ---- 设置页行 / 页面标题 ----

    val GROUP_TITLE: String get() = exchangeStrings.groupTitle
    val GROUP_SUB: String get() = exchangeStrings.groupSub
    val PAGE_SUB: String get() = exchangeStrings.pageSub

    val STATUS_CONFIGURED: String get() = exchangeStrings.statusConfigured
    val STATUS_UNCONFIGURED: String get() = exchangeStrings.statusUnconfigured
    val KEY_MASK: String get() = exchangeStrings.keyMask

    val LAST_SYNC_NEVER: String get() = exchangeStrings.lastSyncNever
    val LAST_SYNC_PREFIX: String get() = exchangeStrings.lastSyncPrefix

    val ADD_BUTTON: String get() = exchangeStrings.addButton
    val REMOVE: String get() = commonStrings.remove
    val SYNC_NOW: String get() = exchangeStrings.syncNow
    val SYNCING: String get() = exchangeStrings.syncing

    /** 页面级手动同步（PRD 故事 4.3；2026-09-08 走查补口：入口不随密钥列表为空而消失）。 */
    val SYNC_ALL_BUTTON: String get() = exchangeStrings.syncAllButton
    val SYNC_ALL_EMPTY: String get() = exchangeStrings.syncAllEmpty

    /** 顶栏手动同步汇总（全部成功）。 */
    fun syncAllDone(keys: Int, newTrades: Int): String = exchangeStrings.syncAllDone(keys, newTrades)

    /** 顶栏手动同步汇总（部分失败）。 */
    fun syncAllPartial(failedKeys: Int, newTrades: Int): String =
        exchangeStrings.syncAllPartial(failedKeys, newTrades)
    val EMPTY_HINT: String get() = exchangeStrings.emptyHint

    // （API 同步间隔文案随 M10 归位 ui/settings/SettingsCopy.kt——M6 遗留「行情与同步」分组归位）

    // ---- 添加弹窗（原型 openApiAdd 逐字口径） ----

    val ADD_TITLE: String get() = exchangeStrings.addTitle
    val EDIT_TITLE: String get() = exchangeStrings.editTitle
    val NAME_LABEL: String get() = exchangeStrings.nameLabel
    val NAME_PLACEHOLDER: String get() = exchangeStrings.namePlaceholder
    val EXCHANGE_LABEL: String get() = exchangeStrings.exchangeLabel
    val EXCHANGE_VALUE: String get() = exchangeStrings.exchangeValue
    val API_KEY_LABEL: String get() = exchangeStrings.apiKeyLabel
    val API_KEY_PLACEHOLDER: String get() = exchangeStrings.apiKeyPlaceholder
    val SECRET_LABEL: String get() = exchangeStrings.secretLabel
    val SECRET_PLACEHOLDER: String get() = exchangeStrings.secretPlaceholder
    val HINT_READONLY: String get() = exchangeStrings.hintReadOnly
    val HINT_ENCRYPTED: String get() = exchangeStrings.hintEncrypted
    val HINT_ACCOUNT: String get() = exchangeStrings.hintAccount
    val TEST_BUTTON: String get() = exchangeStrings.testButton
    val TEST_PASSED: String get() = exchangeStrings.testPassed
    val SAVE_BUTTON: String get() = commonStrings.save
    val SAVE_AND_SYNC_TOAST: String get() = exchangeStrings.saveAndSyncToast

    /** 首次同步完成 toast（`%d` 模板；参数化入口见 `exchangeStrings.firstSyncDone`）。 */
    val SAVE_AND_SYNC_RESULT: String get() = exchangeStrings.firstSyncDoneTemplate
    val REMOVED_TOAST: String get() = exchangeStrings.removedToast
    val KEY_UPDATED_TOAST: String get() = exchangeStrings.keyUpdatedToast

    /** 同步完成 toast（`%d` = 新增 / 去重跳过；参数化入口见 `exchangeStrings.syncDone`）。 */
    val SYNC_DONE_TOAST: String get() = exchangeStrings.syncDoneTemplate

    /** 部分同步 toast（`%d` = 新增 / 下轮续传交易对数；参数化入口见 `exchangeStrings.syncPartial`）。 */
    val SYNC_PARTIAL_TOAST: String get() = exchangeStrings.syncPartialTemplate

    // ---- 校验 ----

    val ERR_NAME_EMPTY: String get() = exchangeStrings.errNameEmpty
    val ERR_KEY_EMPTY: String get() = exchangeStrings.errKeyEmpty
    val ERR_SECRET_EMPTY: String get() = exchangeStrings.errSecretEmpty
    val ERR_UPDATE_CREDS_PAIR: String get() = exchangeStrings.errUpdateCredsPair
    val ERR_DUPLICATE: String get() = exchangeStrings.errDuplicate
    val ERR_GENERIC: String get() = exchangeStrings.errGeneric

    // ---- 编辑回显（安全口径：密钥不回显明文/掩码，留空 = 保持不变） ----

    val EDIT_KEY_PLACEHOLDER: String get() = exchangeStrings.editKeyPlaceholder
    val EDIT_SECRET_PLACEHOLDER: String get() = exchangeStrings.editSecretPlaceholder

    /** ExchangeError → 弹窗/行内文案（api-contracts §4 错误码映射）。 */
    fun errorText(error: ExchangeError): String = when (error) {
        ExchangeError.InvalidKey, ExchangeError.SignatureInvalid -> exchangeStrings.errKeyInvalidB2
        ExchangeError.RateLimited -> exchangeStrings.errRateLimited
        ExchangeError.TimestampSkew -> exchangeStrings.errTimestampSkew
        ExchangeError.Network -> exchangeStrings.errNetwork
        is ExchangeError.Http -> exchangeStrings.errHttp(error.code)
        is ExchangeError.Internal -> exchangeStrings.errInternal
    }
}
