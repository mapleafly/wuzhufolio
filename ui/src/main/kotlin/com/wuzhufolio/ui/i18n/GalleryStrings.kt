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

    // ── Material 3 框架对照区（ADR-007 §2.3）────────────────────────────────
    // 目的：让「自绘组件 vs 官方 M3 组件」在同一主题/同一页并排出现，供人工目视拍板风格方向；
    // 也是 DEF-54（数值输入清洗）与 DEF-55（日期时间选择器）的目标形态演示。
    /** 区块标题。 */
    val m3Section: String
    /** 按钮行标签。 */
    val m3ButtonsLabel: String
    /** 输入行标签。 */
    val m3InputsLabel: String
    /** 选择控件行标签。 */
    val m3SelectionLabel: String
    /** 容器与列表行标签。 */
    val m3ContainersLabel: String
    /** 反馈控件行标签。 */
    val m3FeedbackLabel: String
    /** 日期时间行标签。 */
    val m3DateTimeLabel: String
    /** 数值输入（粘贴清洗）演示标签：提示「粘贴 1,234.56 或带空格/全角数字会自动清洗」。 */
    val m3NumericDemoLabel: String
    /** 数值输入演示的标签（价格）。 */
    val m3NumericDemoField: String
    /** 示例条目文案。 */
    val m3SampleItem: String
    /** 示例提示气泡文案。 */
    val m3TooltipSample: String
    /** 示例按钮文案（确认）。 */
    val m3Confirm: String
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

    override val m3Section = "Material 3 框架对照区（ADR-007：官方组件 vs 自绘组件，同一主题并排）"
    override val m3ButtonsLabel = "按钮：官方 Button / FilledTonal / Outlined / Text"
    override val m3InputsLabel = "输入框：官方 OutlinedTextField（含粘贴自动清洗的数值框）"
    override val m3SelectionLabel = "选择：SegmentedButton / Switch / Checkbox / FilterChip"
    override val m3ContainersLabel = "容器：Card / ListItem"
    override val m3FeedbackLabel = "反馈：Tooltip / Snackbar / 进度指示"
    override val m3DateTimeLabel = "日期与时间：官方 DatePicker / TimePicker（直接内联，不用其 Dialog 壳）"
    override val m3NumericDemoLabel = "粘贴 1,234.56、带空格或全角数字都会被自动清洗为规范值（DEF-54 目标形态）"
    override val m3NumericDemoField = "价格"
    override val m3SampleItem = "示例列表项"
    override val m3TooltipSample = "这是一条工具提示"
    override val m3Confirm = "确认"
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
    override val m3Section = "Material 3 framework area (ADR-007: official vs hand-rolled, same theme, side by side)"
    override val m3ButtonsLabel = "Buttons: official Button / FilledTonal / Outlined / Text"
    override val m3InputsLabel = "Text fields: official OutlinedTextField (incl. paste-sanitised numeric field)"
    override val m3SelectionLabel = "Selection: SegmentedButton / Switch / Checkbox / FilterChip"
    override val m3ContainersLabel = "Containers: Card / ListItem"
    override val m3FeedbackLabel = "Feedback: Tooltip / Snackbar / progress indicators"
    override val m3DateTimeLabel = "Date & time: official DatePicker / TimePicker inlined (no Dialog shell)"
    override val m3NumericDemoLabel = "Pasting 1,234.56, spaces or full-width digits is sanitised automatically (target of DEF-54)"
    override val m3NumericDemoField = "Price"
    override val m3SampleItem = "Sample list item"
    override val m3TooltipSample = "This is a tooltip"
    override val m3Confirm = "Confirm"
}

val galleryStrings: GalleryStrings get() = if (I18n.isZh) GalleryStringsZh else GalleryStringsEn
