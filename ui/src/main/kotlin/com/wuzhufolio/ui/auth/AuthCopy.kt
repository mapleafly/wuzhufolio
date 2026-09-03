package com.wuzhufolio.ui.auth

/**
 * 登录链路逐字文案（来源 = docs/design/prototype/wuzhufolio-light.html 唯一真源 + interaction.md A1–A4，
 * 规格提炼见 /tmp/m2-ux-spec.md；改文案先改原型/交互稿，再同步本文件）。
 */
object AuthCopy {

    const val BRAND = "WuZhuFolio"
    const val BRAND_TAG = "数据本地化 · 零遥测 · 开源 AGPL-3.0"
    const val DOT = " · "

    // 登录页
    const val LOGIN_TITLE = "登录"
    const val LOGIN_SUBTITLE = "选择账户并输入密码 · 密码永不落盘"
    const val LOGIN_USER_HINT = "用户名枚举：开（可在 设置 -> 通用 关闭，关闭后改为纯手动输入）"
    const val LOGIN_PASSWORD_PLACEHOLDER = "••••••••"
    const val LOGIN_ERROR_PASSWORD_EMPTY = "请输入密码"
    const val LOGIN_REMEMBER_LABEL = "记住我（仅会话令牌存入系统钥匙串，密码永不落盘）"
    const val LOGIN_BUTTON = "登录"
    const val LOGIN_LOADING = "正在解密…"
    const val LOGIN_LINK_FORGOT = "忘记密码"
    const val LOGIN_LINK_CREATE = "创建新账户"
    const val LOGIN_TOAST_OK = "已登录「%s」· 数据已解密"

    // 创建页
    const val CREATE_TITLE = "创建新账户"
    const val CREATE_SUBTITLE = "密码强度校验 · 二次确认 · 风险确认"
    const val CREATE_USER_PLACEHOLDER = "为这个投资组合命名（如 Alex）"
    const val CREATE_USER_ERROR_EMPTY = "请输入用户名"
    const val CREATE_PW_HINT = "至少 8 位，需包含字母与数字"
    const val CREATE_PW_ERROR_WEAK = "密码需至少 8 位且包含字母与数字"
    const val CREATE_PW2_PLACEHOLDER = "再次输入密码"
    const val CREATE_PW2_ERROR_MISMATCH = "两次输入的密码不一致"
    const val CREATE_REMEMBER_LABEL = "记住我（会话令牌存入系统钥匙串）"
    const val CREATE_BUTTON = "创建账户"
    const val CREATE_LINK_BACK = "返回登录"
    const val CREATE_ERROR_USERNAME_TAKEN = "用户名已存在"
    const val STRENGTH_WEAK = "弱"
    const val STRENGTH_MEDIUM = "中"
    const val STRENGTH_STRONG = "强"

    // 风险确认
    const val RISK_TITLE = "风险确认"
    const val RISK_BANNER_TITLE = "风险提示 · 请仔细阅读"
    const val RISK_BODY = "密码是数据的唯一钥匙，本产品不上传也不存储密码，忘记密码将无法恢复任何数据；请妥善保管密码并定期备份。"
    const val RISK_AGREE = "我已了解上述风险"
    const val RISK_CONFIRM = "确认创建"
    const val RISK_CANCEL = "取消"
    const val CREATE_TOAST_OK = "账户「%s」已创建 · 请完成初始化"

    // 初始化向导
    const val WIZARD_TITLE = "欢迎使用 WuZhuFolio"
    const val WIZARD_SUBTITLE = "为账户「%s」选择一种初始化方式 · 之后可随时在应用内继续"
    const val WIZARD_LATER = "稍后再说，直接进入仪表盘"
    const val WIZARD_RECOMMEND = "推荐"
    const val WIZARD_PICK_TOAST = "该功能将在%s模块上线后开放，敬请期待"
    const val MODULE_MANUAL = "交易管理（手动录入）"
    const val MODULE_CSV = "交易管理（CSV 导入）"
    const val MODULE_API = "交易所同步"
    const val MODULE_RESTORE = "数据备份恢复"

    // 忘记密码
    const val FORGOT_TITLE = "忘记密码"
    const val FORGOT_SUBTITLE = "本地加密 · 无法在线找回"
    const val FORGOT_BANNER_TITLE = "请知悉"
    const val FORGOT_BODY = "本产品无法找回密码。若记得某次备份使用的密码，可通过该备份恢复数据；否则数据不可恢复。"
    const val FORGOT_HINT = "创建账户时已明确提示该风险 · 定期备份是唯一的数据保障"
    const val FORGOT_BACK = "返回登录"

    // 统一异常（interaction A1–A4 / PRD §2.13；风险门控文案）
    const val ERR_A1_LOGIN = "用户名或密码错误"
    const val ERR_PASSWORD_MISMATCH = "密码错误"
    const val ERR_A2_OLD_PASSWORD = "原密码不正确"
    const val ERR_A3_ACCOUNT_LOAD = "账户数据加载失败，请重试"
    const val ERR_GENERIC = "操作失败，请重试"

    // 账户菜单 / 切换 / 改密 / 登出
    const val ACCOUNT_MENU_SUB = "切换账户 / 登出"
    const val ACCOUNT_CURRENT = "✓ %s（当前）"
    const val ACCOUNT_MENU_CHANGE_PW = "修改密码"
    const val ACCOUNT_MENU_LOGOUT = "登出"
    const val ACCOUNT_MENU_TITLE = "账户"
    const val SWITCH_TITLE = "切换到账户「%s」"
    const val SWITCH_LABEL = "输入目标账户密码（严格模式）"
    const val SWITCH_HINT = "切换将锁定当前账户并解密新账户数据，无需重启"
    const val SWITCH_CONFIRM = "验证并切换"
    const val SWITCH_CANCEL = "取消"
    const val SWITCH_TOAST_OK = "已切换到账户「%s」"
    const val CHANGE_PW_TITLE = "修改密码"
    const val CHANGE_PW_OLD_LABEL = "原密码"
    const val CHANGE_PW_NEW_PLACEHOLDER = "至少 8 位，含字母与数字"
    const val CHANGE_PW_NEW2_PLACEHOLDER = "再次输入"
    const val CHANGE_PW_HINT = "改密仅用新密码重新包裹账户密钥（KEK），数据不整体重新加密；历史备份文件仍按导出时设置的备份密码解密；改密后原「记住我」会话令牌立即失效，需重新登录。"
    const val CHANGE_PW_SAVE = "保存"
    const val CHANGE_PW_TOAST_OK = "密码已修改 · 仅重新包裹 KEK · 请重新登录"
    const val LOGOUT_TOAST = "已登出 · 会话令牌已清除"
    const val CHANGE_PW_BUSY = "正在更新…"
    const val SWITCH_BUSY = "正在验证…"

    // 账户菜单 modal 通用
    const val DIALOG_CLOSE = "关闭"
}
