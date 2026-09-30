package com.wuzhufolio.ui.components

import java.math.BigDecimal
import java.text.Normalizer

/**
 * 金额/数量文本的**规范化与解析**（DEF-54 的修法 · ADR-007 §2.5）。
 *
 * **问题**：从 CSV/网页复制过来的数字常带**尾随空格、NBSP、窄 NBSP、全角数字、千分位逗号、货币符号**，
 * 直接 `BigDecimal(str)` 会抛 `NumberFormatException`；改造前 `toBigDecimalOrNull()` 对脏串返回 `null`，
 * 表现为「总价算不出来 + 报『价格必须大于 0』」（0.1.1 人工反馈第 1 条）。
 *
 * **规范输入口径（必须唯一，否则会静默出错）**：小数点 = `.`，千分位 = `,`。
 * 之所以要显式定义：`1,234.56`（en-US）与 `1.234,56`（de-DE）**语义相反**，
 * 「把逗号一律当千分位删掉」在欧陆格式下会把小数点删掉 → **静默 100 倍误差**。
 * 因此本实现采取**拒绝歧义**策略：[parseAmountOrNull] 对无法确定语义的串返回 `null`，
 * 由表单给出明确错误文案，而不是猜一个数。
 *
 * 本对象是**纯函数**（无 Compose 依赖），因此可被 ViewModel 与输入框共同使用，并被单测直接覆盖。
 */
object AmountSanitizer {

    /** 各类空白：普通空格、制表、NBSP、窄 NBSP、全角空格等（CSV 粘贴的重灾区）。 */
    private val WHITESPACE = Regex("[\\s\\u00A0\\u202F\\u2007\\u2009\\u3000]+")

    /** 常见货币符号（前缀/后缀均直接去掉）。 */
    private val CURRENCY = Regex("[¥￥$€£₩₽₹]")

    /** Unicode 减号/破折号（复制自网页时常见）→ ASCII 减号。 */
    private val UNICODE_MINUS = Regex("[\\u2212\\u2013\\u2014]")

    /** 允许出现的字符（清洗后仍含其它字符即视为非法）。 */
    private val ALLOWED = Regex("^[+-]?[0-9.,]*$")

    /**
     * 温和清洗（**用于输入框实时清洗**）：NFKC 全角→半角、去各类空白、去货币符号、统一减号。
     * 不改动 `,` 与 `.`（分隔符语义交给 [parseAmountOrNull] 判定，避免用户输入中途被强行改写）。
     */
    fun sanitize(raw: CharSequence): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        return normalized
            .replace(WHITESPACE, "")
            .replace(CURRENCY, "")
            .replace(UNICODE_MINUS, "-")
    }

    /**
     * 规范化 + 解析为 [BigDecimal]；非法或**语义歧义**时返回 `null`。
     *
     * 分隔符规则：
     * 1. 同时含 `,` 与 `.`：**最后出现的那个是小数点**；若 `.` 在 `,` 之后 → `,` 视为千分位删除
     *    （`1,234.56` → `1234.56`）；反之（`1.234,56`）→ **判为歧义，返回 `null`**。
     * 2. 只含 `.`：必须**最多一个**，多于一个判为非法（`1.2.3` → `null`）。
     * 3. 只含 `,`：仅在**每一段逗号分组长度均为 3** 时视为千分位并删除（`1,234` → `1234`，
     *    `12,345,678` → `12345678`）；否则判为歧义（`1,5` / `12,34` → `null`）。
     */
    fun parseAmountOrNull(raw: String): BigDecimal? {
        val text = sanitize(raw)
        // 空串 / 含非法字符 → 不进入分隔符判定；任一步失败即整体返回 null（不做猜测）
        val digits = text.takeIf { it.isNotEmpty() && ALLOWED.matches(it) }?.let(::stripSeparators)
        return digits?.toBigDecimalOrNull()
    }

    /**
     * 是否为「分隔符语义**歧义**」（供表单给出针对性错误文案，而不是笼统的「格式错误」）。
     *
     * 只覆盖两种「两种合理读法冲突」的情形：① 同时含 `,` 与 `.` 且 `.` 在前面（欧陆格式）；
     * ② 只含 `,` 但分组长度不是 3。**多小数点、非数字字符属「非法」而非歧义**（返回 `false`）。
     */
    fun isAmbiguous(raw: String): Boolean {
        val text = sanitize(raw)
        if (text.isEmpty() || !ALLOWED.matches(text)) return false
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')
        return when {
            lastComma >= 0 && lastDot >= 0 -> lastDot < lastComma
            lastComma >= 0 -> {
                val groups = text.split(',')
                val head = groups.first().removePrefix("+").removePrefix("-")
                !(head.isNotEmpty() && head.length in 1..3 && groups.drop(1).all { it.length == 3 })
            }
            else -> false
        }
    }

    /**
     * 按上述规则去掉千分位分隔符，保留小数点与符号；**歧义/非法返回 `null`**。
     */
    private fun stripSeparators(text: String): String? {
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')
        return when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastDot < lastComma) {
                    null // 1.234,56 = 欧陆格式 → 与本产品规范输入口径冲突，拒绝而非猜测
                } else {
                    text.replace(",", "")
                }
            }
            lastDot >= 0 -> if (text.count { it == '.' } > 1) null else text
            lastComma >= 0 -> {
                val groups = text.split(',')
                val head = groups.first().removePrefix("+").removePrefix("-")
                val isGrouped = head.isNotEmpty() && head.length in 1..3 &&
                    groups.drop(1).all { it.length == 3 }
                if (isGrouped) text.replace(",", "") else null
            }
            else -> text
        }
    }
}
