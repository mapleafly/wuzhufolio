package com.wuzhufolio.domain.catalog

/**
 * 法币归一纯规则（T3.3，共享规范 §3「法币角色与归一化规则」+ 法币方案决策分析 D.2）。
 *
 * 法币仅作计价单位、不作账本资产；法币计价交易对/手续费币种出现时：
 * - 有孪生稳定币（同币种稳定币）：1:1 映射联动持仓与余额校验（如 USD->USDT、EUR->EURC，
 *   [DEFAULT_TWIN_MAP]；M10 设置可经 [twinMap] 注入覆盖/扩展——映射表可配置，与增资默认映射共用一张表
 *   以保证账本守恒，法币方案决策分析 D.2）；
 * - 已知法币但无孪生映射：按"第三币种"语义处理（仅计入基础币种持仓与成本，不联动计价侧持仓）。
 *
 * 已知法币集合为交易场景常用 ISO 4217 代码（[KNOWN_FIAT_CODES]），未覆盖代码会按普通币种走目录解析；
 * 集合扩展随 M10 设置项落地时一并评估。
 */
class FiatNormalizer(
    private val twinMap: Map<String, String> = DEFAULT_TWIN_MAP,
) {

    /** ticker 归类（[CoinNames.normalizeCode] 归一后比较）。 */
    sealed interface Classification {
        /** 非法币代码：按普通币种走 coins 目录解析。 */
        data object NotFiat : Classification

        /** 有孪生稳定币：quote/费用按 1:1 联动（如 USD->USDT）。 */
        data class TwinMapped(val fiatCode: String, val stableSymbol: String) : Classification

        /** 已知法币但无孪生映射：第三币种语义（仅计基础币种）。 */
        data class ThirdCurrencyFiat(val fiatCode: String) : Classification
    }

    fun classify(rawCode: String): Classification {
        val code = CoinNames.normalizeCode(rawCode)
        return when {
            code.isEmpty() -> Classification.NotFiat
            twinMap.containsKey(code) -> Classification.TwinMapped(code, twinMap.getValue(code))
            code in KNOWN_FIAT_CODES -> Classification.ThirdCurrencyFiat(code)
            else -> Classification.NotFiat
        }
    }

    /** 孪生稳定币 symbol（无则 null；如 "USD" -> "USDT"）。 */
    fun twinStableSymbolOf(rawCode: String): String? = twinMap[CoinNames.normalizeCode(rawCode)]

    /** 是否为已知法币代码。 */
    fun isKnownFiat(rawCode: String): Boolean {
        val code = CoinNames.normalizeCode(rawCode)
        return code in twinMap || code in KNOWN_FIAT_CODES
    }

    companion object {
        /** 默认孪生映射（共享规范 §3 例：USD->USDT、EUR->EURC）。 */
        val DEFAULT_TWIN_MAP: Map<String, String> = linkedMapOf("USD" to "USDT", "EUR" to "EURC")

        /** 常用法币代码（ISO 4217 交易子集；无孪生映射者按第三币种语义）。 */
        val KNOWN_FIAT_CODES: Set<String> = setOf(
            "AED", "ARS", "AUD", "BDT", "BGN", "BHD", "BRL", "CAD", "CHF", "CLP", "CNY", "COP",
            "CZK", "DKK", "EGP", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK", "JPY",
            "KRW", "KWD", "KZT", "MXN", "MYR", "NGN", "NOK", "NZD", "PEN", "PHP", "PKR", "PLN",
            "RON", "RUB", "SAR", "SEK", "SGD", "THB", "TRY", "TWD", "UAH", "USD", "VND", "ZAR",
        )
    }
}
