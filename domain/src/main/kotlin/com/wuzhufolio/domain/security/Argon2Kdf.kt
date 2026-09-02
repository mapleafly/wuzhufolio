package com.wuzhufolio.domain.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.charset.StandardCharsets

/**
 * Argon2id KDF（ADR-002 §2，PRD 故事 5.1-2）：账户密码 -> KEK（32 字节）。
 *
 * - 实现：BouncyCastle Argon2BytesGenerator（纯 JVM，无原生绑定，三平台行为一致）；
 * - 输出 32 字节与 AES-256-GCM 对齐；salt 16 字节（ADR-002 §2）；
 * - 密码以 UTF-8 编码为字节后派生；内部拷贝 finally 清零。传入的 CharArray 生命周期由调用方管理（登出即擦）。
 */
object Argon2Kdf {

    const val OUTPUT_BYTES = 32
    const val SALT_BYTES = 16

    /** 派生 KEK。调用方负责随后擦除输入密码（Zeroization.wipe）。 */
    fun deriveKek(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        require(params.algorithm == KdfParams.Algorithm.ARGON2ID) { "only argon2id is supported" }
        require(salt.size == SALT_BYTES) { "argon2id salt must be exactly $SALT_BYTES bytes" }
        require(password.isNotEmpty()) { "password must not be empty" }

        val passwordBytes = String(password).toByteArray(StandardCharsets.UTF_8)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(params.iterations)
                .withMemoryAsKB(params.memoryKiB)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(parameters)
            val output = ByteArray(OUTPUT_BYTES)
            generator.generateBytes(passwordBytes, output)
            return output
        } finally {
            passwordBytes.fill(0)
        }
    }
}
