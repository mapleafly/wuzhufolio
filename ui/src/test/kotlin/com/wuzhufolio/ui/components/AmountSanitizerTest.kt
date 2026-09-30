package com.wuzhufolio.ui.components

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DEF-54 修法回归：金额/数量文本的规范化与解析（`AmountSanitizer`）。
 *
 * 覆盖三类真实来源：**CSV 粘贴**（尾随空格 / NBSP / 千分位）、**中文输入法全角**、**网页复制**（货币符号 / Unicode 减号），
 * 以及**必须拒绝**的歧义格式（欧陆小数逗号）——后者若被"猜"，会产生静默 100 倍误差。
 */
class AmountSanitizerTest {

    @Test
    fun `CSV 粘贴的尾随空格与各类不间断空格被清除`() {
        assertEquals("1234.56", AmountSanitizer.sanitize("1234.56 "))
        assertEquals("1234.56", AmountSanitizer.sanitize(" 1234.56"))
        assertEquals("1234.56", AmountSanitizer.sanitize("1234.56\u00A0")) // NBSP
        assertEquals("1234.56", AmountSanitizer.sanitize("1234.56\u202F")) // narrow NBSP
        assertEquals("1234.56", AmountSanitizer.sanitize("1 234.56")) // 千分位空格（法语/部分导出）
        assertEquals("0.1", AmountSanitizer.sanitize("0.1\t"))
    }

    @Test
    fun `全角数字与全角句点按 NFKC 归一`() {
        assertEquals("1234.56", AmountSanitizer.sanitize("１２３４．５６"))
        assertEquals("1234.56", AmountSanitizer.sanitize("１２３４.５６ "))
        assertEquals("123456", AmountSanitizer.sanitize("1234\u300056")) // 全角空格（U+3000）→ 删除
    }

    @Test
    fun `货币符号与 Unicode 减号被归一`() {
        assertEquals("1234.56", AmountSanitizer.sanitize("¥1234.56"))
        assertEquals("$1234.56".removePrefix("$"), AmountSanitizer.sanitize("$1234.56").removePrefix("$"))
        assertEquals("-1234.56", AmountSanitizer.sanitize("\u22121234.56")) // U+2212 减号
        assertEquals("-1234.56", AmountSanitizer.sanitize("—1234.56")) // em dash 误用
    }

    @Test
    fun `合法格式解析成功（含千分位）`() {
        assertEquals(BigDecimal("1234.56"), AmountSanitizer.parseAmountOrNull("1234.56"))
        assertEquals(BigDecimal("1234.56"), AmountSanitizer.parseAmountOrNull("1,234.56"))
        assertEquals(BigDecimal("1234.56"), AmountSanitizer.parseAmountOrNull(" 1,234.56 \u00A0"))
        assertEquals(BigDecimal("12345678"), AmountSanitizer.parseAmountOrNull("12,345,678"))
        assertEquals(BigDecimal("1234"), AmountSanitizer.parseAmountOrNull("1,234"))
        assertEquals(BigDecimal("-0.5"), AmountSanitizer.parseAmountOrNull("-0.5"))
        assertEquals(BigDecimal("50000"), AmountSanitizer.parseAmountOrNull("５００００"))
    }

    @Test
    fun `欧陆小数逗号与其它歧义格式一律拒绝（不猜）`() {
        // 1.234,56 = 1234.56（欧陆）——与本产品规范输入口径（小数点 = .）冲突 → 必须拒绝
        assertNull(AmountSanitizer.parseAmountOrNull("1.234,56"))
        assertTrue(AmountSanitizer.isAmbiguous("1.234,56"))
        // 单个逗号但分组长度不是 3 → 无法判定是千分位还是小数点
        assertNull(AmountSanitizer.parseAmountOrNull("1,5"))
        assertNull(AmountSanitizer.parseAmountOrNull("12,34"))
        assertTrue(AmountSanitizer.isAmbiguous("12,34"))
        // 多个小数点 → 非法
        assertNull(AmountSanitizer.parseAmountOrNull("1.2.3"))
        assertFalse(AmountSanitizer.isAmbiguous("1.2.3"))
        // 非数字字符 → 非法
        assertNull(AmountSanitizer.parseAmountOrNull("abc"))
        assertNull(AmountSanitizer.parseAmountOrNull(""))
        assertNull(AmountSanitizer.parseAmountOrNull("   "))
    }

    @Test
    fun `千分位逗号解析后与无分隔写法等价`() {
        assertEquals(
            AmountSanitizer.parseAmountOrNull("1234.56"),
            AmountSanitizer.parseAmountOrNull("1,234.56"),
        )
        assertEquals(
            AmountSanitizer.parseAmountOrNull("12345678"),
            AmountSanitizer.parseAmountOrNull("12,345,678"),
        )
    }
}
