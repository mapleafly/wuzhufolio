package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.catalog.CatalogCoin
import com.wuzhufolio.domain.catalog.CoinCatalog
import com.wuzhufolio.domain.catalog.Resolution
import com.wuzhufolio.domain.catalog.ResolveContext
import com.wuzhufolio.domain.engine.Side
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 标准 CSV 模板解析（T7.3 · PRD 故事 2.3 / §7.2-3「CSV导入」）。
 *
 * 模板列（表头行，别名表兼容主流交易所导出的常见表头；解析一律按 UTC——PRD 时间与时区规则）：
 *   exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes
 * pair 形态 = "BASE/QUOTE"（模板自带斜杠形态；无斜杠拼接符号**不猜测切分**——PRD「币种标识与主数据
 * 规则」，报格式错误行引导改用模板）。
 *
 * 币解析：计价腿先行（无上下文），基础腿携 quote 上下文（消歧规则①）；复用 CoinCatalog.resolve
 *（含映射 AUTO 冻结），歧义/未收录上浮由预览层处置（用户选择固化 MANUAL 后重解）。
 */
@Suppress("TooManyFunctions") // 解析面（表头映射/行解析/时间/消歧注记），拆分类反而碎片化
class CsvTradeParser(private val catalog: CoinCatalog) {

    /** 解析产物（预览层输入；rowKey = "L<csv 行号>"，跨 parse/confirm 稳定）。 */
    data class ParseOutput(
        val rows: List<ParsedTrade>,
        val errors: List<com.wuzhufolio.domain.ledger.CsvRowError>,
        /** 歧义键 "EXCHANGE|ASSET" -> 候选（cgId -> 展示）。 */
        val ambiguous: Map<String, Map<String, String>>,
    )

    /** 单行解析产物（币解析三态：命中 / 歧义 / 未收录）。 */
    data class ParsedTrade(
        val rowKey: String,
        val line: Int,
        val exchange: String,
        val orderId: String?,
        val pair: String,
        val baseAsset: String,
        val quoteAsset: String,
        val side: Side,
        val price: BigDecimal,
        val quantity: BigDecimal,
        val fee: BigDecimal,
        val feeCurrency: String,
        val time: Instant,
        val notes: String?,
        /** 命中的币种（quote, base；UNRESOLVED 行为 null）。 */
        val quoteCoin: CatalogCoin?,
        val baseCoin: CatalogCoin?,
    )

    @Suppress("ReturnCount") // 空文件/缺列/无表头/正常四条出口，逐段早退更可读
    suspend fun parse(bytes: ByteArray, defaultExchange: String = DEFAULT_EXCHANGE): ParseOutput {
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val records = CsvSplitter.split(text)
        if (records.isEmpty()) {
            return ParseOutput(
                emptyList(),
                listOf(com.wuzhufolio.domain.ledger.CsvRowError(1, "文件为空或没有数据行")),
                emptyMap(),
            )
        }
        // 跳过前导注释行（# 开头）——模板以字段说明注释开头
        var headerIndex = 0
        while (headerIndex < records.size && records[headerIndex].firstOrNull()?.startsWith("#") == true) {
            headerIndex++
        }
        if (headerIndex >= records.size) {
            return ParseOutput(emptyList(), listOf(com.wuzhufolio.domain.ledger.CsvRowError(1, "未找到表头行")), emptyMap())
        }
        val header = records[headerIndex].map { it.trim().lowercase() }
        val columns = header.map { HEADER_ALIASES[it] }
        val required = listOf(Col.PAIR, Col.SIDE, Col.PRICE, Col.QUANTITY, Col.TIME)
        val missing = required.filter { c -> columns.none { it == c } }
        if (missing.isNotEmpty()) {
            val names = missing.map { it.name }.joinToString("/")
            return ParseOutput(
                emptyList(),
                listOf(com.wuzhufolio.domain.ledger.CsvRowError(1, "缺少必需列：$names（请使用标准模板表头）")),
                emptyMap(),
            )
        }
        val indexOf: (Col) -> Int = { c -> columns.indexOfFirst { it == c } }
        val iExchange = indexOf(Col.EXCHANGE)
        val iOrder = indexOf(Col.ORDER)
        val iPair = indexOf(Col.PAIR)
        val iSide = indexOf(Col.SIDE)
        val iPrice = indexOf(Col.PRICE)
        val iQty = indexOf(Col.QUANTITY)
        val iFee = indexOf(Col.FEE)
        val iFeeCur = indexOf(Col.FEE_CURRENCY)
        val iTime = indexOf(Col.TIME)
        val iNotes = indexOf(Col.NOTES)

        val rows = ArrayList<ParsedTrade>()
        val errors = ArrayList<com.wuzhufolio.domain.ledger.CsvRowError>()
        val ambiguous = LinkedHashMap<String, LinkedHashMap<String, String>>()

        for (r in (headerIndex + 1) until records.size) {
            val cells = records[r]
            val lineNo = r + 1
            if (cells.all { it.isBlank() } || cells.firstOrNull()?.startsWith("#") == true) continue
            val outcome = parseRecord(cells, lineNo, defaultExchange, indexOf)
            outcome.second?.let { errors += com.wuzhufolio.domain.ledger.CsvRowError(lineNo, it) }
            val row = outcome.first
            if (row != null) {
                rows += row
                if (row.baseCoin == null || row.quoteCoin == null) noteAmbiguousRow(ambiguous, row)
            }
        }
        return ParseOutput(rows, errors, ambiguous)
        return ParseOutput(rows, errors, ambiguous)
    }

    /** 单腿解析：Unique 命中 /（Ambiguous、NotFound）-> null（歧义进候选表，NotFound 预览层计未解析）。 */
    private suspend fun resolveLeg(exchange: String, asset: String, quote: CatalogCoin?): CatalogCoin? =
        when (val res = catalog.resolve(exchange, asset, ResolveContext(quoteCoinId = quote?.id))) {
            is Resolution.Unique -> res.coin
            else -> null
        }

    private suspend fun noteAmbiguous(
        ambiguous: MutableMap<String, LinkedHashMap<String, String>>,
        exchange: String,
        asset: String,
    ) {
        val key = "$exchange|$asset"
        if (ambiguous.containsKey(key)) return
        val candidates = when (val res = catalog.resolve(exchange, asset)) {
            is Resolution.Ambiguous -> res.candidates.associate { it.cgId to (it.symbol + " · " + it.name) }
            else -> emptyMap()
        }
        if (candidates.isNotEmpty()) ambiguous[key] = LinkedHashMap(candidates)
    }

    /**
     * 单行解析（行级校验 V1/V2、pair 切分、币解析三态）。返回 (行, 格式错误原因)；错误原因非空时行丢弃。
     * 抽离自 parse 主循环以控制圈复杂度（detekt 阈值 15）。
     */
    @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
    // 行校验分支（价格/数量/类型/时间/pair/币腿）固有，拆分到字段级反而碎
    private suspend fun parseRecord(
        cells: List<String>,
        lineNo: Int,
        defaultExchange: String,
        indexOf: (Col) -> Int,
    ): Pair<ParsedTrade?, String?> {
        fun cell(i: Int): String = if (i in cells.indices) cells[i].trim() else ""
        val iExchange = indexOf(Col.EXCHANGE)
        val iOrder = indexOf(Col.ORDER)
        val iPair = indexOf(Col.PAIR)
        val iSide = indexOf(Col.SIDE)
        val iPrice = indexOf(Col.PRICE)
        val iQty = indexOf(Col.QUANTITY)
        val iFee = indexOf(Col.FEE)
        val iFeeCur = indexOf(Col.FEE_CURRENCY)
        val iTime = indexOf(Col.TIME)
        val iNotes = indexOf(Col.NOTES)
        val pairRaw = cell(iPair).uppercase().replace(" ", "")
        val slash = pairRaw.indexOf('/')
        if (slash <= 0 || slash == pairRaw.length - 1) {
            return Pair(null, "交易对「$pairRaw」无法解析（需 BASE/QUOTE 形态，拼接符号不猜测切分）")
        }
        val baseAsset = pairRaw.substring(0, slash)
        val quoteAsset = pairRaw.substring(slash + 1)
        val side = when (cell(iSide).uppercase()) {
            "BUY", "买入" -> Side.BUY
            "SELL", "卖出" -> Side.SELL
            else -> return Pair(null, "类型「${cell(iSide)}」无法识别（买入/卖出）")
        }
        val price = runCatching { BigDecimal(cell(iPrice)) }.getOrNull()
            ?: return Pair(null, "数字格式错误：价格")
        val quantity = runCatching { BigDecimal(cell(iQty)) }.getOrNull()
            ?: return Pair(null, "数字格式错误：数量")
        if (price.signum() <= 0 || quantity.signum() <= 0) {
            return Pair(null, "价格/数量必须为正数（V1）")
        }
        val fee = cell(iFee).takeIf { it.isNotEmpty() }
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: BigDecimal.ZERO
        if (fee.signum() < 0) return Pair(null, "手续费必须 ≥ 0（V2）")
        val time = parseTime(cell(iTime)) ?: return Pair(
            null,
            "时间「${cell(iTime)}」无法解析（支持 ISO-8601、yyyy-MM-dd[ HH:mm[:ss]]、yyyy/MM/dd、" +
                "中文日期、epoch 秒/毫秒、Excel 日期序列；一律按 UTC 解析）",
        )
        val exchange = cell(iExchange).ifBlank { defaultExchange }.uppercase()
        val feeCurrency = cell(iFeeCur).uppercase().ifBlank { quoteAsset }
        val quoteCoin = resolveLeg(exchange, quoteAsset, null)
        val baseCoin = resolveLeg(exchange, baseAsset, quoteCoin)
        return Pair(
            ParsedTrade(
                rowKey = "L$lineNo",
                line = lineNo,
                exchange = exchange,
                orderId = cell(iOrder).takeIf { it.isNotEmpty() },
                pair = pairRaw,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                side = side,
                price = price,
                quantity = quantity,
                fee = fee,
                feeCurrency = feeCurrency,
                time = time,
                notes = cell(iNotes).takeIf { it.isNotEmpty() },
                quoteCoin = quoteCoin,
                baseCoin = baseCoin,
            ),
            null,
        )
    }

    /** 歧义腿注记（候选表去重后写入；NotFound 无候选不注记）。 */
    private suspend fun noteAmbiguousRow(
        ambiguous: MutableMap<String, LinkedHashMap<String, String>>,
        row: ParsedTrade,
    ) {
        val legs = listOf(row.quoteAsset to row.quoteCoin, row.baseAsset to row.baseCoin)
        for ((asset, coin) in legs) {
            if (coin != null) continue
            noteAmbiguous(ambiguous, row.exchange, asset)
        }
    }

    /**
     * 时间解析（一律 UTC）。容错口径（2026-09-08 修复轮：模板经 Excel/表格工具改写日期后导入报格式错误）：
     * ① epoch 秒/毫秒（10/13 位数字）；② ISO-8601（含 Z/偏移）；③ 宽松日期时间——分隔符 - / .、
     * 月/日/时可为 1 位、秒与小数秒可选、可带 Z/±HH:mm 偏移（如 2026/1/10 8:00）；
     * ④ 中文日期（2026年1月10日 08:00）；⑤ 紧凑 yyyyMMdd；⑥ Excel 日期序列（含小数时间，1900 基准）。
     */
    @Suppress("ReturnCount") // 多格式逐级尝试，命中即返回
    internal fun parseTime(raw: String): Instant? {
        val v = raw.trim().trim('"')
        if (v.isEmpty()) return null
        epochOf(v)?.let { return it }
        runCatching { Instant.parse(v) }.getOrNull()?.let { return it }
        val normalized = v.replace("年", "-").replace("月", "-").replace("日", " ")
            .replace(Regex("\\s+"), " ").trim()
        regexParse(normalized)?.let { return it }
        compactDate(v)?.let { return it }
        excelSerial(v)?.let { return it }
        return null
    }

    /** epoch 秒（10 位）/ 毫秒（13 位）。 */
    @Suppress("ReturnCount")
    private fun epochOf(v: String): Instant? {
        if (!v.matches(Regex("\\d{10,13}"))) return null
        val n = v.toLongOrNull() ?: return null
        return if (v.length <= 10) Instant.ofEpochSecond(n) else Instant.ofEpochMilli(n)
    }

    private val dateTimePattern = Regex(
        "^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})" +
            "(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\.(\\d{1,9}))?)?" +
            "\\s*(Z|[+-]\\d{2}:?\\d{2})?$",
    )

    /** 宽松日期时间解析（月/日/时 1-2 位、秒可选、可带偏移）。 */
    @Suppress("ReturnCount")
    private fun regexParse(v: String): Instant? {
        val m = dateTimePattern.matchEntire(v) ?: return null
        val date = runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull() ?: return null
        val hour = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val minute = m.groupValues[5].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val second = m.groupValues[6].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val nanos = m.groupValues[7].takeIf { it.isNotEmpty() }?.take(9)?.padEnd(9, '0')?.toInt() ?: 0
        val time = runCatching { LocalTime.of(hour, minute, second, nanos) }.getOrNull() ?: return null
        val offset = when (val zone = m.groupValues[8]) {
            "", "Z" -> ZoneOffset.UTC
            else -> runCatching { ZoneOffset.of(zone) }.getOrNull() ?: return null
        }
        return date.atTime(time).toInstant(offset)
    }

    /** 紧凑日期 yyyyMMdd。 */
    @Suppress("ReturnCount")
    private fun compactDate(v: String): Instant? {
        if (!v.matches(Regex("\\d{8}"))) return null
        val date = runCatching {
            LocalDate.of(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt())
        }.getOrNull() ?: return null
        return date.atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    /** Excel 日期序列（1900 基准；整数 = 日期，小数 = 时刻；限定 1954–2064 避免误判普通数字）。 */
    @Suppress("ReturnCount")
    private fun excelSerial(v: String): Instant? {
        val d = v.toDoubleOrNull() ?: return null
        if (d < 20000 || d > 60000) return null
        val days = d.toLong()
        val nanos = ((d - days) * 86_400_000_000_000L).toLong()
        return LocalDate.ofEpochDay(days - 25569).atStartOfDay().plusNanos(nanos).toInstant(ZoneOffset.UTC)
    }

    private enum class Col { EXCHANGE, ORDER, PAIR, SIDE, PRICE, QUANTITY, FEE, FEE_CURRENCY, TIME, NOTES }

    companion object {
        const val DEFAULT_EXCHANGE = "BINANCE"

        /** 表头别名表（键 = 小写表头；兼容常见交易所导出与标准模板）。 */
        private val HEADER_ALIASES: Map<String, Col?> = mapOf(
            "exchange" to Col.EXCHANGE,
            "platform" to Col.EXCHANGE,
            "交易所" to Col.EXCHANGE,
            "order_id" to Col.ORDER,
            "orderid" to Col.ORDER,
            "order id" to Col.ORDER,
            "订单号" to Col.ORDER,
            "pair" to Col.PAIR,
            "symbol" to Col.PAIR,
            "market" to Col.PAIR,
            "交易对" to Col.PAIR,
            "交易市场" to Col.PAIR,
            "side" to Col.SIDE,
            "type" to Col.SIDE,
            "类型" to Col.SIDE,
            "price" to Col.PRICE,
            "价格" to Col.PRICE,
            "quantity" to Col.QUANTITY,
            "amount" to Col.QUANTITY,
            "qty" to Col.QUANTITY,
            "数量" to Col.QUANTITY,
            "fee" to Col.FEE,
            "手续费" to Col.FEE,
            "fee_coin" to Col.FEE_CURRENCY,
            "fee coin" to Col.FEE_CURRENCY,
            "fee_currency" to Col.FEE_CURRENCY,
            "feecoin" to Col.FEE_CURRENCY,
            "手续费币种" to Col.FEE_CURRENCY,
            "time" to Col.TIME,
            "date" to Col.TIME,
            "date_utc" to Col.TIME,
            "date(utc)" to Col.TIME,
            "交易时间" to Col.TIME,
            "notes" to Col.NOTES,
            "comment" to Col.NOTES,
            "备注" to Col.NOTES,
        )
    }
}

/** RFC4180 精简拆分器：引号字段内支持逗号/换行/双写引号转义。 */
internal object CsvSplitter {

    @Suppress("CyclomaticComplexMethod") // RFC4180 状态机分支（引号/逗号/换行/转义）固有
    fun split(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var hasCell = false
        fun endCell() {
            cells.add(sb.toString())
            sb.setLength(0)
            hasCell = true
        }
        fun endRecord() {
            endCell()
            if (hasCell) {
                records.add(cells.toList())
                cells.clear()
                hasCell = false
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' && sb.isEmpty() -> inQuotes = true
                c == ',' -> endCell()
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRecord()
                }
                c == '\n' -> endRecord()
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return records
    }
}
