package com.wuzhufolio.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .cpro 备份格式模型（M9 · T9.1 · ADR-005「明文头部 + AES-256-GCM 加密 JSON 载荷」·
 * 共享规范 §8 / PRD 故事 5.2）。
 *
 * 字段命名与 ADR-005 §1 逐字对齐（snake_case @SerialName）；金额一律十进制串（勘误链同
 * M006/M009/M010/M011——SQLite NUMERIC 浮点截断风险，模块记录 M5 §5）；时间一律 UTC ISO-8601 文本。
 *
 * 币种引用口径：载荷内**不存本地 coins.id 自增主键**（跨设备/跨库不可移植），统一存 **cg_id**
 * （CoinGecko 稳定 id，共享规范 §6「币种标识」）——导入时经本地币种目录解析为行主键；
 * 目录缺失的币种行跳过并计数（恢复摘要列出，目录刷新后可重导）。
 *
 * 内容范围（ADR-005 §3）：transactions / capital_flows / reconciliation_records / fee_rules /
 * api_keys（凭证先解密入载荷）/ 账户级 settings / 本账户涉及币种的 price_snapshots；
 * **不含 accounts 任何字段、全局设置、coins/exchange_coin_map 全局公共表；
 * 行情 Key（CG/CMC）属设备级秘密（ADR-002 §2.1 方案甲），不随备份导出**。
 */

/** 明文头部（未加密 JSON 单行；恢复前仅展示此摘要——PRD 故事 5.2-5）。 */
@Serializable
data class CproHeader(
    @SerialName("format_version") val formatVersion: Int,
    @SerialName("app_version") val appVersion: String,
    /** 导出时刻（UTC ISO-8601）。 */
    @SerialName("exported_at") val exportedAt: String,
    val counts: CproCounts,
    val range: CproRange,
    val kdf: CproKdf,
    val cipher: String,
)

/** 各表记录条数（头部摘要展示 + 一致性参考）。 */
@Serializable
data class CproCounts(
    val transactions: Int,
    @SerialName("capital_flows") val capitalFlows: Int,
    @SerialName("reconciliation_records") val reconciliationRecords: Int,
    @SerialName("fee_rules") val feeRules: Int,
    @SerialName("api_keys") val apiKeys: Int,
    val settings: Int,
    @SerialName("price_snapshots") val priceSnapshots: Int,
) {
    companion object {
        val EMPTY = CproCounts(0, 0, 0, 0, 0, 0, 0)
    }
}

/** 业务数据时间范围（交易时间/流水时间/校准时刻/快照时刻的最小最大值；空备份为 null）。 */
@Serializable
data class CproRange(
    @SerialName("min_time") val minTime: String? = null,
    @SerialName("max_time") val maxTime: String? = null,
)

/** 备份密码 KDF 参数（salt 以 hex 入头部——salt 非秘密，PRD 头部摘要口径）。 */
@Serializable
data class CproKdf(
    val alg: String,
    /** 16 字节随机盐，hex 编码。 */
    val salt: String,
    val m: Int,
    val t: Int,
    val p: Int,
)

/** 加密载荷明文（JSON）：meta + records。 */
@Serializable
data class CproPayload(
    val meta: CproMeta,
    val records: CproRecords,
)

@Serializable
data class CproMeta(
    @SerialName("exported_at") val exportedAt: String,
    /** 导出时基础法币（提示性；恢复端沿用自身设置）。 */
    @SerialName("base_fiat") val baseFiat: String,
)

@Serializable
data class CproRecords(
    val transactions: List<CproTransaction> = emptyList(),
    @SerialName("capital_flows") val capitalFlows: List<CproCapitalFlow> = emptyList(),
    @SerialName("reconciliation_records") val reconciliationRecords: List<CproReconciliation> = emptyList(),
    @SerialName("fee_rules") val feeRules: List<CproFeeRule> = emptyList(),
    @SerialName("api_keys") val apiKeys: List<CproApiKey> = emptyList(),
    val settings: List<CproSetting> = emptyList(),
    @SerialName("price_snapshots") val priceSnapshots: List<CproSnapshot> = emptyList(),
)

/** 交易记录（uuid/exchange_order_id 为去重键，PRD 5.2-6 三级去重前两级）。 */
@Serializable
data class CproTransaction(
    val uuid: String,
    val exchange: String,
    @SerialName("exchange_order_id") val exchangeOrderId: String? = null,
    /** 展示交易对（保存时冻结形态）。 */
    val pair: String,
    @SerialName("base_cg_id") val baseCgId: String,
    @SerialName("quote_cg_id") val quoteCgId: String,
    /** BUY / SELL。 */
    val type: String,
    val price: String,
    val quantity: String,
    val fee: String,
    @SerialName("fee_currency") val feeCurrency: String? = null,
    @SerialName("transaction_time") val transactionTime: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    /** Manual / CSV / BINANCE API（审计溯源）。 */
    val source: String,
    @SerialName("price_status") val priceStatus: String,
)

/** 资金流水（增资/撤资；uuid 去重键）。 */
@Serializable
data class CproCapitalFlow(
    val uuid: String,
    /** DEPOSIT / WITHDRAWAL。 */
    val type: String,
    val amount: String,
    /** 记录时折算快照（审计口径）。 */
    @SerialName("base_amount") val baseAmount: String,
    val currency: String,
    @SerialName("coin_cg_id") val coinCgId: String,
    @SerialName("flow_time") val flowTime: String,
    @SerialName("source_dest") val sourceDest: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("price_status") val priceStatus: String,
)

/** 持仓校准记录（锚点事件；uuid 去重键；差额/折算按记录值固定）。 */
@Serializable
data class CproReconciliation(
    val uuid: String,
    val symbol: String,
    @SerialName("coin_cg_id") val coinCgId: String,
    val exchange: String,
    @SerialName("local_quantity") val localQuantity: String,
    @SerialName("exchange_quantity") val exchangeQuantity: String,
    val delta: String,
    @SerialName("base_amount") val baseAmount: String,
    @SerialName("created_at") val createdAt: String,
)

/** 手续费费率规则（去重键 = 交易所；exchange 空串 = 全局默认；备份优先覆盖，PRD 5.2-6）。 */
@Serializable
data class CproFeeRule(
    val exchange: String,
    @SerialName("buy_rate") val buyRate: String,
    @SerialName("sell_rate") val sellRate: String,
)

/**
 * API 密钥（凭证**明文**入载荷——导出时先解密、导入时以目标账户 DEK 重加密，ADR-005 §2；
 * 载荷整体 AES-256-GCM 加密，明文只存在于解密后的瞬时内存）。去重键 = (exchange_name + name)。
 */
@Serializable
data class CproApiKey(
    val name: String,
    @SerialName("exchange_name") val exchangeName: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("secret_key") val secretKey: String,
    val passphrase: String? = null,
    val extra: String? = null,
    @SerialName("last_sync_time") val lastSyncTime: String? = null,
    val status: String,
)

/** 账户级设置（按 key 逐项覆盖，备份优先——PRD 5.2-6；全局设置不进备份）。 */
@Serializable
data class CproSetting(val key: String, val value: String)

/** 价格快照（幂等键 = 币种 + 法币 + 小时桶时刻，PRD 5.2-6「按 币种+法币+时间 幂等合并」）。 */
@Serializable
data class CproSnapshot(
    @SerialName("coin_cg_id") val coinCgId: String,
    val fiat: String,
    val price: String,
    @SerialName("price_source") val priceSource: String,
    @SerialName("recorded_at") val recordedAt: String,
)
