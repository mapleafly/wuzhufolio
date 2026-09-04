package com.wuzhufolio.ui.exchange

import com.wuzhufolio.domain.exchange.ExchangeError

/**
 * API 管理页文案（T6.4 · 以 P1 原型 wuzhufolio-light.html API 管理/添加弹窗为逐字基准 + PRD 4.1）。
 */
object ApiCopy {

    // ---- 设置页行 / 页面标题 ----

    const val GROUP_TITLE = "API 管理"
    const val GROUP_SUB = "Binance 只读密钥（字段级加密存储 · 自动增量同步）"
    const val PAGE_SUB = "添加交易所只读 API 后自动同步新成交（增量去重）· 仅返回最近 500 条，更早请 CSV 导入"

    const val STATUS_CONFIGURED = "已配置"
    const val STATUS_UNCONFIGURED = "未配置"
    const val KEY_MASK = "••••••••"

    const val LAST_SYNC_NEVER = "从未同步"
    const val LAST_SYNC_PREFIX = "最后同步"

    const val ADD_BUTTON = "＋ 添加 API（Binance 只读）"
    const val REMOVE = "移除"
    const val SYNC_NOW = "立即同步"
    const val SYNCING = "同步中…"

    const val SYNC_INTERVAL_LABEL = "API 同步间隔（交易数据）"
    const val SYNC_INTERVAL_SUB = "自动增量同步的间隔（默认 30 分钟）"

    // ---- 添加弹窗（原型 openApiAdd 逐字口径） ----

    const val ADD_TITLE = "添加 API（Binance 只读）"
    const val EDIT_TITLE = "编辑 API（Binance 只读）"
    const val NAME_LABEL = "别名"
    const val NAME_PLACEHOLDER = "如：币安主号"
    const val EXCHANGE_LABEL = "交易所"
    const val EXCHANGE_VALUE = "Binance（MVP）"
    const val API_KEY_LABEL = "API Key"
    const val API_KEY_PLACEHOLDER = "输入只读 API Key"
    const val SECRET_LABEL = "Secret Key"
    const val SECRET_PLACEHOLDER = "••••••••"
    const val HINT_READONLY = "· 仅需要只读权限（创建教程：交易所 API 管理页，浏览器打开）"
    const val HINT_ENCRYPTED = "· 密钥字段级加密存储（AES-256-GCM），仅存本地、不上传"
    const val HINT_ACCOUNT = "· 该 API 将关联当前登录账户"
    const val TEST_BUTTON = "测试请求"
    const val TEST_PASSED = "测试请求通过 · 已用该密钥获取账户信息（只读）"
    const val SAVE_BUTTON = "保存"
    const val SAVE_AND_SYNC_TOAST = "密钥已加密保存 · 请清理系统剪贴板 · 立即执行首次同步（增量去重）…"
    const val REMOVED_TOAST = "已移除 API 密钥"
    const val SYNC_DONE_TOAST = "同步完成 · 新增 %d · 去重跳过 %d"
    const val SYNC_PARTIAL_TOAST = "同步完成（部分）· 新增 %d · 剩余 %d 个交易对下轮续传"

    // ---- 校验 ----

    const val ERR_NAME_EMPTY = "请输入别名"
    const val ERR_KEY_EMPTY = "请输入 API Key"
    const val ERR_SECRET_EMPTY = "请输入 Secret Key"
    const val ERR_DUPLICATE = "同名 API 已存在（同一交易所内别名需唯一）"
    const val ERR_GENERIC = "操作失败，请重试"

    /** ExchangeError → 弹窗/行内文案（api-contracts §4 错误码映射）。 */
    fun errorText(error: ExchangeError): String = when (error) {
        ExchangeError.InvalidKey, ExchangeError.SignatureInvalid -> "Binance API 密钥已失效，请检查或更新（B2）"
        ExchangeError.RateLimited -> "Binance 限流触发，请稍后重试"
        ExchangeError.TimestampSkew -> "Binance 时间偏差，请稍后重试"
        ExchangeError.Network -> "网络不可达 Binance，请检查网络后重试"
        is ExchangeError.Http -> "Binance 请求失败（HTTP " + error.code + "）"
        is ExchangeError.Internal -> "内部错误：请查看日志后重试"
    }
}
