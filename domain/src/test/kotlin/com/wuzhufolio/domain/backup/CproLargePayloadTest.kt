package com.wuzhufolio.domain.backup

import com.wuzhufolio.domain.security.KdfParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P6 · T6.2「大载荷」守护：`.cpro` 非流式编解码在**真实用户规模**下的正确性与可完成性。
 *
 * 与 `BackupLoadBenchmark`（手工夹具，出内存曲线）互补：本测试**进默认回归**，锁住「万级交易 + 数万快照
 * 仍能无损往返、头部摘要与实际记录一致」。若未来改成流式/分块格式（C2），本测试是跨格式行为基线。
 *
 * KDF 用 OWASP 下限（19MiB/t2）以控制 CI 时长——KDF 参数不参与编解码语义，与生产 `KdfParams.DEFAULT` 同契约。
 */
class CproLargePayloadTest {

    private val txCount = 20_000
    private val snapshotCount = 60_000

    private fun header(counts: CproCounts) = CproHeader(
        formatVersion = CproCodec.FORMAT_VERSION,
        appVersion = "1.0.0-test",
        exportedAt = "2026-09-14T00:00:00Z",
        counts = counts,
        range = CproRange("2021-01-01T00:00:00Z", "2026-09-14T00:00:00Z"),
        kdf = CproKdf(
            alg = CproCodec.KDF_ALG,
            salt = CproCodec.hex(ByteArray(16) { (it * 7).toByte() }),
            m = KdfParams.OWASP_MINIMUM.memoryKiB,
            t = KdfParams.OWASP_MINIMUM.iterations,
            p = KdfParams.OWASP_MINIMUM.parallelism,
        ),
        cipher = CproCodec.CIPHER,
    )

    private fun transaction(i: Int) = CproTransaction(
        uuid = "0000%06d-1111-4222-8333-444455556666".format(i),
        exchange = "BINANCE",
        exchangeOrderId = "900%07d".format(i),
        pair = if (i % 2 == 0) "BTC/USDT" else "ETH/USDT",
        baseCgId = if (i % 2 == 0) "bitcoin" else "ethereum",
        quoteCgId = "tether",
        type = if (i % 3 == 0) "SELL" else "BUY",
        price = "%d.5".format(1_000 + (i % 90_000)),
        quantity = "0.%08d".format(i % 100_000_000),
        fee = "0.0001",
        feeCurrency = "USDT",
        transactionTime = "2026-01-01T00:%02d:%02dZ".format((i / 60) % 60, i % 60),
        notes = null,
        createdAt = "2026-09-14T00:00:00Z",
        source = "CSV",
        priceStatus = "RESOLVED",
    )

    private fun snapshot(i: Int) = CproSnapshot(
        coinCgId = "coin-%04d".format(i % 200),
        fiat = if (i % 2 == 0) "usd" else "cny",
        price = "%d.%08d".format(i % 90_000, i % 100_000_000),
        priceSource = "COINGECKO",
        recordedAt = "2026-%02d-%02dT%02d:00:00Z".format((i % 12) + 1, (i % 28) + 1, i % 24),
    )

    @Test
    fun `large cpro payload round trips losslessly within the default test heap`() {
        val records = CproRecords(
            transactions = (0 until txCount).map(::transaction),
            capitalFlows = (0 until 1_000).map {
                CproCapitalFlow(
                    uuid = "flow-%06d".format(it),
                    type = "DEPOSIT",
                    amount = "1000.%08d".format(it),
                    baseAmount = "1000.%08d".format(it),
                    currency = "USDT",
                    coinCgId = "tether",
                    flowTime = "2026-01-01T00:00:00Z",
                    sourceDest = null,
                    notes = null,
                    createdAt = "2026-09-14T00:00:00Z",
                    priceStatus = "RESOLVED",
                )
            },
            priceSnapshots = (0 until snapshotCount).map(::snapshot),
        )
        val counts = CproCounts(
            transactions = txCount,
            capitalFlows = 1_000,
            reconciliationRecords = 0,
            feeRules = 0,
            apiKeys = 0,
            settings = 0,
            priceSnapshots = snapshotCount,
        )
        val password = "Large-Payload-Pass-1".toCharArray()

        val bytes = CproCodec.encode(header(counts), CproPayload(CproMeta("2026-09-14T00:00:00Z", "USD"), records), password)

        // 明文头部可独立解析（恢复向导第一步不依赖密码）
        val parsed = CproCodec.parseHeader(bytes)
        assertEquals(txCount, parsed.counts.transactions)
        assertEquals(snapshotCount, parsed.counts.priceSnapshots)

        val decoded = CproCodec.decode(bytes, password)
        assertEquals(txCount, decoded.records.transactions.size)
        assertEquals(snapshotCount, decoded.records.priceSnapshots.size)
        assertEquals(1_000, decoded.records.capitalFlows.size)
        // 无损：抽查首/中/末三条（金额十进制串、时间、cg_id 引用逐字保真）
        assertEquals(records.transactions.first(), decoded.records.transactions.first())
        assertEquals(records.transactions[txCount / 2], decoded.records.transactions[txCount / 2])
        assertEquals(records.transactions.last(), decoded.records.transactions.last())
        assertEquals(records.priceSnapshots.last(), decoded.records.priceSnapshots.last())
        // 文件体积量级合理性（密文 ≈ 明文 JSON；异常偏大/偏小都说明格式或序列化出了问题）
        val fileMb = bytes.size / 1024.0 / 1024.0
        assertTrue(fileMb in 1.0..64.0, "unexpected .cpro size: $fileMb MiB")
    }
}
