package com.wuzhufolio.domain.security

import java.util.Locale

/**
 * T1.3 Argon2id benchmark fixture (not an assertion test; run: ./gradlew :domain:kdfBenchmark).
 *
 * Target (PRD §12): login KDF on a 4GB dual-core low-end machine <= 2s. This box is a 16-thread
 * desktop reference machine; numbers are used to pick default params and set the recheck bar.
 * Login = one derivation. Final freeze record: docs/dev/modules/M1.md.
 */
fun main() {
    val salt = ByteArray(Argon2Kdf.SALT_BYTES) { it.toByte() }
    val password = "benchmark-password-ZB-2026".toCharArray()
    val candidates = listOf(
        KdfParams.DEFAULT,
        KdfParams(memoryKiB = 64 * 1024, iterations = 2, parallelism = 1),
        KdfParams(memoryKiB = 48 * 1024, iterations = 3, parallelism = 1),
        KdfParams(memoryKiB = 32 * 1024, iterations = 3, parallelism = 1),
        KdfParams(memoryKiB = 32 * 1024, iterations = 2, parallelism = 1),
        KdfParams.OWASP_MINIMUM,
    )

    println("== Argon2id KDF benchmark (BouncyCastle pure JVM) ==")
    println("jdk=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name") +
        " arch=" + System.getProperty("os.arch") + " cpus=" + Runtime.getRuntime().availableProcessors())
    Zeroization.wipe(Argon2Kdf.deriveKek(password, salt, KdfParams.DEFAULT)) // JIT warmup (untimed)

    val results = candidates.associateWith { bestOf3Millis(salt, password, it) }
    val owasp = results.getValue(KdfParams.OWASP_MINIMUM)
    println("m(KiB)  t  p   | best-ms | x OWASP-min")
    for ((params, ms) in results) {
        println(
            String.format(Locale.ROOT, "%6d %2d %2d   | %7.1f | %4.1f",
                params.memoryKiB, params.iterations, params.parallelism, ms, ms / owasp),
        )
    }
    println("note: login = 1 derivation; 4GB dual-core target machine recheck listed as M2 gate item")
}

private fun bestOf3Millis(salt: ByteArray, password: CharArray, params: KdfParams): Double {
    var best = Double.MAX_VALUE
    repeat(3) {
        val start = System.nanoTime()
        val kek = Argon2Kdf.deriveKek(password, salt, params)
        val ms = (System.nanoTime() - start) / 1_000_000.0
        Zeroization.wipe(kek)
        if (ms < best) best = ms
    }
    return best
}
