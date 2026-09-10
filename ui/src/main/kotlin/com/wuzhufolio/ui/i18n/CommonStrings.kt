package com.wuzhufolio.ui.i18n

/**
 * 跨模块公共文案（按钮/通用标签；M12 T12.4 zh/en 双档）。
 *
 * 目录约定（全项目一致，新模块照此办理）：
 * 1. 每个模块一个 `XxxStrings` 接口 + `XxxStringsZh` / `XxxStringsEn` 两个实现 +
 *    一个 `xxxStrings` 访问器（`val xxxStrings: XxxStrings get() = if (I18n.isZh) ... else ...`）；
 * 2. 既有 `object XxxCopy` 常量一律改写为**动态取值属性**（`val X: String get() = xxxStrings.x`），
 *    调用点不变——这是本模块能不动 700+ 调用点完成全量 i18n 的关键；
 * 3. 带参数的文案写成接口函数（`fun imported(n: Int): String`），不要用 `String.format` 拼装；
 * 4. **禁止**在调用点写死中文字面量（含 Toast/错误上浮文案）；数值/时间一律走 [WzFormat]。
 */
interface CommonStrings {
    val close: String
    val confirm: String
    val cancel: String
    val save: String
    val edit: String
    val delete: String
    val search: String
    val loading: String
    val emptyRecords: String
    val refresh: String
    val ok: String
    val remove: String
    val add: String
    val retry: String
}

object CommonStringsZh : CommonStrings {
    override val close = "关闭"
    override val confirm = "确认"
    override val cancel = "取消"
    override val save = "保存"
    override val edit = "编辑"
    override val delete = "删除"
    override val search = "搜索"
    override val loading = "加载中…"
    override val emptyRecords = "暂无记录"
    override val refresh = "刷新"
    override val ok = "确定"
    override val remove = "移除"
    override val add = "添加"
    override val retry = "重试"
}

object CommonStringsEn : CommonStrings {
    override val close = "Close"
    override val confirm = "Confirm"
    override val cancel = "Cancel"
    override val save = "Save"
    override val edit = "Edit"
    override val delete = "Delete"
    override val search = "Search"
    override val loading = "Loading…"
    override val emptyRecords = "No records"
    override val refresh = "Refresh"
    override val ok = "OK"
    override val remove = "Remove"
    override val add = "Add"
    override val retry = "Retry"
}

val commonStrings: CommonStrings get() = if (I18n.isZh) CommonStringsZh else CommonStringsEn
