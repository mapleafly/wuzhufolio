package com.wuzhufolio.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * CproCodec 单测（M9 · T9.1 验收「导出→导入往返无损；错误密码失败」+ ADR-005 §1/§2）。
 */
class CproCodecTest {

    private fun sampleHeader(saltHex: String): CproHeader = CproHeader(
        formatVersion = CproCodec.FORMAT_VERSION,
        appVersion = "test",
        exportedAt = "2026-09-09T00:00:00Z",
        counts = CproCounts(2, 1, 0, 1, 1, 2, 3),
        range = CproRange("2025-01-01T00:00:00Z", "2025-06-01T00:00:00Z"),
        kdf = CproKdf(CproCodec.KDF_ALG, saltHex, 1024, 1, 1),
        cipher = CproCodec.CIPHER,
    )

    private fun samplePayload(): CproPayload = CproPayload(
        meta = CproMeta("2026-09-09T00:00:00Z", "USD"),
        records = CproRecords(
            transactions = listOf(
                CproTransaction(
                    uuid = "u-1",
                    exchange = "BINANCE",
                    exchangeOrderId = "o-1",
                    pair = "BTC/USDT",
                    baseCgId = "bitcoin",
                    quoteCgId = "tether",
                    type = "BUY",
                    price = "50000",
                    quantity = "0.1",
                    fee = "5",
                    feeCurrency = "USDT",
                    transactionTime = "2025-01-01T00:00:00Z",
                    notes = "备注,含逗号",
                    createdAt = "2025-01-02T00:00:00Z",
                    source = "Manual",
                    priceStatus = "OK",
                ),
            ),
            apiKeys = listOf(
                CproApiKey(
                    name = "主号",
                    exchangeName = "BINANCE",
                    apiKey = "AKIA-明文",
                    secretKey = "SECRET-明文",
                    passphrase = "p@ss",
                    extra = null,
                    lastSyncTime = "2025-02-01T00:00:00Z",
                    status = "OK",
                ),
            ),
            priceSnapshots = listOf(
                CproSnapshot("bitcoin", "USD", "50000.5", "coingecko", "2025-01-01T00:00:00Z"),
            ),
        ),
    )

    @Test
    fun `roundtrip is lossless`() {
        val header = sampleHeader(CproCodec.hex(ByteArray(16) { it.toByte() }))
        val payload = samplePayload()
        val bytes = CproCodec.encode(header, payload, "Strong-Pass-1".toCharArray())

        val decoded = CproCodec.decode(bytes, "Strong-Pass-1".toCharArray())
        assertEquals(payload, decoded, "往返应无损（含中文/逗号字段与凭证明文）")
        val parsedHeader = CproCodec.parseHeader(bytes)
        assertEquals(header, parsedHeader)
    }

    @Test
    fun `wrong password fails with typed reason`() {
        val header = sampleHeader(CproCodec.hex(ByteArray(16) { it.toByte() }))
        val bytes = CproCodec.encode(header, samplePayload(), "Correct-Pass-1".toCharArray())
        val e = assertFailsWith<CproDecodeException> {
            CproCodec.decode(bytes, "Wrong-Pass-1".toCharArray())
        }
        assertEquals(CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED, e.reason)
    }

    @Test
    fun `header tampering is rejected via AAD binding`() {
        val header = sampleHeader(CproCodec.hex(ByteArray(16) { it.toByte() }))
        val bytes = CproCodec.encode(header, samplePayload(), "Strong-Pass-1".toCharArray())
        // 篡改头部（app_version 替换为更长文本，保持合法 JSON）→ GCM 认证失败
        val tamperedHeader = header.copy(appVersion = "tampered-version")
        val headerJson = kotlinx.serialization.json.Json
            .encodeToString(CproHeader.serializer(), tamperedHeader)
            .toByteArray()
        val bodyStart = bytes.indexOf('\n'.code.toByte()) + 1
        val tampered = headerJson + byteArrayOf('\n'.code.toByte()) + bytes.copyOfRange(bodyStart, bytes.size)
        val e = assertFailsWith<CproDecodeException> {
            CproCodec.decode(tampered, "Strong-Pass-1".toCharArray())
        }
        assertEquals(CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED, e.reason)
    }

    @Test
    fun `newer format version is rejected as unsupported`() {
        val header = sampleHeader(CproCodec.hex(ByteArray(16) { it.toByte() })).copy(formatVersion = 99)
        val bytes = CproCodec.encode(header, samplePayload(), "Strong-Pass-1".toCharArray())
        val e = assertFailsWith<CproDecodeException> {
            CproCodec.decode(bytes, "Strong-Pass-1".toCharArray())
        }
        assertEquals(CproDecodeException.Reason.UNSUPPORTED_FORMAT, e.reason)
    }

    @Test
    fun `malformed file is rejected without password attempt`() {
        val e1 = assertFailsWith<CproDecodeException> {
            CproCodec.parseHeader("not a cpro file".toByteArray())
        }
        assertEquals(CproDecodeException.Reason.MALFORMED_FILE, e1.reason)
        val e2 = assertFailsWith<CproDecodeException> {
            CproCodec.decode(ByteArray(20) { 1 }, "Strong-Pass-1".toCharArray())
        }
        assertEquals(CproDecodeException.Reason.MALFORMED_FILE, e2.reason)
    }

    @Test
    fun `header json is single line plaintext`() {
        val header = sampleHeader(CproCodec.hex(ByteArray(16) { 0xAB.toByte() }))
        val bytes = CproCodec.encode(header, samplePayload(), "Strong-Pass-1".toCharArray())
        val headerText = String(bytes.copyOfRange(0, bytes.indexOf('\n'.code.toByte())))
        assertTrue(headerText.contains("\"format_version\":1"), "头部为明文 JSON（PRD 5.2-2 摘要可读）")
        assertTrue('\n' !in headerText, "头部单行（分隔符唯一）")
        assertTrue(headerText.contains("\"cipher\":\"aes-256-gcm\""))
    }
}
