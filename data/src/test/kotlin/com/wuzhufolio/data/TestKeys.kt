package com.wuzhufolio.data

import java.security.SecureRandom

/** 测试用随机 32 字节库密钥（生产由 OS 钥匙串/降级文件提供，见 data.security）。 */
fun randomDbKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
