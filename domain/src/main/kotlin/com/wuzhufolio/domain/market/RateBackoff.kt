package com.wuzhufolio.domain.market

/**
 * 429 指数退避纯规则（T5.1/ADR-003 §4：1s 起指数递增、上限 60s；
 * 调度层对连续失败序次应用，成功后归零）。抖动由宿主在 [delaySeconds] 基础上 ±20% 内随机施加。
 */
object RateBackoff {

    /** 连续第 [strike] 次失败后的等待秒数（strike ≤ 0 → 0；≥ 7 → 封顶 60s）。 */
    fun delaySeconds(strike: Int): Long = when {
        strike <= 0 -> 0L
        strike >= 7 -> 60L
        else -> 1L shl (strike - 1)
    }
}
