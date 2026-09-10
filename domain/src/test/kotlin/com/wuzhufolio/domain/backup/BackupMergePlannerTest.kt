package com.wuzhufolio.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 合并规划器单测（M9 · T9.2 · PRD 5.2-6 三级去重 + 幂等合并 + 全量覆盖口径；黄金用例 10 形状）。
 */
class BackupMergePlannerTest {

    private val resolvable = setOf("bitcoin", "tether", "usd-coin")

    private fun tx(
        uuid: String,
        orderId: String? = null,
        exchange: String = "BINANCE",
        baseCg: String = "bitcoin",
    ) = CproTransaction(
        uuid = uuid,
        exchange = exchange,
        exchangeOrderId = orderId,
        pair = "BTC/USDT",
        baseCgId = baseCg,
        quoteCgId = "tether",
        type = "BUY",
        price = "50000",
        quantity = "0.1",
        fee = "0",
        transactionTime = "2025-01-01T00:00:00Z",
        createdAt = "2025-01-02T00:00:00Z",
        source = "Manual",
        priceStatus = "OK",
    )

    private fun flow(uuid: String, cg: String = "tether") = CproCapitalFlow(
        uuid = uuid,
        type = "DEPOSIT",
        amount = "100",
        baseAmount = "100",
        currency = "USDT",
        coinCgId = cg,
        flowTime = "2025-01-01T00:00:00Z",
        createdAt = "2025-01-02T00:00:00Z",
        priceStatus = "OK",
    )

    private fun recon(uuid: String, cg: String = "bitcoin") = CproReconciliation(
        uuid = uuid,
        symbol = "BTC",
        coinCgId = cg,
        exchange = "BINANCE",
        localQuantity = "1",
        exchangeQuantity = "0.8",
        delta = "-0.2",
        baseAmount = "10000",
        createdAt = "2025-02-01T00:00:00Z",
    )

    private fun snap(cg: String = "bitcoin", fiat: String = "USD", hour: Int = 0) = CproSnapshot(
        coinCgId = cg,
        fiat = fiat,
        price = "50000",
        priceSource = "coingecko",
        recordedAt = "2025-01-01T%02d:30:00Z".format(hour),
    )

    // ---- 增量合并：三级去重 ----

    @Test
    fun `uuid match skips duplicate`() {
        val records = CproRecords(transactions = listOf(tx("u-1")))
        val plan = BackupMergePlanner.plan(
            records,
            BackupMergePlanner.ExistingKeys(txUuids = setOf("u-1")),
            resolvable,
            fullOverwrite = false,
        )
        assertEquals(0, plan.transactions.size)
        assertEquals(1, plan.duplicateSkipped)
    }

    @Test
    fun `exchange plus order id match skips duplicate`() {
        val records = CproRecords(transactions = listOf(tx("u-other", orderId = "o-1")))
        val plan = BackupMergePlanner.plan(
            records,
            BackupMergePlanner.ExistingKeys(txOrderKeys = setOf("BINANCE|o-1")),
            resolvable,
            fullOverwrite = false,
        )
        assertEquals(0, plan.transactions.size)
        assertEquals(1, plan.duplicateSkipped)
    }

    @Test
    fun `intra-payload duplicates collapse`() {
        val records = CproRecords(transactions = listOf(tx("u-1"), tx("u-1"), tx("u-2", orderId = "o-x")))
        val plan = BackupMergePlanner.plan(records, BackupMergePlanner.ExistingKeys(), resolvable, false)
        assertEquals(2, plan.transactions.size)
        assertEquals(1, plan.duplicateSkipped)
    }

    @Test
    fun `api keys skip on exchange plus name`() {
        val key = CproApiKey(name = "主号", exchangeName = "BINANCE", apiKey = "k", secretKey = "s", status = "OK")
        val records = CproRecords(apiKeys = listOf(key))
        val plan = BackupMergePlanner.plan(
            records,
            BackupMergePlanner.ExistingKeys(apiKeyKeys = setOf("BINANCE|主号")),
            resolvable,
            false,
        )
        assertEquals(0, plan.apiKeys.size)
        assertEquals(1, plan.duplicateSkipped)
    }

    @Test
    fun `fee rules and settings always upsert backup wins`() {
        val records = CproRecords(
            feeRules = listOf(
                CproFeeRule("", "0.1", "0.1"),
                CproFeeRule("BINANCE", "0.05", "0.06"),
            ),
            settings = listOf(CproSetting("pnl_scheme", "red_up"), CproSetting("custom.key", "v")),
        )
        val plan = BackupMergePlanner.plan(records, BackupMergePlanner.ExistingKeys(), resolvable, false)
        assertEquals(2, plan.feeRules.size, "费率规则备份优先覆盖（upsert 清单）")
        assertEquals(2, plan.settings.size)
        assertEquals("red_up", plan.settings.first { it.key == "pnl_scheme" }.value)
    }

    @Test
    fun `snapshot idempotent by coin fiat hour bucket local wins`() {
        val records = CproRecords(priceSnapshots = listOf(snap(), snap(cg = "tether")))
        val existing = BackupMergePlanner.ExistingKeys(
            snapshotBuckets = setOf(BackupMergePlanner.snapshotBucket(snap())!!),
        )
        val plan = BackupMergePlanner.plan(records, existing, resolvable, false)
        assertEquals(1, plan.priceSnapshots.size, "同桶已存在 → 保留本地；缺桶 → 插入")
        assertEquals("tether", plan.priceSnapshots.first().coinCgId)
    }

    @Test
    fun `missing coin rows are skipped and listed`() {
        val records = CproRecords(
            transactions = listOf(tx("u-1", baseCg = "unknown-coin")),
            capitalFlows = listOf(flow("f-1", cg = "also-unknown")),
            reconciliationRecords = listOf(recon("r-1")),
            priceSnapshots = listOf(snap(cg = "unknown-coin")),
        )
        val plan = BackupMergePlanner.plan(records, BackupMergePlanner.ExistingKeys(), resolvable, false)
        assertEquals(1, plan.businessInserts, "仅可解析的校准行保留")
        assertEquals(3, plan.missingCoinSkipped, "交易/资金/快照各 1 条因缺失币种跳过")
        assertTrue(plan.missingCoinIds.containsAll(listOf("unknown-coin", "also-unknown")))
        assertEquals(1, plan.reconciliationRecords.size, "可解析币种的校准行保留")
    }

    // ---- 全量覆盖 ----

    @Test
    fun `full overwrite ignores local keys but keeps snapshot idempotency`() {
        val records = CproRecords(
            transactions = listOf(tx("u-1", orderId = "o-1")),
            capitalFlows = listOf(flow("f-1")),
            reconciliationRecords = listOf(recon("r-1")),
            apiKeys = listOf(
                CproApiKey(
                    name = "主号",
                    exchangeName = "BINANCE",
                    apiKey = "k",
                    secretKey = "s",
                    status = "OK",
                ),
            ),

            priceSnapshots = listOf(snap()),
        )
        val existing = BackupMergePlanner.ExistingKeys(
            txUuids = setOf("u-1"),
            txOrderKeys = setOf("BINANCE|o-1"),
            flowUuids = setOf("f-1"),
            reconUuids = setOf("r-1"),
            apiKeyKeys = setOf("BINANCE|主号"),
            snapshotBuckets = setOf(BackupMergePlanner.snapshotBucket(snap())!!),
        )
        val plan = BackupMergePlanner.plan(records, existing, resolvable, fullOverwrite = true)
        assertEquals(1, plan.transactions.size, "全量覆盖：表已清空，本地 uuid/订单号不拦截")
        assertEquals(1, plan.capitalFlows.size)
        assertEquals(1, plan.reconciliationRecords.size)
        assertEquals(1, plan.apiKeys.size)
        assertEquals(0, plan.priceSnapshots.size, "快照属全局公共表：本地同桶行保留（不清空也不回退）")
        assertEquals(1, plan.duplicateSkipped, "唯一跳过 = 同桶快照（幂等合并计入重复跳过）")
    }

    // ---- 黄金用例 10 形状：同一载荷二次规划零新增 ----

    @Test
    fun `golden case 10 - re-planning the same payload is a no-op`() {
        val records = CproRecords(
            transactions = listOf(tx("u-1", orderId = "o-1"), tx("u-2")),
            capitalFlows = listOf(flow("f-1")),
            priceSnapshots = listOf(snap(), snap(hour = 1)),
        )
        val first = BackupMergePlanner.plan(records, BackupMergePlanner.ExistingKeys(), resolvable, false)
        assertEquals(5, first.businessInserts + first.priceSnapshots.size, "2 交易 + 1 资金 + 2 快照")
        // 模拟第一轮已应用：既有键 = 第一轮插入集
        val applied = BackupMergePlanner.ExistingKeys(
            txUuids = first.transactions.map { it.uuid }.toSet(),
            txOrderKeys = first.transactions.mapNotNull { r ->
                r.exchangeOrderId?.let { r.exchange.uppercase() + "|" + it }
            }.toSet(),
            flowUuids = first.capitalFlows.map { it.uuid }.toSet(),
            snapshotBuckets = first.priceSnapshots.map { BackupMergePlanner.snapshotBucket(it)!! }.toSet(),
        )
        val second = BackupMergePlanner.plan(records, applied, resolvable, false)
        assertEquals(0, second.businessInserts + second.priceSnapshots.size, "第二次导入不产生重复记录")
        assertEquals(5, second.duplicateSkipped)
    }
}
