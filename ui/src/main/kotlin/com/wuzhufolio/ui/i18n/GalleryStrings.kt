package com.wuzhufolio.ui.i18n

/**
 * 组件走查页文案（M0 开发期组件库走查页；M12 T12.4 zh/en 双档）。
 *
 * 目录约定同 [CommonStrings]：接口 + Zh/En 两个实现 + `galleryStrings` 访问器；
 * 文案写成动态取值属性，随当前语言解析，切换语言由 `WuzhuTheme` 的
 * `key(language.code)` 重建子树生效。
 *
 * zh 档一律与走查页原文逐字一致（既有 UI 测试/走查基线以此为锚），en 档为面向开发者的
 * 自然英文；示例用文案（如「已保存（示例）」）也一并入表，不在调用点留硬编码。
 */
interface GalleryStrings {

    /** 页面标题（侧边栏同名入口文案见 `ShellStrings.navGallery`；供走查页页头使用）。 */
    val title: String

    // 字体层级
    val typographySection: String
    val pageTitleSample: String
    val bodySample: String
    val tableNumberSample: String
    val captionSample: String

    // 按钮
    val buttonsSection: String
    val primaryButton: String
    val secondaryButton: String
    val dangerButton: String
    val disabledButton: String

    // 输入框
    val textFieldsSection: String
    val accountNameLabel: String
    val accountNamePlaceholder: String
    val quantityLabel: String
    val quantityError: String

    // 数据表
    val tableSection: String
    val symbolColumn: String
    val pnl24hColumn: String

    // Modal
    val modalSection: String
    val openModal: String
    val modalTitle: String
    val fieldLabel: String
    val fieldPlaceholder: String

    // Toast
    val toastSection: String
    val successToast: String
    val successToastSample: String
    val failureToast: String
    val failureToastSample: String

    // 语义色与盈亏配色方案
    val semanticColorsSection: String
    val pnlGreenUp: String
    val pnlRedUp: String
    val pnlColorblind: String
}

object GalleryStringsZh : GalleryStrings {
    override val title = "组件走查"

    override val typographySection = "字体层级（design-tokens §3）"
    override val pageTitleSample = "页面标题 20/600"
    override val bodySample = "正文 14：隐私、本地、可信、数据优先。"
    override val tableNumberSample = "表格数字（等宽 tnum）：0.05432100  +31.26%"
    override val captionSample = "注释/时间戳 11：2026-08-31 12:00 UTC"

    override val buttonsSection = "按钮（主/次/危险 + 禁用）"
    override val primaryButton = "主按钮"
    override val secondaryButton = "次按钮"
    override val dangerButton = "危险按钮"
    override val disabledButton = "禁用"

    override val textFieldsSection = "输入框（正常 / 错误态）"
    override val accountNameLabel = "账户名称"
    override val accountNamePlaceholder = "例如：主账户"
    override val quantityLabel = "数量"
    override val quantityError = "数量必须大于 0"

    override val tableSection = "数据表（表头 / hover / 选中行）"
    override val symbolColumn = "币种"
    override val pnl24hColumn = "24h 盈亏"

    override val modalSection = "Modal（esc / 遮罩点击 / 关闭按钮可关）"
    override val openModal = "打开 Modal"
    override val modalTitle = "示例 Modal"
    override val fieldLabel = "字段"
    override val fieldPlaceholder = "输入内容"

    override val toastSection = "Toast（成功 / 失败，3s 自动消失）"
    override val successToast = "成功 Toast"
    override val successToastSample = "已保存（示例）"
    override val failureToast = "失败 Toast"
    override val failureToastSample = "同步失败（示例）"

    override val semanticColorsSection = "语义色与盈亏配色方案（design-tokens §2.3，强制 +/- 符号）"
    override val pnlGreenUp = "绿涨红跌"
    override val pnlRedUp = "红涨绿跌"
    override val pnlColorblind = "色盲友好"
}

object GalleryStringsEn : GalleryStrings {
    override val title = "Component gallery"

    override val typographySection = "Type scale (design-tokens §3)"
    override val pageTitleSample = "Page title 20/600"
    override val bodySample = "Body 14: private, local, trustworthy, data first."
    override val tableNumberSample = "Table numbers (mono tnum): 0.05432100  +31.26%"
    override val captionSample = "Caption/timestamp 11: 2026-08-31 12:00 UTC"

    override val buttonsSection = "Buttons (primary / secondary / danger + disabled)"
    override val primaryButton = "Primary"
    override val secondaryButton = "Secondary"
    override val dangerButton = "Danger"
    override val disabledButton = "Disabled"

    override val textFieldsSection = "Text fields (normal / error)"
    override val accountNameLabel = "Account name"
    override val accountNamePlaceholder = "e.g. Main account"
    override val quantityLabel = "Quantity"
    override val quantityError = "Quantity must be greater than 0"

    override val tableSection = "Table (header / hover / selected row)"
    override val symbolColumn = "Symbol"
    override val pnl24hColumn = "24h P&L"

    override val modalSection = "Modal (dismissed by Esc / scrim click / close button)"
    override val openModal = "Open modal"
    override val modalTitle = "Sample modal"
    override val fieldLabel = "Field"
    override val fieldPlaceholder = "Enter text"

    override val toastSection = "Toasts (success / failure, auto-dismiss after 3s)"
    override val successToast = "Success toast"
    override val successToastSample = "Saved (sample)"
    override val failureToast = "Failure toast"
    override val failureToastSample = "Sync failed (sample)"

    override val semanticColorsSection =
        "Semantic colors and P&L color schemes (design-tokens §2.3, +/- sign required)"
    override val pnlGreenUp = "Green up / red down"
    override val pnlRedUp = "Red up / green down"
    override val pnlColorblind = "Colorblind friendly"
}

val galleryStrings: GalleryStrings get() = if (I18n.isZh) GalleryStringsZh else GalleryStringsEn
