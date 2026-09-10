package com.wuzhufolio.data.backup

/**
 * CSV 明文导出（M9 · T9.3 · PRD §9.9「数据主权」：交易记录 / 资金流水 / 持仓汇总，不含 API 密钥）。
 * RFC 4180 转义（含逗号/引号/换行的字段加引号、引号翻倍）；UTF-8 无 BOM；
 * 金额十进制原串、时间 UTC ISO-8601（与导入模板同口径，导出文件可直接回导）。
 */
internal object CsvExportWriter {

    /** 触发引号包裹的字符：逗号 / 引号 / 换行（RFC 4180）。 */
    private val NEEDS_QUOTES = charArrayOf(',', '"', '\n', '\r')

    /** 单字段转义。 */
    fun field(value: String?): String {
        val s = value ?: ""
        return if (s.indexOfAny(NEEDS_QUOTES) >= 0) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }

    /** 一行 CSV（字段依次转义拼接）。 */
    fun line(vararg fields: String?): String = fields.joinToString(",") { field(it) }

    /** 交易导出行集（表头 + 数据行；side 用中文形态与导入模板一致）。 */
    fun transactions(rows: List<BackupTxRow>): List<String> = buildList {
        add(
            line(
                "exchange", "order_id", "pair", "side", "price", "quantity",
                "fee", "fee_currency", "time", "notes", "source", "uuid",
            ),
        )
        for (row in rows) {
            add(
                line(
                    row.exchange,
                    row.exchangeOrderId,
                    row.pair,
                    if (row.type == "SELL") "卖出" else "买入",
                    row.price,
                    row.quantity,
                    row.fee,
                    row.feeCurrency,
                    row.transactionTime,
                    row.notes,
                    row.source,
                    row.uuid,
                ),
            )
        }
    }

    /** 资金导出行（服务层预解析币种符号后传入；typeText = 增资/撤资/校准）。 */
    data class FundCsvRow(
        val timeText: String,
        val typeText: String,
        val coin: String,
        val amount: String,
        val baseAmount: String,
        val sourceDest: String?,
        val notes: String?,
        val uuid: String,
    )

    /** 资金导出行集（表头 + 数据行，按时间升序由调用方排序）。 */
    fun funds(rows: List<FundCsvRow>): List<String> = buildList {
        add(line("type", "coin", "amount", "base_amount", "time", "source_dest", "notes", "uuid"))
        for (row in rows.sortedBy { it.timeText }) {
            add(
                line(
                    row.typeText,
                    row.coin,
                    row.amount,
                    row.baseAmount,
                    row.timeText,
                    row.sourceDest,
                    row.notes,
                    row.uuid,
                ),
            )
        }
    }

    /** 持仓汇总导出行集（重放 LENIENT 期末持仓；数量非零行 + 异常标记）。 */
    fun holdings(
        rows: List<HoldingCsvRow>,
    ): List<String> = buildList {
        add(line("coin", "coin_name", "quantity", "avg_cost", "cost_fiat", "realized_pnl", "estimated", "anomalous"))
        for (row in rows) {
            add(
                line(
                    row.symbol,
                    row.coinName,
                    row.quantity.toPlainString(),
                    row.avgCostFiat?.toPlainString(),
                    row.costFiat.toPlainString(),
                    row.realizedPnlFiat.toPlainString(),
                    row.estimated.toString(),
                    row.anomalous.toString(),
                ),
            )
        }
    }

    /** 持仓汇总行（服务层组装输入）。 */
    data class HoldingCsvRow(
        val symbol: String,
        val coinName: String,
        val quantity: java.math.BigDecimal,
        val avgCostFiat: java.math.BigDecimal?,
        val costFiat: java.math.BigDecimal,
        val realizedPnlFiat: java.math.BigDecimal,
        val estimated: Boolean,
        val anomalous: Boolean,
    )
}
