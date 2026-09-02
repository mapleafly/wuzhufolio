package com.wuzhufolio.domain.security

/**
 * 密钥材料擦除（ADR-002 §2「密钥材料擦除：使用后清零」；PRD 故事 5.1-4 密码不落盘）。
 * JVM 下无强保证（GC 移动/拷贝不可避免），按威胁模型与主流桌面密码管理器同级处理。
 */
object Zeroization {

    /** 清零字节数组内容。 */
    fun wipe(vararg arrays: ByteArray) {
        for (array in arrays) array.fill(0)
    }

    /** 清零密码字符数组内容。 */
    fun wipe(chars: CharArray) {
        chars.fill('\u0000')
    }
}
