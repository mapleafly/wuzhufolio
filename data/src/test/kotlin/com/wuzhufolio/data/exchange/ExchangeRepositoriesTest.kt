package com.wuzhufolio.data.exchange

import com.wuzhufolio.data.exchange.ApiKeyCiphers
import com.wuzhufolio.domain.exchange.DuplicateApiKeyNameException
import com.wuzhufolio.domain.exchange.SyncStatus
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M6 数据层仓库验收：api_keys 字段加密往返（DEK/AAD）、别名唯一、sync_logs 写读、
 * transactions 去重（(exchange, orderId)）与 pair 枚举。
 */
class ExchangeRepositoriesTest {

    private val env = ExchangeTestEnv()

    @AfterTest
    fun tearDown() = env.close()

    private fun cipher(
        accountId: Int,
        dek: ByteArray,
        apiKey: String,
        secret: String,
    ): (Int) -> ApiKeyCiphers = { rowId ->
        val id = accountId.toString()
        ApiKeyCiphers(
            apiKey = env.crypto.encryptField(apiKey, dek, id, rowId.toString(), "api_key"),
            secretKey = env.crypto.encryptField(secret, dek, id, rowId.toString(), "secret_key"),
        )
    }

    @Test
    fun `api key save roundtrips ciphertext and decrypts with account dek`() {
        val session = env.loginAccount("alice")
        val record = env.apiKeys.create(session.account.id, "币安主号", "BINANCE",
            cipher(session.account.id, session.dek, "ak-123", "sk-456"))
        assertTrue(record.id > 0)
        assertTrue(!record.apiKeyCipher.contains("ak-123"), "密文不得含明文")
        assertTrue(record.apiKeyCipher.startsWith("v1"))
        val decryptedKey = env.crypto.decryptField(record.apiKeyCipher, session.dek,
            session.account.id.toString(), record.id.toString(), "api_key")
        assertEquals("ak-123", decryptedKey)
        val listed = env.apiKeys.list(session.account.id)
        assertEquals(1, listed.size)
        assertEquals("BINANCE", listed[0].exchangeName)
    }

    @Test
    fun `duplicate alias within same exchange is rejected`() {
        val session = env.loginAccount()
        env.apiKeys.create(session.account.id, "主号", "BINANCE",
            cipher(session.account.id, session.dek, "a", "b"))
        assertFailsWith<DuplicateApiKeyNameException> {
            env.apiKeys.create(session.account.id, "主号", "BINANCE",
                cipher(session.account.id, session.dek, "c", "d"))
        }
        val other = env.apiKeys.create(session.account.id, "主号", "COINBASE",
            cipher(session.account.id, session.dek, "e", "f"))
        assertNotNull(other)
    }

    @Test
    fun `sync logs append and recent list keeps order`() {
        val session = env.loginAccount()
        val key = env.apiKeys.create(session.account.id, "主号", "BINANCE",
            cipher(session.account.id, session.dek, "a", "b"))
        env.syncLogs.append(session.account.id, key.id, SyncStatus.OK, 3, "同步成功 · 新增 3")
        env.syncLogs.append(session.account.id, key.id, SyncStatus.FAILED, 0, "同步失败：Binance API 密钥已失效")
        val recent = env.syncLogs.recent(session.account.id, 10)
        assertEquals(2, recent.size)
        assertTrue(recent[0].message.contains("同步失败"))
        assertTrue(recent[1].message.contains("同步成功"))
    }

    @Test
    fun `transactions dedupe on exchange and order id and enumerates synced pairs`() {
        val session = env.loginAccount()
        val btcId = kotlinx.coroutines.runBlocking { env.catalog.getBySymbol("BTC").single().id }.toInt()
        val usdtId = kotlinx.coroutines.runBlocking { env.catalog.getBySymbol("USDT").single().id }.toInt()
        val row = ImportedTradeRow(
            accountId = session.account.id,
            exchange = "BINANCE",
            exchangeOrderId = "101",
            pair = "BTC/USDT",
            baseCoinId = btcId,
            quoteCoinId = usdtId,
            type = "BUY",
            price = BigDecimal("50000"),
            quantity = BigDecimal("0.5"),
            fee = BigDecimal("0.0001"),
            feeCurrency = "BTC",
            transactionTime = Instant.parse("2026-09-01T10:00:00Z"),
        )
        assertTrue(env.transactions.insertIfAbsent(row))
        val dup = env.transactions.insertIfAbsent(row.copy(price = BigDecimal("99999")))
        assertTrue(!dup, "同订单号重复行必须被去重")
        val count = env.transactions.countByAccount(session.account.id)
        assertEquals(1, count)
        val pairs = env.transactions.syncedPairs(session.account.id, "BINANCE")
        assertEquals(1, pairs.size)
        assertEquals("BTC/USDT", pairs[0].pair)
        assertEquals(101L, pairs[0].maxOrderId)
    }

    @Test
    fun `remove key shrinks the list`() {
        val session = env.loginAccount()
        val record = env.apiKeys.create(session.account.id, "temp", "BINANCE",
            cipher(session.account.id, session.dek, "a", "b"))
        env.apiKeys.remove(session.account.id, record.id)
        assertTrue(env.apiKeys.list(session.account.id).isEmpty())
    }
}
