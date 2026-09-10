package com.wuzhufolio.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * 数值/时间格式化单测（M12 T12.4 · PRD §6「数据精度」+「时间与时区规则」）。
 * 覆盖：默认精度（8/2/2 与 2/2/2）、微小价格自适应、盈亏强制 +/-、无数据 "--"、
 * 日期时间格式随语言（时区恒为系统本地）。
 */
class WzFormatTest {

    private fun zh(): Unit {
        I18n.set(com.wuzhufolio.domain.settings.AppLanguage.ZH)
        WzFormat.precision = com.wuzhufolio.domain.settings.PrecisionPreset.DEFAULT
    }

    private fun en(): Unit {
        I18n.set(com.wuzhufolio.domain.settings.AppLanguage.EN)
        WzFormat.precision = com.wuzhufolio.domain.settings.PrecisionPreset.DEFAULT
    }

    @Test
    fun `price keeps default eight decimals and trims trailing zeros`() {
        zh()
        assertEquals("50,050", WzFormat.price(BigDecimal("50050.0000000000")))
        assertEquals("0.12345678", WzFormat.price(BigDecimal("0.1234567800")))
    }

    @Test
    fun `simplified preset narrows prices to two decimals`() {
        zh()
        WzFormat.precision = com.wuzhufolio.domain.settings.PrecisionPreset.SIMPLIFIED
        assertEquals("50,050.13", WzFormat.price(BigDecimal("50050.126")))
        assertEquals("0.12", WzFormat.price(BigDecimal("0.12345678")))
    }

    @Test
    fun `tiny price auto-increases significant digits instead of showing zero`() {
        zh()
        // PRD：单价有效数字不足 8 位时自动增加有效数字位，避免显示为 0
        // 8 位小数下 1.2345e-8 会显示为 0.00000001 → 提升到 12 位有效（上限）后可见 6 位有效数字
        assertEquals("0.000000012345", WzFormat.price(BigDecimal("0.000000012345")))
        // 常规价格不受影响
        assertEquals("0.12345678", WzFormat.price(BigDecimal("0.12345678")))
    }

    @Test
    fun `amounts use two decimals with grouping`() {
        zh()
        assertEquals("120,464.77", WzFormat.amount(BigDecimal("120464.765")))
    }

    @Test
    fun `pnl values always carry an explicit sign`() {
        zh()
        assertEquals("+4,915.00", WzFormat.signedAmount(BigDecimal("4915")))
        assertEquals("-1,200.50", WzFormat.signedAmount(BigDecimal("-1200.5")))
        assertEquals("+31.26%", WzFormat.signedPercent(BigDecimal("31.255")))
        assertEquals("-8.00%", WzFormat.signedPercent(BigDecimal("-8")))
    }

    @Test
    fun `missing values render as dash`() {
        zh()
        assertEquals("--", WzFormat.price(null))
        assertEquals("--", WzFormat.amount(null))
        assertEquals("--", WzFormat.signedAmount(null))
        assertEquals("--", WzFormat.signedPercent(null))
        assertEquals("--", WzFormat.dateTime(null))
    }

    @Test
    fun `money prefixes the fiat currency`() {
        zh()
        assertEquals("$120,464.77", WzFormat.money(BigDecimal("120464.77"), "USD"))
        assertEquals("+$1.00", WzFormat.signedMoney(BigDecimal.ONE, "USD"))
        assertEquals("EUR 10.00", WzFormat.money(BigDecimal.TEN, "EUR"))
    }

    @Test
    fun `date time format follows the language while the zone stays local`() {
        val at = Instant.parse("2026-09-10T07:24:00Z")
        val local = at.atZone(ZoneId.systemDefault())
        zh()
        assertEquals(
            "%04d-%02d-%02d %02d:%02d".format(local.year, local.monthValue, local.dayOfMonth, local.hour, local.minute),
            WzFormat.dateTime(at),
        )
        en()
        assertTrue(
            WzFormat.dateTime(at).startsWith(EN_MONTHS[local.monthValue - 1]),
            "English format should use the month name, was " + WzFormat.dateTime(at),
        )
        assertTrue(WzFormat.dateTime(at).contains(local.year.toString()))
    }

    @Test
    fun `form date time is stable and parseable`() {
        val at = Instant.parse("2026-09-10T07:24:00Z")
        assertTrue(WzFormat.formDateTime(at).contains("T"))
    }

    private companion object {
        val EN_MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
    }
}
