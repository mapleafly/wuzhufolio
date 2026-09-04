package com.wuzhufolio.data.market

import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.market.QuotaCallKind
import com.wuzhufolio.domain.market.QuotaPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * 月度额度账本（T5.4 · ADR-003 §4「本地持久化调用计数」）：settings 全局行 JSON 持久化，
 * 键 = [MarketConfig.KEY_QUOTA]，载荷 {"month":"yyyy-MM","calls":{"CURRENT":n,"HISTORY":n,"DIRECTORY":n}}；
 * 跨月自动归零（月度键翻转）。
 */
class SettingsQuotaLedger(
    private val settings: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 当前月计数（跨月自动清零语义由读侧判定——过期月按 0 返回并顺带重置存储）。 */
    fun counts(): Map<QuotaCallKind, Int> {
        val currentMonth = QuotaPolicy.monthKey(now())
        val raw = settings.getGlobal(MarketConfig.KEY_QUOTA)
        val (month, calls) = parse(raw)
        if (month != currentMonth) {
            calls.keys.forEach { calls[it] = 0 }
            persist(currentMonth, calls)
        }
        return calls
    }

    /** 记录一次调用（计数 +1）。 */
    fun record(kind: QuotaCallKind) {
        val calls = counts().toMutableMap()
        calls[kind] = (calls[kind] ?: 0) + 1
        persist(QuotaPolicy.monthKey(now()), calls)
    }

    /** 当前月已用百分比（domain QuotaPolicy 口径）。 */
    fun percentUsed(): Int = QuotaPolicy.percentUsed(counts())

    /** 是否达到 80% 降档线。 */
    fun downgraded(): Boolean = QuotaPolicy.isDowngraded(counts())

    private fun persist(month: String, calls: Map<QuotaCallKind, Int>) {
        val payload: JsonObject = buildJsonObject {
            put("month", month)
            put("calls", buildJsonObject {
                calls.forEach { (kind, count) -> put(kind.storageValue, count) }
            })
        }
        settings.putGlobal(MarketConfig.KEY_QUOTA, payload.toString())
    }

    @Suppress("SwallowedException") // 载荷损坏按空账本处理（自愈式重建），不中断刷新链路
    private fun parse(raw: String?): Pair<String, MutableMap<QuotaCallKind, Int>> {
        if (raw.isNullOrBlank()) return QuotaPolicy.monthKey(now()) to mutableMapOf()
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val month = root["month"]?.let { it.toString().trim('"') }.orEmpty()
            val calls = mutableMapOf<QuotaCallKind, Int>()
            (root["calls"]?.jsonObject ?: JsonObject(emptyMap())).forEach { (k, v) ->
                val kind = QuotaCallKind.fromStorage(k)
                val count = v.toString().toIntOrNull() ?: 0
                if (count > 0) calls[kind] = count
            }
            month to calls
        } catch (e: Exception) {
            QuotaPolicy.monthKey(now()) to mutableMapOf()
        }
    }
}
