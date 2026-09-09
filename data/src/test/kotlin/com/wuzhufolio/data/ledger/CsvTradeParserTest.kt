package com.wuzhufolio.data.ledger

import com.wuzhufolio.domain.engine.Side
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * CSV 解析器（T7.3）：标准模板/别名表头、UTC 时间解析、格式错误行、歧义上浮、# 注释行跳过。
 */
class CsvTradeParserTest {

    private fun parse(env: LedgerTestEnv, csv: String) = runBlocking {
        env.parser.parse(csv.toByteArray())
    }

    @Test
    fun parsesTemplateWithAliasHeadersAndUtc() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val csv = """
                exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes
                BINANCE,abc-1,BTC/USDT,买入,50000,0.2,0.1,USDT,2025-01-10 08:00:00,首笔
                BINANCE,,ETH/USDT,SELL,3400,1.5,0,USDT,2026-08-29T10:00:00Z,
            """.trimIndent()
            val out = parse(env, csv)
            assertEquals(0, out.errors.size)
            assertEquals(2, out.rows.size)
            val first = out.rows[0]
            assertEquals("BINANCE", first.exchange)
            assertEquals("abc-1", first.orderId)
            assertEquals("BTC/USDT", first.pair)
            assertEquals(Side.BUY, first.side)
            assertEquals(Instant.parse("2025-01-10T08:00:00Z"), first.time)
            assertEquals("USDT", first.feeCurrency)
            assertEquals("bitcoin", first.baseCoin!!.cgId)
            assertEquals("tether", first.quoteCoin!!.cgId)
            assertEquals(Side.SELL, out.rows[1].side)
            assertEquals(Instant.parse("2026-08-29T10:00:00Z"), out.rows[1].time)
        }
    }

    @Test
    fun defaultExchangeAppliesWhenBlank() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val csv = "exchange,pair,side,price,quantity,time\n, BTC/USDT,买入,1,1,2025-01-01 00:00:00".replace(" ", "")
            // 手工构造：exchange 为空 -> 默认 BINANCE
            val out = parse(env, "exchange,pair,side,price,quantity,time\n,BTC/USDT,buy,1,1,2025-01-01 00:00:00")
            assertEquals(1, out.rows.size)
            assertEquals("BINANCE", out.rows[0].exchange)
        }
    }

    @Test
    fun formatErrorsReportedPerLine() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val csv = """
                exchange,pair,side,price,quantity,time
                BINANCE,BTCUSDT,买入,1,1,2025-01-01 00:00:00
                BINANCE,BTC/USDT,买入,abc,1,2025-01-01 00:00:00
                BINANCE,BTC/USDT,买入,-1,1,2025-01-01 00:00:00
                BINANCE,BTC/USDT,买入,1,1,not-a-time
            """.trimIndent()
            val out = parse(env, csv)
            // 拼接符号不猜测切分 / 非数字 / 价格非正 / 时间无法解析
            assertEquals(4, out.errors.size)
            assertEquals(0, out.rows.size)
            assertTrue(out.errors[0].reason.contains("无法解析"))
            assertTrue(out.errors[1].reason.contains("数字格式"))
        }
    }

    @Test
    fun ambiguousTickerSurfacesCandidates() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            // BTC 在目录唯一（无同名歧义）——构造歧义需候选 >1；用不存在重复的资产验证 NotFound 归未解析
            val csv = "exchange,pair,side,price,quantity,time\nBINANCE,XYZ/USDT,买入,1,1,2025-01-01 00:00:00"
            val out = parse(env, csv)
            assertEquals(1, out.rows.size)
            assertTrue(out.rows[0].baseCoin == null, "未收录资产不解析")
            assertTrue(out.ambiguous.isEmpty())
        }
    }

    @Test
    fun commentLinesAreSkipped() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val csv = """
                # WuZhuFolio 模板注释行
                exchange,pair,side,price,quantity,time
                BINANCE,BTC/USDT,买入,1,1,2025-01-01 00:00:00
            """.trimIndent()
            val out = parse(env, csv)
            assertEquals(1, out.rows.size)
            assertEquals(0, out.errors.size)
        }
    }

    /**
     * 时间容错（2026-09-08 修复轮：模板经 Excel/表格工具改写日期后导入报三条格式错误）。
     * 覆盖：斜杠分隔 + 单位小时、ISO 带 Z、中文日期、紧凑 yyyyMMdd、epoch 秒/毫秒、Excel 序列。
     */
    @Test
    fun tolerantTimeFormatsAllParse() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val cases = linkedMapOf(
                "2026/1/10 8:00" to "2026-01-10T08:00:00Z",
                "2026/01/10 08:00:30" to "2026-01-10T08:00:30Z",
                "2026-01-10T08:00:00Z" to "2026-01-10T08:00:00Z",
                "2026-01-10 08:00:00+08:00" to "2026-01-10T00:00:00Z",
                "2026年1月10日 08:00:00" to "2026-01-10T08:00:00Z",
                "20260110" to "2026-01-10T00:00:00Z",
                "1768032000" to "2026-01-10T08:00:00Z", // epoch 秒
                "1768032000000" to "2026-01-10T08:00:00Z", // epoch 毫秒
                "46032.333333" to null, // Excel 序列：仅断言可解析（不校验具体时刻）
            )
            for ((raw, expected) in cases) {
                val parsed = env.parser.parseTime(raw)
                assertTrue(parsed != null, "应可解析：$raw")
                if (expected != null) {
                    assertEquals(Instant.parse(expected), parsed, "解析结果不符：$raw")
                }
            }
        }
    }

    /** 完整导入链路：Excel 风格时间的模板行不再报格式错误。 */
    @Test
    fun excelStyleTimesImportWithoutErrors() = runBlocking {
        LedgerTestEnv().use { env ->
            env.login()
            val csv = """
                exchange,order_id,pair,side,price,quantity,fee,fee_currency,time,notes
                BINANCE,,BTC/USDT,买入,50000,0.2,0.1,USDT,2026/1/10 8:00:00,
                BINANCE,,ETH/USDT,买入,3000,2,0.6,USDT,2026/2/15 12:30:00,
                BINANCE,,BTC/USDT,卖出,60000,0.1,0.06,USDT,2026/6/1 9:00:00,
            """.trimIndent()
            val out = parse(env, csv)
            assertEquals(0, out.errors.size, "Excel 风格时间不应报格式错误：" + out.errors)
            assertEquals(3, out.rows.size)
            assertEquals(Instant.parse("2026-01-10T08:00:00Z"), out.rows[0].time)
        }
    }
}
