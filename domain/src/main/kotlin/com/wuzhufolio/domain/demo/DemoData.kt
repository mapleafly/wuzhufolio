package com.wuzhufolio.domain.demo

/**
 * 示例数据标记（B 迭代 **B1** · 决策档 `D46` · task-breakdown **T15.3**）。
 *
 * 口径（为什么用 notes 前缀而不是新增标记位/settings 键）：
 * - 示例数据的**唯一识别依据** = `notes` 列的统一前缀 [MARKER]（`transactions.notes` /
 *   `capital_flows.notes` 两列**既有**）⇒ **零 schema 变更、零新表、零 `.cpro` 格式变更、
 *   零新增 settings 键**（硬约束 5 与 §8.1 红线 2 均不触碰）；
 * - **幂等**：已存在带标记行 ⇒ 视为「已加载」，不再重复写入；
 * - **清除范围**：只删带标记行——真实记录（无标记）**永不**被示例数据路径删除；
 * - **标记不随界面语言变化**：否则清除逻辑与历史数据识别会失效。界面上「示例数据」徽标与引导说明
 *   由 UI 层 i18n（zh/en 双档）提供，[MARKER] 本身是**数据契约**，与数据层既有中文串
 *   （如 CSV 模板备注）同口径。
 */
object DemoData {

    /** 示例数据统一标记前缀（写入两处 notes 列的**行首**）。 */
    const val MARKER: String = "[示例] "

    /** 生成带标记的备注（示例数据集的所有备注一律经此产出，保证可识别）。 */
    fun note(text: String): String = MARKER + text

    /** 判断一条备注是否为示例数据行（null / 无前缀 = 真实数据）。 */
    fun isDemoNote(notes: String?): Boolean = notes?.startsWith(MARKER) == true
}
