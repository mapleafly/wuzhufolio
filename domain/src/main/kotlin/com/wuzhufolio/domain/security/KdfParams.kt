package com.wuzhufolio.domain.security

/**
 * Argon2id 派生参数（ADR-002 §2：初值 m=64MiB/t=3/p=1，T1.3 基准校准后冻结；PRD §12 登录解密 <=2s）。
 *
 * 以文本存 accounts.kdf_params（data-model §2.1），格式为稳定 JSON 子集：
 * [KdfParams.toStorageString] 输出 {"alg":"argon2id","m":<memoryKiB>,"t":<iterations>,"p":<parallelism>}
 * （M9 .cpro 载荷内同样承载该字符串；解析失败视为数据损坏，不允许静默降级）。
 */
data class KdfParams(
    val algorithm: Algorithm = Algorithm.ARGON2ID,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    init {
        require(memoryKiB.toLong() >= 8L * parallelism) { "argon2 memory must be at least 8 KiB per lane" }
        require(iterations >= 1) { "argon2 iterations must be >= 1" }
        require(parallelism >= 1) { "argon2 parallelism must be >= 1" }
    }

    enum class Algorithm(val storageValue: String) {
        ARGON2ID("argon2id"),
    }

    /** 稳定序列化（与 M2 accounts.kdf_params / M9 .cpro 载荷共用）。 */
    fun toStorageString(): String =
        "{\"alg\":\"" + algorithm.storageValue + "\",\"m\":" + memoryKiB +
            ",\"t\":" + iterations + ",\"p\":" + parallelism + "}"

    companion object {
        /** OWASP Argon2id 最低建议值（m=19MiB/t=2/p=1）——校准不达标时的降级目标（ADR-002 §2 风险表）。 */
        val OWASP_MINIMUM = KdfParams(memoryKiB = 19 * 1024, iterations = 2, parallelism = 1)

        /** 出厂默认（T1.3 实测后冻结，见 docs/dev/modules/M1.md 校准记录）。 */
        val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)

        private val STORAGE_PATTERN =
            Regex("""^\{"alg":"([a-z0-9]+)","m":(\d+),"t":(\d+),"p":(\d+)\}$""")

        /** 严格解析存储串；格式/取值非法一律抛错（不允许静默改用默认参数）。 */
        fun fromStorageString(value: String): KdfParams {
            val match = STORAGE_PATTERN.matchEntire(value.trim()) ?: return invalid(value)
            val algorithm = algorithmOf(match.groupValues[1], value)
            return KdfParams(
                algorithm = algorithm,
                memoryKiB = intField("m", match.groupValues[2], value),
                iterations = intField("t", match.groupValues[3], value),
                parallelism = intField("p", match.groupValues[4], value),
            )
        }

        private fun invalid(value: String): Nothing =
            throw IllegalArgumentException("unsupported or malformed kdf_params storage string: " + value)

        private fun algorithmOf(raw: String, original: String): Algorithm =
            Algorithm.entries.firstOrNull { it.storageValue == raw }
                ?: throw IllegalArgumentException("unknown kdf algorithm in kdf_params: " + original)

        private fun intField(name: String, raw: String, original: String): Int =
            raw.toIntOrNull()
                ?: throw IllegalArgumentException("bad " + name + " in kdf_params: " + original)
    }
}
