package com.wuzhufolio.domain.backup

import com.wuzhufolio.domain.security.KdfParams
import java.lang.management.ManagementFactory
import kotlin.math.max

/**
 * P6 大载荷内存曲线夹具（到期检查点 = M13 安全自查清单 §7-1 / STATUS「P6 启动须携带的开放项 ②」）。
 *
 * 背景：`.cpro` 编解码为**非流式**（`CproCodec.encode/decode` 全量入内存；ADR-005 风险表原计划以流式缓解，
 * 但 GCM 单 tag 全载荷认证决定了分块流式 = 改格式 = C2）。本夹具给出**实测**内存曲线，用于回答：
 * 「真实用户规模（乃至 10 倍规模）下，单次备份/恢复的堆峰值是多少、是否落在打包版默认堆内」。
 *
 * 运行：`./gradlew :domain:backupBenchmark`（默认 `-Xmx1g`，可 `-PbenchXmx=512m` 覆盖）
 *      单档：`./gradlew :domain:backupBenchmark --args="heavy"`（light/typical/heavy/stress）
 *
 * 口径：峰值堆 = 独立采样线程每 2ms 读一次 `MemoryMXBean.heapMemoryUsage.used` 的最大值（含未回收中间对象，
 * 比「前后 used 差」更接近真实峰值）；每档先 `System.gc()` 取基线。夹具不写文件（纯内存编解码）。
 */
private data class Scale(
    val key: String,
    val label: String,
    val transactions: Int,
    val snapshots: Int,
    val flows: Int,
    val recon: Int,
)

private val SCALES = listOf(
    Scale("light", "轻量 · 1 年 · 1 币（新用户）", transactions = 1_000, snapshots = 5_000, flows = 100, recon = 20),
    Scale("typical", "典型 · 3 年 · 8 币", transactions = 5_000, snapshots = 30_000, flows = 300, recon = 50),
    Scale("heavy", "重度 · 5 年 · 20 币 + 高频交易", transactions = 50_000, snapshots = 150_000, flows = 1_000, recon = 200),
    Scale("stress", "压力 · 10 年 · 50 币（10× 典型）", transactions = 200_000, snapshots = 500_000, flows = 5_000, recon = 1_000),
)

private const val FEE_RULES = 20
private const val API_KEYS = 5
private const val SETTINGS = 40

private fun tx(i: Int): CproTransaction {
    val base = if (i % 3 == 0) "bitcoin" else if (i % 3 == 1) "ethereum" else "tether"
    val symbol = if (i % 3 == 0) "BTC" else if (i % 3 == 1) "ETH" else "USDT"
    return CproTransaction(
        uuid = "3f2a%06d-9c1b-4d7e-8a55-01b2c3d4e5f6".format(i),
        exchange = "BINANCE",
        exchangeOrderId = "100000%06d".format(i),
        pair = "$symbol/USDT",
        baseCgId = base,
        quoteCgId = "tether",
        type = if (i % 4 == 0) "SELL" else "BUY",
        price = "%d.%08d".format(20_000 + (i % 60_000), i % 100_000_000),
        quantity = "0.%08d".format(i % 100_000_000),
        fee = "0.0001%04d".format(i % 10_000),
        feeCurrency = "USDT",
        transactionTime = "202%d-0%d-1%dT0%d:2%d:%02dZ".format(i % 6, (i % 9) + 1, i % 10, i % 10, i % 6, i % 60),
        notes = "imported row $i · 交易所导出补录（含备注与中文）",
        createdAt = "2026-09-14T00:00:00Z",
        source = if (i % 5 == 0) "CSV" else "BINANCE API",
        priceStatus = "RESOLVED",
    )
}

private fun snapshot(i: Int): CproSnapshot = CproSnapshot(
    coinCgId = "coin-%03d".format(i % 50),
    fiat = "usd",
    price = "0.%08d".format(i % 100_000_000),
    priceSource = "COINGECKO",
    recordedAt = "2026-0%d-1%dT%02d:00:00Z".format((i % 9) + 1, i % 10, i % 24),
)

private fun flow(i: Int): CproCapitalFlow = CproCapitalFlow(
    uuid = "a1b2%06d-0000-4000-8000-abcdefabcdef".format(i),
    type = if (i % 2 == 0) "DEPOSIT" else "WITHDRAWAL",
    amount = "%d.%08d".format(1_000 + i, i % 100_000_000),
    baseAmount = "%d.%08d".format(999 + i, i % 100_000_000),
    currency = "USDT",
    coinCgId = "tether",
    flowTime = "2026-01-1%dT0%d:00:00Z".format(i % 10, i % 10),
    sourceDest = "Binance 现货账户",
    notes = "增资备注 $i",
    createdAt = "2026-09-14T00:00:00Z",
    priceStatus = "RESOLVED",
)

private fun recon(i: Int): CproReconciliation = CproReconciliation(
    uuid = "ccdd%06d-1111-4222-8333-1234567890ab".format(i),
    symbol = "BTC",
    coinCgId = "bitcoin",
    exchange = "BINANCE",
    localQuantity = "0.%08d".format(i % 100_000_000),
    exchangeQuantity = "0.%08d".format((i + 1) % 100_000_000),
    delta = "0.%08d".format(i % 100_000_000),
    baseAmount = "%d.%08d".format(100 + i, i % 100_000_000),
    createdAt = "2026-09-14T00:00:00Z",
)

private fun scale(key: String): Scale = SCALES.first { it.key == key }

@Suppress("ExplicitGarbageCollectionCall") // 基准需要 GC 后基线（前后各一次），非生产路径
private fun runOne(s: Scale): String {
    val payload = buildPayload(s)
    val header = buildHeader(s)
    val password = "Bench-Pass-2026".toCharArray()
    val totalRecords = s.transactions + s.snapshots + s.flows + s.recon + FEE_RULES + API_KEYS + SETTINGS

    System.gc()
    Thread.sleep(60)
    val baseline = usedHeap()
    val encodeWatch = Sampler()
    val t0 = System.nanoTime()
    val bytes = CproCodec.encode(header, payload, password)
    val encodeMs = (System.nanoTime() - t0) / 1_000_000
    val encodePeak = encodeWatch.stop()

    System.gc()
    Thread.sleep(60)
    val decodeWatch = Sampler()
    val t1 = System.nanoTime()
    val decoded = CproCodec.decode(bytes, password)
    val decodeMs = (System.nanoTime() - t1) / 1_000_000
    val decodePeak = decodeWatch.stop()

    check(decoded.records.transactions.size == s.transactions) { "round-trip transactions mismatch" }
    check(decoded.records.priceSnapshots.size == s.snapshots) { "round-trip snapshots mismatch" }

    return buildString {
        append("%-8s | %10d | %8.1f | %8.1f | %7d | %7d | %8.1f | %8.1f | %6.1f".format(
            s.key,
            totalRecords,
            bytes.size / 1024.0 / 1024.0,
            encodePayloadJsonMb(payload),
            encodeMs,
            decodeMs,
            (encodePeak - baseline) / 1024.0 / 1024.0,
            (decodePeak - baseline) / 1024.0 / 1024.0,
            max(encodePeak, decodePeak).toDouble() / bytes.size,
        ))
    }
}

/** 合成载荷（字段长度贴近真实记录：十进制串 / UTC 时间 / 中文备注）。 */
@Suppress("LongMethod") // 七类记录各一段构造，拆开反而看不出规模对应关系
private fun buildPayload(s: Scale): CproPayload = CproPayload(
    meta = CproMeta(exportedAt = "2026-09-14T00:00:00Z", baseFiat = "USD"),
    records = CproRecords(
        transactions = (0 until s.transactions).map(::tx),
        capitalFlows = (0 until s.flows).map(::flow),
        reconciliationRecords = (0 until s.recon).map(::recon),
        feeRules = (0 until FEE_RULES).map { CproFeeRule("BINANCE", "0.001", "0.001") },
        apiKeys = (0 until API_KEYS).map {
            CproApiKey(
                name = "只读 key " + it,
                exchangeName = "BINANCE",
                apiKey = "AKIA%040d".format(it),
                secretKey = "SECRET%056d".format(it),
                passphrase = null,
                extra = null,
                lastSyncTime = "2026-09-14T00:00:00Z",
                status = "OK",
            )
        },
        settings = (0 until SETTINGS).map { CproSetting("account.key." + it, "value-" + it + "-" + "x".repeat(40)) },
        priceSnapshots = (0 until s.snapshots).map(::snapshot),
    ),
)

private fun buildHeader(s: Scale): CproHeader = CproHeader(
    formatVersion = CproCodec.FORMAT_VERSION,
    appVersion = "1.0.0-bench",
    exportedAt = "2026-09-14T00:00:00Z",
    counts = CproCounts(
        transactions = s.transactions,
        capitalFlows = s.flows,
        reconciliationRecords = s.recon,
        feeRules = FEE_RULES,
        apiKeys = API_KEYS,
        settings = SETTINGS,
        priceSnapshots = s.snapshots,
    ),
    range = CproRange(minTime = "2020-01-01T00:00:00Z", maxTime = "2026-09-14T00:00:00Z"),
    kdf = CproKdf(
        alg = CproCodec.KDF_ALG,
        salt = CproCodec.hex(ByteArray(16) { it.toByte() }),
        m = KdfParams.DEFAULT.memoryKiB,
        t = KdfParams.DEFAULT.iterations,
        p = KdfParams.DEFAULT.parallelism,
    ),
    cipher = CproCodec.CIPHER,
)

/** 明文 JSON 载荷大小（同一序列化口径；仅用于报告「内存/载荷」比值基线）。 */
private fun encodePayloadJsonMb(payload: CproPayload): Double {
    val json = kotlinx.serialization.json.Json { encodeDefaults = true }
    return json.encodeToString(CproPayload.serializer(), payload).toByteArray().size / 1024.0 / 1024.0
}

private fun usedHeap(): Long = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used

private class Sampler {
    private val peak = java.util.concurrent.atomic.AtomicLong(0)
    @Volatile private var running = true
    private val thread = Thread {
        while (running) {
            peak.accumulateAndGet(usedHeap(), ::max)
            Thread.sleep(2)
        }
    }.apply { isDaemon = true; start() }

    fun stop(): Long {
        running = false
        thread.join(200)
        peak.accumulateAndGet(usedHeap(), ::max)
        return peak.get()
    }
}

fun main(args: Array<String>) {
    val requested = args.firstOrNull { !it.startsWith("-") }
    val scales = if (requested != null) listOf(scale(requested)) else SCALES
    val maxHeapMb = Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0

    println("== WuZhuFolio .cpro 大载荷内存曲线（P6）==")
    println("JVM maxHeap = %.0f MiB · KDF = m=%dKiB t=%d p=%d".format(
        maxHeapMb,
        KdfParams.DEFAULT.memoryKiB,
        KdfParams.DEFAULT.iterations,
        KdfParams.DEFAULT.parallelism,
    ))
    println("scale    | 记录总数   | 文件MiB  | 载荷MiB  | 编码ms  | 解码ms  | 编码峰值MiB | 解码峰值MiB | 峰值/文件")
    println("-".repeat(110))
    var failed = false
    for (s in scales) {
        try {
            println(runOne(s))
        } catch (t: Throwable) {
            failed = true
            println("%-8s | %10d | %s".format(s.key, s.transactions + s.snapshots, "FAILED: ${t::class.simpleName}: ${t.message}"))
        }
    }
    println("-".repeat(110))
    println("注：峰值 = 独立采样线程（2ms）观测的堆 used 最大值 − GC 后基线；含 JSON 中间对象，未含文件 IO。")
    if (failed) println("（存在失败档：见上表 FAILED 行——用于标定当前 -Xmx 下的容量边界）")
}
