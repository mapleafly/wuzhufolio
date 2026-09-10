package com.wuzhufolio.ui.auth

import com.wuzhufolio.ui.i18n.authStrings

/**
 * 登录链路逐字文案（来源 = docs/design/prototype/wuzhufolio-light.html 唯一真源 + interaction.md A1–A4，
 * 规格提炼见 /tmp/m2-ux-spec.md；改文案先改原型/交互稿，再同步本文件）。
 *
 * M12 i18n（T12.4）：全部成员改写为**动态取值属性**，委派 [com.wuzhufolio.ui.i18n.AuthStrings]
 * （zh/en 双档，见 i18n/AuthStrings.kt）。成员名与签名保持不变，调用点无感；zh 档与改造前逐字一致
 * （UI 冒烟测试对逐字文案有断言）。
 *
 * 例外（非中文、zh/en 同形，故不入文案表，仍为常量）：[BRAND] 产品名、[LOGIN_PASSWORD_PLACEHOLDER]
 * 占位符、[DOT] 分隔符。
 */
object AuthCopy {

    const val BRAND = "WuZhuFolio"
    const val DOT = " · "
    const val LOGIN_PASSWORD_PLACEHOLDER = "••••••••"

    val BRAND_TAG: String get() = authStrings.brandTag

    // 登录页
    val LOGIN_TITLE: String get() = authStrings.loginTitle
    val LOGIN_SUBTITLE: String get() = authStrings.loginSubtitle
    val LOGIN_USER_HINT: String get() = authStrings.loginUserHint
    val LOGIN_ERROR_PASSWORD_EMPTY: String get() = authStrings.loginErrorPasswordEmpty
    val LOGIN_REMEMBER_LABEL: String get() = authStrings.loginRememberLabel
    val LOGIN_BUTTON: String get() = authStrings.loginButton
    val LOGIN_LOADING: String get() = authStrings.loginLoading
    val LOGIN_LINK_FORGOT: String get() = authStrings.loginLinkForgot
    val LOGIN_LINK_CREATE: String get() = authStrings.loginLinkCreate

    /** `%s` = 账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.loginToastOk]）。 */
    val LOGIN_TOAST_OK: String get() = authStrings.loginToastOkFormat

    // 创建页
    val CREATE_TITLE: String get() = authStrings.createTitle
    val CREATE_SUBTITLE: String get() = authStrings.createSubtitle
    val CREATE_USER_PLACEHOLDER: String get() = authStrings.createUserPlaceholder
    val CREATE_USER_ERROR_EMPTY: String get() = authStrings.createUserErrorEmpty
    val CREATE_PW_HINT: String get() = authStrings.createPwHint
    val CREATE_PW_ERROR_WEAK: String get() = authStrings.createPwErrorWeak
    val CREATE_PW2_PLACEHOLDER: String get() = authStrings.createPw2Placeholder
    val CREATE_PW2_ERROR_MISMATCH: String get() = authStrings.createPw2ErrorMismatch
    val CREATE_REMEMBER_LABEL: String get() = authStrings.createRememberLabel
    val CREATE_BUTTON: String get() = authStrings.createButton
    val CREATE_LINK_BACK: String get() = authStrings.createLinkBack
    val CREATE_ERROR_USERNAME_TAKEN: String get() = authStrings.createErrorUsernameTaken
    val STRENGTH_WEAK: String get() = authStrings.strengthWeak
    val STRENGTH_MEDIUM: String get() = authStrings.strengthMedium
    val STRENGTH_STRONG: String get() = authStrings.strengthStrong

    // 风险确认
    val RISK_TITLE: String get() = authStrings.riskTitle
    val RISK_BANNER_TITLE: String get() = authStrings.riskBannerTitle
    val RISK_BODY: String get() = authStrings.riskBody
    val RISK_AGREE: String get() = authStrings.riskAgree
    val RISK_CONFIRM: String get() = authStrings.riskConfirm
    val RISK_CANCEL: String get() = authStrings.cancel

    /** `%s` = 账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.createToastOk]）。 */
    val CREATE_TOAST_OK: String get() = authStrings.createToastOkFormat

    // 初始化向导
    val WIZARD_TITLE: String get() = authStrings.wizardTitle

    /** `%s` = 账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.wizardSubtitle]）。 */
    val WIZARD_SUBTITLE: String get() = authStrings.wizardSubtitleFormat
    val WIZARD_LATER: String get() = authStrings.wizardLater
    val WIZARD_RECOMMEND: String get() = authStrings.wizardRecommend

    /** `%s` = 模块名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.wizardPickToast]）。 */
    val WIZARD_PICK_TOAST: String get() = authStrings.wizardPickToastFormat
    val MODULE_MANUAL: String get() = authStrings.moduleManual
    val MODULE_CSV: String get() = authStrings.moduleCsv

    // M6/M9：向导「关联交易所 API」「从备份恢复」已可用（不再占位预告）
    val WIZARD_API_READY_TOAST: String get() = authStrings.wizardApiReadyToast
    val WIZARD_RESTORE_READY_TOAST: String get() = authStrings.wizardRestoreReadyToast

    // 忘记密码
    val FORGOT_TITLE: String get() = authStrings.forgotTitle
    val FORGOT_SUBTITLE: String get() = authStrings.forgotSubtitle
    val FORGOT_BANNER_TITLE: String get() = authStrings.forgotBannerTitle
    val FORGOT_BODY: String get() = authStrings.forgotBody
    val FORGOT_HINT: String get() = authStrings.forgotHint
    val FORGOT_BACK: String get() = authStrings.forgotBack

    // 统一异常（interaction A1–A4 / PRD §2.13；风险门控文案）
    val ERR_A1_LOGIN: String get() = authStrings.errA1Login
    val ERR_PASSWORD_MISMATCH: String get() = authStrings.errPasswordMismatch
    val ERR_A2_OLD_PASSWORD: String get() = authStrings.errA2OldPassword
    val ERR_A3_ACCOUNT_LOAD: String get() = authStrings.errA3AccountLoad
    val ERR_GENERIC: String get() = authStrings.errGeneric

    // 账户菜单 / 切换 / 改密 / 登出
    val ACCOUNT_MENU_SUB: String get() = authStrings.accountMenuSub
    val ACCOUNT_MENU_CHANGE_PW: String get() = authStrings.accountMenuChangePw
    val ACCOUNT_MENU_LOGOUT: String get() = authStrings.accountMenuLogout
    val ACCOUNT_MENU_TITLE: String get() = authStrings.accountMenuTitle

    /** `%s` = 当前账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.accountCurrent]）。 */
    val ACCOUNT_CURRENT: String get() = authStrings.accountCurrentFormat

    /** `%s` = 目标账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.switchTitle]）。 */
    val SWITCH_TITLE: String get() = authStrings.switchTitleFormat
    val SWITCH_LABEL: String get() = authStrings.switchLabel
    val SWITCH_HINT: String get() = authStrings.switchHint
    val SWITCH_CONFIRM: String get() = authStrings.switchConfirm
    val SWITCH_CANCEL: String get() = authStrings.cancel

    /** `%s` = 目标账户名（`String.format` 消费；参数化函数形态见 [com.wuzhufolio.ui.i18n.AuthStrings.switchToastOk]）。 */
    val SWITCH_TOAST_OK: String get() = authStrings.switchToastOkFormat
    val CHANGE_PW_TITLE: String get() = authStrings.changePwTitle
    val CHANGE_PW_OLD_LABEL: String get() = authStrings.changePwOldLabel
    val CHANGE_PW_NEW_PLACEHOLDER: String get() = authStrings.changePwNewPlaceholder
    val CHANGE_PW_NEW2_PLACEHOLDER: String get() = authStrings.changePwNew2Placeholder
    val CHANGE_PW_HINT: String get() = authStrings.changePwHint
    val CHANGE_PW_SAVE: String get() = authStrings.save
    val CHANGE_PW_TOAST_OK: String get() = authStrings.changePwToastOk
    val LOGOUT_TOAST: String get() = authStrings.logoutToast
    val CHANGE_PW_BUSY: String get() = authStrings.changePwBusy
    val SWITCH_BUSY: String get() = authStrings.switchBusy

    // 账户菜单 modal 通用
    val DIALOG_CLOSE: String get() = authStrings.close
}
