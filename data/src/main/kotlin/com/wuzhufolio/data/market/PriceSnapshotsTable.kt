package com.wuzhufolio.data.market

import com.wuzhufolio.data.db.DbGate
import com.wuzhufolio.domain.market.PriceResolution
import com.wuzhufolio.domain.market.PriceSource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * price_snapshots 表映射（DDL = M006；data-model §2.11；price 列 TEXT 勘误见迁移注记/模块记录 M5 §5）。
 */
object PriceSnapshotsTable : Table("price_snapshots") {
    val id = integer("id").autoIncrement()
    val coinId = integer("coin_id")
    val fiat = varchar("fiat", 16)
    val price = text("price")
    val priceSource = varchar("price_source", 16)
    val recordedAt = varchar("recorded_at", 40)

    override val primaryKey = PrimaryKey(id)
}

/** 快照行（data 层形态：coin = coins.id FK；recordedAt 解析回 Instant）。 */
data class SnapshotRow(
    val coinId: Int,
    val fiat: String,
    val price: BigDecimal,
    val source: PriceSource,
    val recordedAt: Instant,
)

/** 批量写输入（同小时末条语义见 [PriceSnapshotRepository.upsert]）。 */
data class SnapshotWrite(
    val coinId: Int,
    val fiat: String,
    val price: BigDecimal,
    val source: PriceSource,
    val at: Instant,
)

/** 批量 upsert 结果（审计/测试断言）。 */
data class UpsertSummary(val inserted: Int, val updated: Int)

/**
 * 快照时间规范化（本表 recorded_at 统一为固定三位毫秒的 UTC ISO 文本）：
 * `Instant.toString()` 在有/无纳秒时形态不同（"…00Z" vs "…00.123456Z"），文本比较会错序
 * （".5Z" < "Z"），不能用于 SQL 范围/排序——固定宽度格式保证文本序 == 时间序。
 */
object SqlUtc {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(instant: Instant): String = FORMAT.format(instant)

    fun parse(text: String): Instant = Instant.parse(text)
}

/**
 * 价格快照仓库（T5.2 · ADR-003 §5 / data-model §2.11 注）：同小时去重由「单写队列 + 先查后写」保证，
 * 无跨事务竞争——全部数据访问经 [DbGate]（写事务串行，读经 WAL 并行）。
 * 查询层采用内存过滤/排序（目录规模可控，先例 = SqlCoinCatalog.search）。
 */
@Suppress("TooManyFunctions") // 快照仓库动作面固有（upsert/批量/最新/桶/最近/区间/压缩/计数），全为薄数据访问
class PriceSnapshotRepository(private val gate: DbGate) {

    /** 同小时末条 upsert：存在同 (coin, fiat, 小时桶) 行则覆盖价格/来源/时刻；返回 true = 插入新行。 */
    suspend fun upsert(w: SnapshotWrite): Boolean {
        val bucketStart = PriceResolution.hourBucketOf(w.at)
        val bucketEnd = bucketStart.plusSeconds(3600)
        return gate.write {
            val existing = PriceSnapshotsTable.selectAll()
                .where { sameBucket(w.coinId, w.fiat, bucketStart, bucketEnd) }
                .singleOrNull()
            if (existing == null) {
                insertRow(w)
                true
            } else {
                PriceSnapshotsTable.update({ PriceSnapshotsTable.id eq existing[PriceSnapshotsTable.id] }) {
                    it[price] = w.price.toPlainString()
                    it[priceSource] = w.source.storageValue
                    it[recordedAt] = SqlUtc.format(w.at)
                }
                false
            }
        }
    }

    /** 批量写（事务内逐条 upsert；同批同桶多条 = 末条胜出——调用方保证批内时间升序即可确定末条）。 */
    suspend fun upsertBatch(rows: List<SnapshotWrite>): UpsertSummary {
        var inserted = 0
        var updated = 0
        gate.write {
            for (row in rows) {
                val bucketStart = PriceResolution.hourBucketOf(row.at)
                val bucketEnd = bucketStart.plusSeconds(3600)
                val existing = PriceSnapshotsTable.selectAll()
                    .where { sameBucket(row.coinId, row.fiat, bucketStart, bucketEnd) }
                    .singleOrNull()
                if (existing == null) {
                    insertRow(row)
                    inserted++
                } else {
                    PriceSnapshotsTable.update({ PriceSnapshotsTable.id eq existing[PriceSnapshotsTable.id] }) {
                        it[price] = row.price.toPlainString()
                        it[priceSource] = row.source.storageValue
                        it[recordedAt] = SqlUtc.format(row.at)
                    }
                    updated++
                }
            }
        }
        return UpsertSummary(inserted, updated)
    }

    /** 最新一条（当前价，同 coin+fiat 最后写入）。 */
    suspend fun latest(coinId: Int, fiat: String): SnapshotRow? =
        selectCoinFiat(coinId, fiat).maxByOrNull { it.recordedAt }

    /** 目标小时桶行（同小时去重保证至多一条；24h 配对/90 天内解析用）。 */
    suspend fun atBucket(coinId: Int, fiat: String, bucketStart: Instant): SnapshotRow? {
        val bucketEnd = bucketStart.plusSeconds(3600)
        val rows = gate.read {
            PriceSnapshotsTable.selectAll()
                .where { sameBucket(coinId, fiat, bucketStart, bucketEnd) }
                .toList()
                .map { it.toRow() }
        }
        return rows.maxByOrNull { it.recordedAt }
    }

    /** 不晚于 [at] 的最近一条（「最近可得价」估算兜底——M7 事件构造折算解析层用）。 */
    suspend fun nearestBefore(coinId: Int, fiat: String, at: Instant): SnapshotRow? {
        val rows = gate.read {
            PriceSnapshotsTable.selectAll()
                .where {
                    (PriceSnapshotsTable.coinId eq coinId) and
                        (PriceSnapshotsTable.fiat eq fiat) and
                        (PriceSnapshotsTable.recordedAt lessEq SqlUtc.format(at))
                }
                .toList()
                .map { it.toRow() }
        }
        return rows.maxByOrNull { it.recordedAt }
    }

    /** 区间行（历史回填去重/读取；[from, to)）。 */
    suspend fun range(coinId: Int, fiat: String, from: Instant, to: Instant): List<SnapshotRow> {
        val rows = gate.read {
            PriceSnapshotsTable.selectAll()
                .where {
                    (PriceSnapshotsTable.coinId eq coinId) and
                        (PriceSnapshotsTable.fiat eq fiat) and
                        (PriceSnapshotsTable.recordedAt greaterEq SqlUtc.format(from)) and
                        (PriceSnapshotsTable.recordedAt less SqlUtc.format(to))
                }
                .toList()
                .map { it.toRow() }
        }
        return rows.sortedBy { it.recordedAt }
    }

    /**
     * 降采样压缩（T5.2 验收「降采样正确」）：删除 90 天保留界之前的**非整点桶**行
     * （整点 00:00 UTC 行 = 当日日线，保留）；幂等。
     * @return 本次删除行数。
     */
    suspend fun compactPreservingDaily(now: Instant): Int {
        val cutoff = PriceResolution.hourlyCutoff(now)
        val doomed = gate.read {
            PriceSnapshotsTable.selectAll()
                .where { PriceSnapshotsTable.recordedAt less SqlUtc.format(cutoff) }
                .toList()
                .filter { row ->
                    val at = SqlUtc.parse(row[PriceSnapshotsTable.recordedAt])
                    !PriceResolution.isMidnightBucket(at)
                }
                .map { it[PriceSnapshotsTable.id] }
        }
        if (doomed.isEmpty()) return 0
        var removed = 0
        gate.write {
            for (id in doomed) {
                PriceSnapshotsTable.deleteWhere { PriceSnapshotsTable.id eq id }
                removed++
            }
        }
        return removed
    }

    /** 全部行数（测试/诊断）。 */
    suspend fun count(): Long = gate.read { PriceSnapshotsTable.selectAll().count() }

    private fun insertRow(w: SnapshotWrite) {
        PriceSnapshotsTable.insert {
            it[coinId] = w.coinId
            it[fiat] = w.fiat
            it[price] = w.price.toPlainString()
            it[priceSource] = w.source.storageValue
            it[recordedAt] = SqlUtc.format(w.at)
        }
    }

    private fun sameBucket(
        coinId: Int,
        fiat: String,
        bucketStart: Instant,
        bucketEnd: Instant,
    ): org.jetbrains.exposed.v1.core.Op<Boolean> =
        (PriceSnapshotsTable.coinId eq coinId) and
            (PriceSnapshotsTable.fiat eq fiat) and
            (PriceSnapshotsTable.recordedAt greaterEq SqlUtc.format(bucketStart)) and
            (PriceSnapshotsTable.recordedAt less SqlUtc.format(bucketEnd))

    private suspend fun selectCoinFiat(coinId: Int, fiat: String) = gate.read {
        PriceSnapshotsTable.selectAll()
            .where { (PriceSnapshotsTable.coinId eq coinId) and (PriceSnapshotsTable.fiat eq fiat) }
            .toList()
            .map { it.toRow() }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRow(): SnapshotRow = SnapshotRow(
        coinId = this[PriceSnapshotsTable.coinId],
        fiat = this[PriceSnapshotsTable.fiat],
        price = BigDecimal(this[PriceSnapshotsTable.price]),
        source = PriceSource.fromStorage(this[PriceSnapshotsTable.priceSource]),
        recordedAt = SqlUtc.parse(this[PriceSnapshotsTable.recordedAt]),
    )
}
