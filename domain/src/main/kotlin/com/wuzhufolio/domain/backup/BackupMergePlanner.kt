package com.wuzhufolio.domain.backup

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * 增量合并 / 全量覆盖规划器（M9 · T9.2 · PRD 故事 5.2-6 / ADR-005 §4，纯规则无 IO）。
 *
 * 去重优先级（增量合并，PRD 5.2-6）：
 * - 业务记录：记录 uuid → 交易所+订单号 → 无标识模糊匹配并逐条确认；
 *   v1 格式 uuid 为导出端非空约束（M009/M011/M012 DDL uuid NOT NULL），**「无标识记录」在 v1 载荷中
 *   无输入面**——模糊级去重不存在触发路径（留档说明见模块记录 M9 §5）；
 * - api_keys：(exchange_name + 别名) 命中即跳过（本地已有同键密钥不覆盖）；
 * - fee_rules：(交易所) 命中即**备份优先覆盖**（upsert）；
 * - 账户级 settings：按 key 逐项**备份优先覆盖**；
 * - price_snapshots：币种+法币+小时桶幂等合并——本地已有同桶行时**保留本地**（不回退本地较新快照），
 *   仅在缺行时插入（幂等性不受影响：同文件重导结果不变，黄金用例 10）。
 *
 * 全量覆盖（ADR-005 §4）：账户级业务表先清空后全量插入（载荷内仍按 uuid/订单号批内去重），
 * **快照属全局公共表不清空、仍按桶幂等合并**。
 *
 * 币种可解析性：载荷以 cg_id 引用币种；[ExistingKeys] 由 data 层装配，[resolvableCoinIds] =
 * 本地目录可解析的 cg_id 集合；引用缺失币种的记录跳过并计数（恢复摘要列出，目录刷新后可重导）。
 */
object BackupMergePlanner {

    /** 本地既有键（导入前快照；全部为字符串键，coin 维度以 cg_id 表达）。 */
    data class ExistingKeys(
        val txUuids: Set<String> = emptySet(),
        /** "EXCHANGE|order_id"（order_id 非空行）。 */
        val txOrderKeys: Set<String> = emptySet(),
        val flowUuids: Set<String> = emptySet(),
        val reconUuids: Set<String> = emptySet(),
        /** "EXCHANGE|别名"。 */
        val apiKeyKeys: Set<String> = emptySet(),
        /** 快照桶键 "cg_id|FIAT|epochHour"。 */
        val snapshotBuckets: Set<String> = emptySet(),
    )

    /** 规划结果：待写入清单 + 跳过计数（应用层照单执行，不再二次判定）。 */
    data class MergePlan(
        val transactions: List<CproTransaction>,
        val capitalFlows: List<CproCapitalFlow>,
        val reconciliationRecords: List<CproReconciliation>,
        val feeRules: List<CproFeeRule>,
        val apiKeys: List<CproApiKey>,
        val settings: List<CproSetting>,
        val priceSnapshots: List<CproSnapshot>,
        /** 既有 uuid/订单号/密钥/快照桶命中等重复跳过条数。 */
        val duplicateSkipped: Int,
        /** 本地币种目录缺 cg_id 跳过条数。 */
        val missingCoinSkipped: Int,
        val missingCoinIds: List<String>,
    ) {
        /** 待写入业务记录合计（不含快照/费率/设置）。 */
        val businessInserts: Int
            get() = transactions.size + capitalFlows.size + reconciliationRecords.size
    }

    /** 规划上下文（缺失币种收集 + 跳过计数的共享累加器）。 */
    private class PlanContext(val resolvable: Set<String>, val fullOverwrite: Boolean) {
        val missing = sortedSetOf<String>()
        var duplicateSkipped = 0
        var missingSkipped = 0

    }

    fun plan(
        records: CproRecords,
        existing: ExistingKeys,
        resolvableCoinIds: Set<String>,
        fullOverwrite: Boolean,
    ): MergePlan {
        val ctx = PlanContext(resolvableCoinIds, fullOverwrite)
        val txs = planTransactions(records.transactions, existing, ctx)
        val flows = planFlows(records.capitalFlows, existing, ctx)
        val recons = planRecons(records.reconciliationRecords, existing, ctx)
        val keys = planApiKeys(records.apiKeys, existing, ctx)
        val snapshots = planSnapshots(records.priceSnapshots, existing, ctx)
        // fee_rules / settings：备份优先覆盖（批内同键后者胜），无跳过路径
        val feeRules = LinkedHashMap<String, CproFeeRule>()
        for (rule in records.feeRules) feeRules[rule.exchange.trim().uppercase()] = rule
        val settings = LinkedHashMap<String, CproSetting>()
        for (setting in records.settings) settings[setting.key] = setting
        return MergePlan(
            transactions = txs,
            capitalFlows = flows,
            reconciliationRecords = recons,
            feeRules = feeRules.values.toList(),
            apiKeys = keys,
            settings = settings.values.toList(),
            priceSnapshots = snapshots,
            duplicateSkipped = ctx.duplicateSkipped,
            missingCoinSkipped = ctx.missingSkipped,
            missingCoinIds = ctx.missing.toList(),
        )
    }

    /** 交易：uuid → 交易所+订单号（v1 无第三级输入面，见类 KDoc）。 */
    private fun planTransactions(
        rows: List<CproTransaction>,
        existing: ExistingKeys,
        ctx: PlanContext,
    ): List<CproTransaction> {
        val seenUuids = HashSet<String>()
        val seenOrderKeys = HashSet<String>()
        val inserts = ArrayList<CproTransaction>()
        for (tx in rows) {
            val orderKey = txOrderKey(tx)
            val seenInPayload = tx.uuid in seenUuids || orderKey in seenOrderKeys
            val seenInLocal = !ctx.fullOverwrite &&
                (tx.uuid in existing.txUuids || (tx.exchangeOrderId != null && orderKey in existing.txOrderKeys))
            val missingLegs = listOf(tx.baseCgId, tx.quoteCgId).filter { it !in ctx.resolvable }
            when {
                seenInPayload || seenInLocal -> {
                    seenUuids += tx.uuid
                    seenOrderKeys += orderKey
                    ctx.duplicateSkipped++
                }
                missingLegs.isNotEmpty() -> {
                    ctx.missing.addAll(missingLegs)
                    ctx.missingSkipped++
                }
                else -> {
                    seenUuids += tx.uuid
                    seenOrderKeys += orderKey
                    inserts += tx
                }
            }
        }
        return inserts
    }

    /** 资金流水：uuid 去重。 */
    private fun planFlows(
        rows: List<CproCapitalFlow>,
        existing: ExistingKeys,
        ctx: PlanContext,
    ): List<CproCapitalFlow> {
        val seen = HashSet<String>()
        val inserts = ArrayList<CproCapitalFlow>()
        for (flow in rows) {
            val seenInLocal = !ctx.fullOverwrite && flow.uuid in existing.flowUuids
            when {
                flow.uuid in seen || seenInLocal -> {
                    seen += flow.uuid
                    ctx.duplicateSkipped++
                }
                flow.coinCgId !in ctx.resolvable -> {
                    ctx.missing += flow.coinCgId
                    ctx.missingSkipped++
                }
                else -> {
                    seen += flow.uuid
                    inserts += flow
                }
            }
        }
        return inserts
    }

    /** 校准记录：uuid 去重。 */
    private fun planRecons(
        rows: List<CproReconciliation>,
        existing: ExistingKeys,
        ctx: PlanContext,
    ): List<CproReconciliation> {
        val seen = HashSet<String>()
        val inserts = ArrayList<CproReconciliation>()
        for (recon in rows) {
            val seenInLocal = !ctx.fullOverwrite && recon.uuid in existing.reconUuids
            when {
                recon.uuid in seen || seenInLocal -> {
                    seen += recon.uuid
                    ctx.duplicateSkipped++
                }
                recon.coinCgId !in ctx.resolvable -> {
                    ctx.missing += recon.coinCgId
                    ctx.missingSkipped++
                }
                else -> {
                    seen += recon.uuid
                    inserts += recon
                }
            }
        }
        return inserts
    }

    /** API 密钥：(exchange_name + 别名) 命中跳过（本地不覆盖）。 */
    private fun planApiKeys(
        rows: List<CproApiKey>,
        existing: ExistingKeys,
        ctx: PlanContext,
    ): List<CproApiKey> {
        val seen = HashSet<String>()
        val inserts = ArrayList<CproApiKey>()
        for (key in rows) {
            val dedupKey = key.exchangeName.trim().uppercase() + "|" + key.name
            val seenInLocal = !ctx.fullOverwrite && dedupKey in existing.apiKeyKeys
            if (dedupKey in seen || seenInLocal) {
                ctx.duplicateSkipped++
            } else {
                seen += dedupKey
                inserts += key
            }
        }
        return inserts
    }

    /** 快照：币种+法币+小时桶幂等（本地已有同桶行保留本地，两模式一致）；时间非法 = 损坏行跳过。 */
    private fun planSnapshots(
        rows: List<CproSnapshot>,
        existing: ExistingKeys,
        ctx: PlanContext,
    ): List<CproSnapshot> {
        val seen = HashSet<String>()
        val inserts = ArrayList<CproSnapshot>()
        for (snapshot in rows) {
            val bucket = snapshotBucket(snapshot)
            when {
                bucket == null -> ctx.missingSkipped++
                bucket in seen || bucket in existing.snapshotBuckets -> ctx.duplicateSkipped++
                snapshot.coinCgId !in ctx.resolvable -> {
                    ctx.missing += snapshot.coinCgId
                    ctx.missingSkipped++
                }
                else -> {
                    seen += bucket
                    inserts += snapshot
                }
            }
        }
        return inserts
    }

    private fun txOrderKey(tx: CproTransaction): String =
        tx.exchange.trim().uppercase() + "|" + (tx.exchangeOrderId ?: "")

    /** 快照幂等桶键（同 M006「同小时末条」桶口径）；时间非法 = 无法定位桶，按损坏行跳过。 */
    internal fun snapshotBucket(snapshot: CproSnapshot): String? {
        val at = parseInstant(snapshot.recordedAt) ?: return null
        val epochHour = at.epochSecond / 3600
        return snapshot.coinCgId + "|" + snapshot.fiat.uppercase() + "|" + epochHour
    }

    private fun parseInstant(text: String): Instant? = try {
        OffsetDateTime.parse(text).toInstant()
    } catch (e: DateTimeParseException) {
        try {
            Instant.parse(text)
        } catch (e2: DateTimeParseException) {
            null
        }
    }
}
