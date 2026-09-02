package com.wuzhufolio.domain.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** T1.2/KdfParams：存储串往返 + 非法输入拒绝（不允许静默降级参数）。 */
class KdfParamsTest {

    @Test
    fun `default matches frozen argon2id values and storage roundtrip`() {
        val default = KdfParams.DEFAULT
        assertEquals(KdfParams.Algorithm.ARGON2ID, default.algorithm)
        assertEquals(64 * 1024, default.memoryKiB)
        assertEquals(3, default.iterations)
        assertEquals(1, default.parallelism)
        val round = KdfParams.fromStorageString(default.toStorageString())
        assertEquals(default, round)
    }

    @Test
    fun `owasp minimum is valid and smaller than default`() {
        val min = KdfParams.OWASP_MINIMUM
        assertTrue(min.memoryKiB < KdfParams.DEFAULT.memoryKiB)
        assertEquals(19 * 1024, min.memoryKiB)
        assertEquals(2, min.iterations)
    }

    @Test
    fun `malformed storage strings are rejected`() {
        val samples = listOf(
            "not json",
            """{"alg":"argon2id","m":65536,"t":3}""",
            """{"alg":"scrypt","m":65536,"t":3,"p":1}""",
            """{"alg":"argon2id","m":-1,"t":3,"p":1}""",
            """{"alg":"argon2id","m":65536,"t":0,"p":1}""",
            """{"alg":"argon2id","m":abc,"t":3,"p":1}""",
            "",
        )
        for (sample in samples) {
            assertFailsWith<IllegalArgumentException> { KdfParams.fromStorageString(sample) }
        }
    }

    @Test
    fun `rejects below-minimum memory per lane`() {
        assertFailsWith<IllegalArgumentException> {
            KdfParams(memoryKiB = 7, iterations = 1, parallelism = 2)
        }
    }

    @Test
    fun `storage strings are canonical for equal params`() {
        assertEquals(
            KdfParams.DEFAULT.toStorageString(),
            KdfParams(memoryKiB = 65536, iterations = 3, parallelism = 1).toStorageString(),
        )
        assertNotEquals(KdfParams.DEFAULT.toStorageString(), KdfParams.OWASP_MINIMUM.toStorageString())
    }
}
