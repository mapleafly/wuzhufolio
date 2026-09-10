package com.wuzhufolio.ui.i18n

import com.wuzhufolio.domain.settings.PrecisionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 数值与时间的统一格式化（M12 T12.4 · PRD §6「数据精度」+「时间与时区规则」+ interaction.md §3-3/3-4）。
 *
 * 口径（全部为**展示层**规则，不改存储/不改领域计算）：
 * - 金额/价格默认保留 8 位小数，市值/盈亏/百分比默认 2 位；精度档由 [precision] 承载
 *   （设置 → 通用「默认精度」，[PrecisionPreset.DEFAULT] = 8/2/2、[PrecisionPreset.SIMPLIFIED] = 2/2/2）；
 * - **微小价格自适应**（PRD：单价有效数字不足 8 位时自动增加有效数字位，避免显示为 0）：
 *   [price] 在 8 位小数下会被截断为 0 时，逐位增加有效数字直到出现非零有效数字（上限 [MAX_SIG_DIGITS]）；
 * - 盈亏/涨跌数值**强制显示 +/- 符号**（PRD 无障碍基线：颜色仅作辅助语义）——[signed] / [percent]；
 * - 无数据一律 "--"（[DASH]；interaction.md §2.3）；
 * - 时间：**时区恒为系统本地**（跨端共享规范 §4「界面显示统一转换为系统本地时区」），
 *   **格式随语言**（同条「日期时间格式随 I18N 语言设置」）——中文 `2026-09-10 15:24`、英文 `Sep 10, 2026 15:24`；
 * - 分组分隔与小数点：zh/en 同形（`,` / `.`），以 [Locale.ROOT] 恒定输出，避免运行环境默认 Locale 漂移。
 */
object WzFormat {

    /** 无数据占位（PRD 列表默认数据规则）。 */
    const val DASH: String = "--"

    /** 微小价格自适应上限（有效数字位数；8 位小数后最多再补到 12 位有效，防极端小币显示为 0）。 */
    const val MAX_SIG_DIGITS: Int = 12

    private val _precision = MutableStateFlow(PrecisionPreset.DEFAULT)

    /** 精度档（WuzhuTheme 订阅以在切换后强制重组；见 theme/Theme.kt）。 */
    val precisionFlow: StateFlow<PrecisionPreset> = _precision.asStateFlow()

    /** 当前精度档（启动期与设置页写入）。 */
    var precision: PrecisionPreset
        get() = _precision.value
        set(value) {
            _precision.value = value
        }

    /** 价格小数位（精度档第一段）。 */
    val priceDecimals: Int get() = if (precision == PrecisionPreset.SIMPLIFIED) 2 else 8

    /** 金额/市值/盈亏/百分比小数位（精度档后两段，本产品固定同值）。 */
    val amountDecimals: Int get() = 2

    // ---------- 数值 ----------

    /**
     * 币种单价（PRD「微小价格精度自适应」）。null → "--"。
     * 8 位小数下非零但会显示为 0 的极小价格，自动增加有效数字位。
     */
    fun price(value: BigDecimal?): String = when {
        value == null -> DASH
        // 微小价格：当前精度档下有效数字不足（PRD「单价有效数字不足 8 位自动增加有效数字位」）
        // → 提升到「前导零 + 精度档位数」位小数（上限 MAX_SIG_DIGITS），避免显示为 0 或信息丢失
        isTiny(value) -> trimNumber(value, significantDigits(value))
        else -> trimNumber(value, priceDecimals)
    }

    /** 是否属于「微小价格」：绝对值 < 1 且在精度档下有效数字少于精度档位数。 */
    private fun isTiny(value: BigDecimal): Boolean =
        value.signum() != 0 &&
            value.abs() < BigDecimal.ONE &&
            value.setScale(priceDecimals, RoundingMode.HALF_UP).precision() < priceDecimals

    /** 金额（基础法币等，固定 2 位；PRD 市值/盈亏 2 位口径）。null → "--"。 */
    fun amount(value: BigDecimal?): String = if (value == null) DASH else fixed(value, amountDecimals)

    /** 数量（持仓/成交数量；保留至精度上限并去掉尾零，避免 1.50000000 一类的冗余显示）。 */
    fun quantity(value: BigDecimal?): String = if (value == null) DASH else trimNumber(value, priceDecimals)

    /** 百分比（2 位 + "%"；输入为百分数值本身，如 31.26 → "31.26%"）。null → "--"。 */
    fun percent(value: BigDecimal?): String = if (value == null) DASH else fixed(value, amountDecimals) + "%"

    /** 带符号百分比（盈亏率；强制 +/-，PRD 无障碍基线）。null → "--"。 */
    fun signedPercent(value: BigDecimal?): String =
        if (value == null) DASH else sign(value) + fixed(value.abs(), amountDecimals) + "%"

    /** 带符号金额（盈亏；强制 +/-）。null → "--"。 */
    fun signedAmount(value: BigDecimal?): String =
        if (value == null) DASH else sign(value) + fixed(value.abs(), amountDecimals)

    /** 带符号数量（校准差额等）。null → "--"。 */
    fun signedQuantity(value: BigDecimal?): String =
        if (value == null) DASH else sign(value) + trimNumber(value.abs(), priceDecimals)

    /** 法币金额前缀展示（列表内联金额，如 "$120,464.77" / "--"）。 */
    fun money(value: BigDecimal?, fiat: String): String =
        if (value == null) DASH else fiatSymbol(fiat) + fixed(value, amountDecimals)

    /** 带符号法币金额（盈亏卡片，如 "+$1,234.00"）。 */
    fun signedMoney(value: BigDecimal?, fiat: String): String =
        if (value == null) DASH else sign(value) + fiatSymbol(fiat) + fixed(value.abs(), amountDecimals)

    /** 法币符号（原型口径：USD → "$"，其余用 ISO 代码前缀，避免猜错符号）。 */
    fun fiatSymbol(fiat: String): String = if (fiat.equals("USD", ignoreCase = true)) "$" else fiat.uppercase() + " "

    // ---------- 时间（UTC 存 / 本地显，格式随语言） ----------

    private val dateTimeZh: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    private val dateZh: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val timeZh: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateTimeEn: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)
    private val dateEn: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val timeEn: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** 本地时区日期时间。null → "--"。 */
    fun dateTime(at: Instant?): String =
        at?.atZone(ZoneId.systemDefault())?.format(pick(dateTimeZh, dateTimeEn)) ?: DASH

    /** 本地时区日期。null → "--"。 */
    fun date(at: Instant?): String =
        at?.atZone(ZoneId.systemDefault())?.format(pick(dateZh, dateEn)) ?: DASH

    /** 本地时区时刻。null → "--"。 */
    fun time(at: Instant?): String = at?.atZone(ZoneId.systemDefault())?.format(pick(timeZh, timeEn)) ?: DASH

    /** 表单/输入态的本地时间串（固定 ISO-like，便于解析；不随语言变化）。 */
    fun formDateTime(at: Instant): String =
        at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT))

    // ---------- 内部 ----------

    private fun <T> pick(zh: T, en: T): T = if (I18n.isZh) zh else en

    private fun sign(value: BigDecimal): String = if (value.signum() < 0) "-" else "+"

    private fun fixed(value: BigDecimal, decimals: Int): String =
        grouped(value.setScale(decimals, RoundingMode.HALF_UP))

    /** 保留至 [maxDecimals] 位并去除尾零（整数部分照常分组）。 */
    private fun trimNumber(value: BigDecimal, maxDecimals: Int): String {
        val scaled = value.setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val normalized = if (scaled.scale() < 0) scaled.setScale(0) else scaled
        return grouped(normalized)
    }

    /** 千分位分组（恒定 ROOT Locale；负数保留符号）。 */
    private fun grouped(value: BigDecimal): String {
        val plain = value.toPlainString()
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val fracPart = if (dot >= 0) body.substring(dot) else ""
        val groupedInt = intPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + groupedInt + fracPart
    }

    /**
     * 微小价格所需的小数位 = 前导零个数 + 精度档位数（PRD「有效数字不足 8 位时自动增加有效数字位」），
     * 上限 [MAX_SIG_DIGITS]。
     */
    private fun significantDigits(value: BigDecimal): Int {
        var leadingZeros = 0
        var probe = value.abs()
        while (probe < BigDecimal.ONE && leadingZeros < MAX_SIG_DIGITS) {
            probe = probe.movePointRight(1)
            leadingZeros++
        }
        return minOf(leadingZeros + priceDecimals, MAX_SIG_DIGITS)
    }
}
