package com.wuzhufolio.ui.i18n

/**
 * 登录链路文案（AuthGate 门控全链路：登录 / 创建 / 风险确认 / 初始化向导 / 忘记密码 / 账户菜单 / 切换 / 改密 / 登出）。
 *
 * 目录约定见 [CommonStrings] 头注释；本表两点补充说明：
 * 1. zh 档与既有逐字文案**字节一致**（来源 = docs/design/prototype/wuzhufolio-light.html 唯一真源 +
 *    interaction.md A1–A4，M2 起由 `object AuthCopy` 承载）——UI 冒烟测试对逐字文案有断言，不得改写；
 * 2. 参数化文案（原 `%s` 模板）提供两种同源形态：`...Format` 模板（唯一真源）+
 *    接口函数（约定第 3 条，新调用点直接用函数）。`AuthCopy` 的既有成员签名冻结为
 *    `String.format` 消费的 `String`，故委派模板；模板与函数同源，不会漂移。
 *
 * 未入表：产品名 `WuZhuFolio`、密码占位符 `••••••••`、分隔符 ` · `（zh/en 同形，见 [com.wuzhufolio.ui.auth.AuthCopy]）。
 */
interface AuthStrings {

    // ---------- 品牌 ----------
    val brandTag: String

    // ---------- 登录页 ----------
    val loginTitle: String
    val loginSubtitle: String
    val loginUserHint: String
    val loginErrorPasswordEmpty: String
    val loginRememberLabel: String
    val loginButton: String
    val loginLoading: String
    val loginLinkForgot: String
    val loginLinkCreate: String
    val loginToastOkFormat: String

    fun loginToastOk(username: String): String = loginToastOkFormat.replace("%s", username)

    // ---------- 创建页 ----------
    val createTitle: String
    val createSubtitle: String
    val createUserPlaceholder: String
    val createUserErrorEmpty: String
    val createPwHint: String
    val createPwErrorWeak: String
    val createPw2Placeholder: String
    val createPw2ErrorMismatch: String
    val createRememberLabel: String
    val createButton: String
    val createLinkBack: String
    val createErrorUsernameTaken: String
    val createToastOkFormat: String

    fun createToastOk(username: String): String = createToastOkFormat.replace("%s", username)

    // 密码强度
    val strengthWeak: String
    val strengthMedium: String
    val strengthStrong: String

    // ---------- 风险确认（法定产品文案，逐字完整） ----------
    val riskTitle: String
    val riskBannerTitle: String
    val riskBody: String
    val riskAgree: String
    val riskConfirm: String

    // ---------- 初始化向导 ----------
    val wizardTitle: String
    val wizardSubtitleFormat: String
    val wizardLater: String
    val wizardRecommend: String
    val wizardPickToastFormat: String
    val moduleManual: String
    val moduleCsv: String
    val wizardApiReadyToast: String
    val wizardRestoreReadyToast: String

    fun wizardSubtitle(username: String): String = wizardSubtitleFormat.replace("%s", username)

    fun wizardPickToast(module: String): String = wizardPickToastFormat.replace("%s", module)

    // ---------- 忘记密码（法定产品文案，逐字完整） ----------
    val forgotTitle: String
    val forgotSubtitle: String
    val forgotBannerTitle: String
    val forgotBody: String
    val forgotHint: String
    val forgotBack: String

    // ---------- 统一异常（interaction A1–A4 / PRD §2.13） ----------
    val errA1Login: String
    val errPasswordMismatch: String
    val errA2OldPassword: String
    val errA3AccountLoad: String
    val errGeneric: String

    // ---------- 账户菜单 / 切换 / 改密 / 登出 ----------
    val accountMenuSub: String
    val accountMenuTitle: String
    val accountMenuChangePw: String
    val accountMenuLogout: String
    val accountCurrentFormat: String
    val switchTitleFormat: String
    val switchLabel: String
    val switchHint: String
    val switchConfirm: String
    val switchToastOkFormat: String
    val changePwTitle: String
    val changePwOldLabel: String
    val changePwNewLabel: String
    val changePwNewPlaceholder: String
    val changePwNew2Label: String
    val changePwNew2Placeholder: String
    val changePwHint: String
    val changePwToastOk: String
    val changePwBusy: String
    val switchBusy: String
    val logoutToast: String

    fun accountCurrent(username: String): String = accountCurrentFormat.replace("%s", username)

    fun switchTitle(username: String): String = switchTitleFormat.replace("%s", username)

    fun switchToastOk(username: String): String = switchToastOkFormat.replace("%s", username)

    // ---------- 表单字段标签（GatePages / AccountDialogs） ----------
    val fieldUsername: String
    val fieldPassword: String
    val fieldConfirmPassword: String

    // ---------- 向导卡片（GatePages） ----------
    val wizardManualTitle: String
    val wizardManualDesc: String
    val wizardCsvTitle: String
    val wizardCsvDesc: String
    val wizardApiTitle: String
    val wizardApiDesc: String
    val wizardRestoreTitle: String
    val wizardRestoreDesc: String

    // ---------- 门控通用 ----------
    val loadingSession: String
    val cancel: String
    val save: String
    val close: String
}

object AuthStringsZh : AuthStrings {
    override val brandTag = "数据本地化 · 零遥测 · 开源 AGPL-3.0"
    override val loginTitle = "登录"
    override val loginSubtitle = "选择账户并输入密码 · 密码永不落盘"
    override val loginUserHint = "用户名枚举：开（可在 设置 -> 通用 关闭，关闭后改为纯手动输入）"
    override val loginErrorPasswordEmpty = "请输入密码"
    override val loginRememberLabel = "记住我（仅会话令牌存入系统钥匙串，密码永不落盘）"
    override val loginButton = "登录"
    override val loginLoading = "正在解密…"
    override val loginLinkForgot = "忘记密码"
    override val loginLinkCreate = "创建新账户"
    override val loginToastOkFormat = "已登录「%s」· 数据已解密"

    override val createTitle = "创建新账户"
    override val createSubtitle = "密码强度校验 · 二次确认 · 风险确认"
    override val createUserPlaceholder = "为这个投资组合命名（如 Alex）"
    override val createUserErrorEmpty = "请输入用户名"
    override val createPwHint = "至少 8 位，需包含字母与数字"
    override val createPwErrorWeak = "密码需至少 8 位且包含字母与数字"
    override val createPw2Placeholder = "再次输入密码"
    override val createPw2ErrorMismatch = "两次输入的密码不一致"
    override val createRememberLabel = "记住我（会话令牌存入系统钥匙串）"
    override val createButton = "创建账户"
    override val createLinkBack = "返回登录"
    override val createErrorUsernameTaken = "用户名已存在"
    override val createToastOkFormat = "账户「%s」已创建 · 请完成初始化"

    override val strengthWeak = "弱"
    override val strengthMedium = "中"
    override val strengthStrong = "强"

    override val riskTitle = "风险确认"
    override val riskBannerTitle = "风险提示 · 请仔细阅读"
    override val riskBody =
        "密码是数据的唯一钥匙，本产品不上传也不存储密码，忘记密码将无法恢复任何数据；请妥善保管密码并定期备份。"
    override val riskAgree = "我已了解上述风险"
    override val riskConfirm = "确认创建"

    override val wizardTitle = "欢迎使用 WuZhuFolio"
    override val wizardSubtitleFormat = "为账户「%s」选择一种初始化方式 · 之后可随时在应用内继续"
    override val wizardLater = "稍后再说，直接进入仪表盘"
    override val wizardRecommend = "推荐"
    override val wizardPickToastFormat = "该功能将在%s模块上线后开放，敬请期待"
    override val moduleManual = "交易管理（手动录入）"
    override val moduleCsv = "交易管理（CSV 导入）"
    override val wizardApiReadyToast = "已进入设置 · 在「API 管理」中添加 Binance 只读密钥后将自动首次同步"
    override val wizardRestoreReadyToast =
        "已进入设置 · 在「数据管理」中选择 .cpro 备份文件即可恢复（PRD 5.2-8：导入数据归入当前账户）"

    override val forgotTitle = "忘记密码"
    override val forgotSubtitle = "本地加密 · 无法在线找回"
    override val forgotBannerTitle = "请知悉"
    override val forgotBody = "本产品无法找回密码。若记得某次备份使用的密码，可通过该备份恢复数据；否则数据不可恢复。"
    override val forgotHint = "创建账户时已明确提示该风险 · 定期备份是唯一的数据保障"
    override val forgotBack = "返回登录"

    override val errA1Login = "用户名或密码错误"
    override val errPasswordMismatch = "密码错误"
    override val errA2OldPassword = "原密码不正确"
    override val errA3AccountLoad = "账户数据加载失败，请重试"
    override val errGeneric = "操作失败，请重试"

    override val accountMenuSub = "切换账户 / 登出"
    override val accountMenuTitle = "账户"
    override val accountMenuChangePw = "修改密码"
    override val accountMenuLogout = "登出"
    override val accountCurrentFormat = "✓ %s（当前）"
    override val switchTitleFormat = "切换到账户「%s」"
    override val switchLabel = "输入目标账户密码（严格模式）"
    override val switchHint = "切换将锁定当前账户并解密新账户数据，无需重启"
    override val switchConfirm = "验证并切换"
    override val switchToastOkFormat = "已切换到账户「%s」"
    override val changePwTitle = "修改密码"
    override val changePwOldLabel = "原密码"
    override val changePwNewLabel = "新密码"
    override val changePwNewPlaceholder = "至少 8 位，含字母与数字"
    override val changePwNew2Label = "确认新密码"
    override val changePwNew2Placeholder = "再次输入"
    override val changePwHint =
        "改密仅用新密码重新包裹账户密钥（KEK），数据不整体重新加密；历史备份文件仍按导出时设置的备份密码解密；" +
            "改密后原「记住我」会话令牌立即失效，需重新登录。"
    override val changePwToastOk = "密码已修改 · 仅重新包裹 KEK · 请重新登录"
    override val changePwBusy = "正在更新…"
    override val switchBusy = "正在验证…"
    override val logoutToast = "已登出 · 会话令牌已清除"

    override val fieldUsername = "用户名"
    override val fieldPassword = "密码"
    override val fieldConfirmPassword = "确认密码"

    override val wizardManualTitle = "手动添加交易"
    override val wizardManualDesc = "逐条录入买入 / 卖出记录，构建初始持仓"
    override val wizardCsvTitle = "CSV 导入"
    override val wizardCsvDesc = "导入交易所导出的 CSV 批量历史（可先下载标准模板）"
    override val wizardApiTitle = "关联交易所 API"
    override val wizardApiDesc = "只读密钥自动同步 · 保存后立即执行首次同步"
    override val wizardRestoreTitle = "从备份恢复"
    override val wizardRestoreDesc = "从 .cpro 备份文件恢复全部数据（已创建目标账户）"

    override val loadingSession = "加载会话…"
    override val cancel = "取消"
    override val save = "保存"
    override val close = "关闭"
}

object AuthStringsEn : AuthStrings {
    override val brandTag = "Local data · Zero telemetry · AGPL-3.0 open source"
    override val loginTitle = "Sign in"
    override val loginSubtitle = "Choose an account and enter your password · the password is never stored"
    override val loginUserHint =
        "Username enumeration: on (turn it off under Settings -> General to type the username manually)"
    override val loginErrorPasswordEmpty = "Enter your password"
    override val loginRememberLabel =
        "Remember me (only the session token goes to the system keychain; the password is never stored)"
    override val loginButton = "Sign in"
    override val loginLoading = "Decrypting…"
    override val loginLinkForgot = "Forgot password"
    override val loginLinkCreate = "Create account"
    override val loginToastOkFormat = "Signed in as “%s” · data decrypted"

    override val createTitle = "Create account"
    override val createSubtitle = "Password strength · confirmation · risk acknowledgement"
    override val createUserPlaceholder = "Name this portfolio (e.g. Alex)"
    override val createUserErrorEmpty = "Enter a username"
    override val createPwHint = "At least 8 characters, with letters and digits"
    override val createPwErrorWeak = "Password must be at least 8 characters and include letters and digits"
    override val createPw2Placeholder = "Re-enter the password"
    override val createPw2ErrorMismatch = "Passwords do not match"
    override val createRememberLabel = "Remember me (session token kept in the system keychain)"
    override val createButton = "Create account"
    override val createLinkBack = "Back to sign in"
    override val createErrorUsernameTaken = "Username already exists"
    override val createToastOkFormat = "Account “%s” created · please complete the setup"

    override val strengthWeak = "Weak"
    override val strengthMedium = "Medium"
    override val strengthStrong = "Strong"

    override val riskTitle = "Risk acknowledgement"
    override val riskBannerTitle = "Risk notice · read carefully"
    override val riskBody =
        "The password is the only key to your data; this product never uploads or stores it, and a forgotten " +
            "password means no data can be recovered. Keep your password safe and back up regularly."
    override val riskAgree = "I understand these risks"
    override val riskConfirm = "Create account"

    override val wizardTitle = "Welcome to WuZhuFolio"
    override val wizardSubtitleFormat = "Choose how to set up account “%s” · you can continue in the app at any time"
    override val wizardLater = "Skip for now — go to the dashboard"
    override val wizardRecommend = "Recommended"
    override val wizardPickToastFormat = "This feature opens once the %s module ships — stay tuned"
    override val moduleManual = "Transactions (manual entry)"
    override val moduleCsv = "Transactions (CSV import)"
    override val wizardApiReadyToast =
        "Settings opened · add a Binance read-only key under “API management” and the first sync runs automatically"
    override val wizardRestoreReadyToast =
        "Settings opened · choose a .cpro backup file under “Data management” to restore " +
            "(PRD 5.2-8: imported data belongs to the current account)"

    override val forgotTitle = "Forgot password"
    override val forgotSubtitle = "Encrypted locally · no online recovery"
    override val forgotBannerTitle = "Please note"
    override val forgotBody =
        "This product cannot recover your password. If you remember the password used for one of your backups, " +
            "you can restore the data from that backup; otherwise the data cannot be recovered."
    override val forgotHint =
        "This risk was stated clearly when the account was created · regular backups are the only safeguard " +
            "for your data"
    override val forgotBack = "Back to sign in"

    override val errA1Login = "Incorrect username or password"
    override val errPasswordMismatch = "Incorrect password"
    override val errA2OldPassword = "Current password is incorrect"
    override val errA3AccountLoad = "Failed to load account data — please retry"
    override val errGeneric = "Something went wrong — please retry"

    override val accountMenuSub = "Switch account / sign out"
    override val accountMenuTitle = "Account"
    override val accountMenuChangePw = "Change password"
    override val accountMenuLogout = "Sign out"
    override val accountCurrentFormat = "✓ %s (current)"
    override val switchTitleFormat = "Switch to account “%s”"
    override val switchLabel = "Enter the target account password (strict mode)"
    override val switchHint = "Switching locks the current account and decrypts the new one — no restart required"
    override val switchConfirm = "Verify and switch"
    override val switchToastOkFormat = "Switched to account “%s”"
    override val changePwTitle = "Change password"
    override val changePwOldLabel = "Current password"
    override val changePwNewLabel = "New password"
    override val changePwNewPlaceholder = "At least 8 characters, with letters and digits"
    override val changePwNew2Label = "Confirm new password"
    override val changePwNew2Placeholder = "Re-enter"
    override val changePwHint =
        "Changing the password only re-wraps the account key (KEK) with the new password — data is not " +
            "re-encrypted as a whole. Existing backup files still decrypt with the password set when they were " +
            "exported. The previous “remember me” session token is invalidated immediately and you must sign in again."
    override val changePwToastOk = "Password changed · KEK re-wrapped only · please sign in again"
    override val changePwBusy = "Updating…"
    override val switchBusy = "Verifying…"
    override val logoutToast = "Signed out · session token cleared"

    override val fieldUsername = "Username"
    override val fieldPassword = "Password"
    override val fieldConfirmPassword = "Confirm password"

    override val wizardManualTitle = "Add transactions manually"
    override val wizardManualDesc = "Enter buy / sell records one by one to build the initial positions"
    override val wizardCsvTitle = "CSV import"
    override val wizardCsvDesc = "Import a batch of history from an exchange CSV (a standard template is available)"
    override val wizardApiTitle = "Connect exchange API"
    override val wizardApiDesc = "Read-only key syncs automatically · the first sync runs right after saving"
    override val wizardRestoreTitle = "Restore from backup"
    override val wizardRestoreDesc = "Restore all data from a .cpro backup file (target account already created)"

    override val loadingSession = "Loading session…"
    override val cancel = "Cancel"
    override val save = "Save"
    override val close = "Close"
}

val authStrings: AuthStrings get() = if (I18n.isZh) AuthStringsZh else AuthStringsEn
