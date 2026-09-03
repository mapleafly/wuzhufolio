package com.wuzhufolio.domain.accounts

/**
 * 账户口令策略（M2 定稿；PRD 1.1-3 无定量规则 → 按 P1 原型 pwScore 口径定量，供登录/创建/改密/备份密码共用）：
 * - 最低门槛（创建/改密拦截）：长度 >= 8 且至少含一个字母与一个数字；
 * - 强度三档（弱/中/强，仅指示不阻止，中/强高于门槛）。
 */
object AccountPolicy {

    const val MIN_LENGTH = 8
    const val MEDIUM_LENGTH = 10
    const val STRONG_LENGTH = 12

    enum class Strength { WEAK, MEDIUM, STRONG }

    /** 强度档（不校验门槛，纯指示）。 */
    fun strength(password: String): Strength {
        val len = password.length
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasLetter = hasUpper || hasLower
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return when {
            len >= STRONG_LENGTH && hasUpper && hasLower && hasDigit && hasSpecial -> Strength.STRONG
            len >= MEDIUM_LENGTH && hasLetter && hasDigit && hasSpecial -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }

    /** 最低门槛（PRD 1.1-3：最小长度、包含数字/字母）。 */
    fun meetsMinimum(password: String): Boolean {
        if (password.length < MIN_LENGTH) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    /** 用户名非空（原型/9.1 校验：不能为空且唯一）。 */
    fun isValidUsername(username: String): Boolean = username.isNotBlank()
}
