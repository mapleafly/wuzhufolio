package com.wuzhufolio.data.market

import com.wuzhufolio.data.exchange.ApiKeyRepository
import com.wuzhufolio.data.ledger.LedgerTestEnv
import com.wuzhufolio.domain.engine.FlowKind
import com.wuzhufolio.domain.engine.Side
import com.wuzhufolio.domain.ledger.FundFilter
import com.wuzhufolio.domain.ledger.FundInput
import com.wuzhufolio.domain.ledger.TransactionInput
import com.wuzhufolio.domain.ledger.TxFilter
import com.wuzhufolio.domain.market.MarketWatchService
import com.wuzhufolio.domain.market.WatchImportFailReason
import com.wuzhufolio.domain.market.WatchImportResult
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * 自选清单**跨账户显式导入**实现守护（B 迭代 **B3** · `D48` · T15.5）——真实 SQLCipher 库 +
 * 既有账本/自选用例（**无 fake**）。
 *
 * 逐条对应 `WatchImportService` KDoc 的五条不变量：
 * 1. **仅复制自选清单本身**（交易 / 资金流水 / API 密钥零变化）；
 * 2. **必须显式**（服务层只接受「来源账户 id」这一显式入参；UI 侧的选择 + 二次确认由 `WatchImportUiTest` 守护）；
 * 3. **单向**——只写当前账户，**来源账户一字不改**（含**不触发** D41 的旧全局清单一次性认领）；
 * 4. 未登录 / 无其它账户 / 来源为空 / 全量重复 ⇒ **类型化结果 + 零写入**；部分重复 ⇒ **去重保序**；
 * 5. **账户级作用域**（既有 `ActiveSessionStore`）+ **零 schema / 零新增 settings 键**。
 *
 * 时间一律**相对 `now` 构造**（`AGENTS.md §7.3-8` / DEF-58：不得硬编码绝对时间基准）。
 */
class WatchImportServiceTest {

    private val now: Instant = Instant.now()

    private fun watch(e: LedgerTestEnv) = SettingsMarketWatchService(e.settings, e.catalog, e.sessions)

    private fun importer(e: LedgerTestEnv, w: SettingsMarketWatchService) = SettingsWatchImportService(
        settings = e.settings,
        catalog = e.catalog,
        sessions = e.sessions,
        accounts = e.accounts,
        watch = w,
    )

    /** 账户自选**原始存储行**（null = 从未写入过）。 */
    private fun storedRow(e: LedgerTestEnv, accountId: Int): String? =
        e.settings.getAccount(accountId, MarketWatchService.SETTINGS_KEY)

    /** 解析原始存储行（逐元素断言）。 */
    private fun decode(raw: String): List<String> = Json.decodeFromString<List<String>>(raw)

    private suspend fun LedgerTestEnv.transactions() = service.listTransactions(TxFilter())

    private suspend fun LedgerTestEnv.funds() = fundService.listFunds(FundFilter()).rows

    private fun apiKeyCount(e: LedgerTestEnv, accountId: Int) = ApiKeyRepository(e.gate).list(accountId).size

    /** 目录口径的展示名（与实现同源：symbol 由目录给出）。 */
    private suspend fun LedgerTestEnv.symbolsOf(cgIds: List<String>): List<String> =
        cgIds.map { catalog.getByCgId(it)!!.symbol }

    /** 给**当前**账户造一条真实增资 + 一笔真实买入 + 一个 API 密钥（用于「不涉其它数据」断言）。 */
    private suspend fun LedgerTestEnv.seedOtherData(tag: String) {
        fundService.saveFund(
            FundInput(
                kind = FlowKind.DEPOSIT,
                coinSymbol = "USDT",
                quantity = BigDecimal("10000"),
                time = now.minus(10, ChronoUnit.DAYS),
                notes = "real-deposit-$tag",
            ),
        )
        service.saveTransaction(
            TransactionInput(
                exchange = "BINANCE",
                baseSymbol = "BTC",
                quoteSymbol = "USDT",
                side = Side.BUY,
                price = BigDecimal("50000"),
                quantity = BigDecimal("0.1"),
                fee = BigDecimal.ZERO,
                feeCoinSymbol = "USDT",
                time = now.minus(5, ChronoUnit.DAYS),
                notes = "real-buy-$tag",
            ),
        )
        addKey(name = "key-$tag")
    }

    // ---- 不变量 1：仅复制自选清单本身 ----

    @Test
    fun `import copies only the watch list and leaves every other table untouched`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            e.seedOtherData("a")
            watch(e).addCoins(listOf("bitcoin", "ethereum"))
            // 切换前记录来源账户的「其它数据」行数（账本查询按当前会话过滤，故必须先取）
            val sourceTx = e.transactions().size
            val sourceFunds = e.funds().size
            val sourceKeys = apiKeyCount(e, accountA.account.id)
            val sourceRowBefore = storedRow(e, accountA.account.id)

            val accountB = e.login("account-b")
            watch(e).addCoin("dai")

            val result = importer(e, watch(e)).importFrom(accountA.account.id)

            assertEquals(
                WatchImportResult.Imported(added = 2, skipped = 1, sourceAccountId = accountA.account.id, sourceUsername = "account-a"),
                result,
                "来源 3 枚（tether/bitcoin/ethereum）中 bitcoin+ethereum 新增、tether 已在目标中跳过",
            )
            assertEquals(
                listOf("tether", "dai", "bitcoin", "ethereum"),
                decode(storedRow(e, accountB.account.id)!!),
                "来源中尚不存在的币按**来源序**追加在末尾（保序去重）",
            )
            // 目标账户：除自选外零数据
            assertEquals(0, e.transactions().size, "导入不得写入交易")
            assertEquals(0, e.funds().size, "导入不得写入资金流水")
            assertEquals(0, apiKeyCount(e, accountB.account.id), "导入不得写入 API 密钥")

            // 来源账户：其它数据与自选行**全部不变**
            e.sessions.set(accountA)
            assertEquals(sourceTx, e.transactions().size, "导入不得新增/删除来源的交易")
            assertEquals(sourceFunds, e.funds().size, "导入不得新增/删除来源的资金流水")
            assertEquals(sourceKeys, apiKeyCount(e, accountA.account.id), "导入不得触碰来源的 API 密钥")
            assertEquals(sourceRowBefore, storedRow(e, accountA.account.id), "来源自选行逐字节不变")
        }
    }

    // ---- 不变量 3：单向，只写当前账户 ----

    @Test
    fun `import writes only the current account and keeps every other account row byte identical`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin", "ethereum"))
            val accountC = e.login("account-c")
            watch(e).addCoins(listOf("dai"))
            val accountB = e.login("account-b")

            val sourceRowBefore = storedRow(e, accountA.account.id)
            val thirdRowBefore = storedRow(e, accountC.account.id)
            val result = importer(e, watch(e)).importFrom(accountA.account.id)

            assertTrue(result is WatchImportResult.Imported, "应导入成功，实际 $result")
            assertEquals(listOf("tether", "bitcoin", "ethereum"), decode(storedRow(e, accountB.account.id)!!))
            assertEquals(sourceRowBefore, storedRow(e, accountA.account.id), "来源账户行逐字节不变")
            assertEquals(thirdRowBefore, storedRow(e, accountC.account.id), "第三方账户行逐字节不变")
        }
    }

    @Test
    fun `import does not claim the legacy global list for the source account`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a") // 来源：**从未写入过**自选
            val accountB = e.login("account-b")
            // 目标账户自选**直接落库**（绕过自选服务 ⇒ 不触发 D41 认领），并模拟升级前的旧全局行
            e.settings.putAccount(accountB.account.id, MarketWatchService.SETTINGS_KEY, """["tether"]""")
            e.settings.putGlobal(MarketWatchService.SETTINGS_KEY, """["bitcoin","ethereum"]""")

            val importService = importer(e, watch(e))
            val summaries = importService.listOtherAccounts()
            assertEquals(1, summaries.size)
            assertTrue(summaries.single().defaultSeed, "来源未写入过 ⇒ 摘要为默认种子态")

            val result = importService.importFrom(accountA.account.id)

            assertEquals(WatchImportResult.AlreadyComplete(accountA.account.id, "account-a"), result)
            assertNull(
                storedRow(e, accountA.account.id),
                "**来源账户不得被认领**：只读摘要与导入路径都不能写来源账户的 watch.coins 行",
            )
            assertNull(
                e.settings.getGlobal("watch.migrated"),
                "来源侧读取不得置位迁移标记（置位即说明走错了 readStoredIds 路径）",
            )
        }
    }

    // ---- 不变量 4：类型化结果 + 零写入 ----

    @Test
    fun `import without a session is a typed failure and writes nothing`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin"))
            val sourceRowBefore = storedRow(e, accountA.account.id)
            e.sessions.clear()

            val importService = importer(e, watch(e))
            assertEquals(
                WatchImportResult.Failed(WatchImportFailReason.NO_SESSION),
                importService.importFrom(accountA.account.id),
            )
            assertTrue(importService.listOtherAccounts().isEmpty(), "未登录 ⇒ 摘要为空列表（UI 据此不渲染入口）")
            assertEquals(sourceRowBefore, storedRow(e, accountA.account.id), "未登录时零写入")
        }
    }

    @Test
    fun `import with no other account reports source not found and writes nothing`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            val importService = importer(e, watch(e))

            assertTrue(importService.listOtherAccounts().isEmpty(), "本机只有当前账户 ⇒ 无来源")
            assertEquals(
                WatchImportResult.Failed(WatchImportFailReason.SOURCE_NOT_FOUND),
                importService.importFrom(accountA.account.id),
                "把当前账户自身当作来源 ⇒ 类型化失败",
            )
            assertNull(storedRow(e, accountA.account.id), "零写入（默认种子态未被物化）")
        }
    }

    @Test
    fun `import from an empty source writes nothing`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            val watchA = watch(e)
            watchA.addCoin("bitcoin")
            watchA.removeCoin("bitcoin")
            watchA.removeCoin("tether") // 显式清空 ⇒ 存储为 []
            assertEquals(emptyList(), watchA.watchCoins(), "来源自选已清空")
            val sourceRowBefore = storedRow(e, accountA.account.id)

            val accountB = e.login("account-b")
            val result = importer(e, watch(e)).importFrom(accountA.account.id)

            assertEquals(WatchImportResult.NoSource(accountA.account.id, "account-a"), result)
            assertNull(storedRow(e, accountB.account.id), "来源为空 ⇒ 目标账户零写入")
            assertEquals(sourceRowBefore, storedRow(e, accountA.account.id))
        }
    }

    @Test
    fun `import of an already complete list is idempotent and writes nothing`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin", "ethereum"))
            val accountB = e.login("account-b")
            watch(e).addCoins(listOf("bitcoin", "ethereum"))
            val targetRowBefore = storedRow(e, accountB.account.id)

            val importService = importer(e, watch(e))
            assertEquals(
                WatchImportResult.AlreadyComplete(accountA.account.id, "account-a"),
                importService.importFrom(accountA.account.id),
                "全量重复 ⇒ 幂等结果",
            )
            assertEquals(targetRowBefore, storedRow(e, accountB.account.id), "全量重复 ⇒ 不写（行逐字节不变）")
            // 再导一次仍是幂等（不累积副作用）
            assertEquals(
                WatchImportResult.AlreadyComplete(accountA.account.id, "account-a"),
                importService.importFrom(accountA.account.id),
            )
            assertEquals(targetRowBefore, storedRow(e, accountB.account.id))
        }
    }

    @Test
    fun `partial duplicate import appends only the missing coins in source order`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin", "ethereum", "dai")) // ⇒ [tether, bitcoin, ethereum, dai]
            val accountB = e.login("account-b")
            watch(e).addCoins(listOf("ethereum", "tether")) // ⇒ [tether, ethereum]

            val result = importer(e, watch(e)).importFrom(accountA.account.id)

            assertEquals(
                WatchImportResult.Imported(added = 2, skipped = 2, sourceAccountId = accountA.account.id, sourceUsername = "account-a"),
                result,
                "部分重复：tether/ethereum 已存在（跳过 2），bitcoin/dai 新增（2）",
            )
            assertEquals(
                listOf("tether", "ethereum", "bitcoin", "dai"),
                decode(storedRow(e, accountB.account.id)!!),
                "既有顺序不变、新币按**来源序**追加末尾",
            )
        }
    }

    // ---- 只读摘要（不变量 2/3 的只读半边） ----

    @Test
    fun `source payload with blanks and duplicates is normalized before import`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            // 手工写入**异常载荷**（应用自身的写入路径不会产生：addCoins 已归一）——守护归一兜底
            e.settings.putAccount(
                accountA.account.id,
                MarketWatchService.SETTINGS_KEY,
                """["tether"," bitcoin ","bitcoin",""]""",
            )
            val sourceRowBefore = storedRow(e, accountA.account.id)
            val accountB = e.login("account-b")

            val importService = importer(e, watch(e))
            val summary = importService.listOtherAccounts().single()
            assertEquals(
                listOf("tether", "bitcoin"),
                summary.cgIds,
                "摘要按 D35 `addCoins` 同口径归一（trim → 去空串 → 去重）⇒「看到的 = 会导入的」",
            )

            val result = importService.importFrom(accountA.account.id)

            assertEquals(
                WatchImportResult.Imported(added = 1, skipped = 1, sourceAccountId = accountA.account.id, sourceUsername = "account-a"),
                result,
            )
            assertEquals(
                listOf("tether", "bitcoin"),
                decode(storedRow(e, accountB.account.id)!!),
                "空串与重复项不得带进当前账户",
            )
            assertEquals(sourceRowBefore, storedRow(e, accountA.account.id), "来源行一字不改（含其异常载荷）")
        }
    }

    @Test
    fun `list other accounts returns read only summaries and leaves the source untouched`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin", "ethereum"))
            val accountB = e.login("account-b")
            watch(e).addCoin("dai")
            val accountC = e.login("account-c") // 从未写入 ⇒ 默认种子态（且为当前账户）

            val sourceRowA = storedRow(e, accountA.account.id)
            val summaries = importer(e, watch(e)).listOtherAccounts()

            assertEquals(listOf(1, 2), summaries.map { it.accountId }, "按账户 id 升序，且不含当前账户")
            assertEquals(listOf("account-a", "account-b"), summaries.map { it.username })
            val summaryA = summaries.first { it.accountId == accountA.account.id }
            assertEquals(listOf("tether", "bitcoin", "ethereum"), summaryA.cgIds)
            assertEquals(e.symbolsOf(listOf("tether", "bitcoin", "ethereum")), summaryA.displayNames)
            assertEquals(3, summaryA.coinCount)
            assertTrue(!summaryA.defaultSeed && !summaryA.empty)
            assertEquals(
                listOf("tether", "dai"),
                summaries.first { it.accountId == accountB.account.id }.cgIds,
                "B 的自选为 [tether, dai]（存储序）",
            )
            assertTrue(summaries.none { it.accountId == accountC.account.id }, "当前账户不出现在来源列表")
            // 只读：来源行一字不改
            assertEquals(sourceRowA, storedRow(e, accountA.account.id))
            assertNull(storedRow(e, accountC.account.id), "只读摘要不得物化当前账户的自选行")
        }
    }

    @Test
    fun `list other accounts is empty without a session`() = runBlocking {
        LedgerTestEnv().use { e ->
            e.login("account-a")
            e.login("account-b")
            e.sessions.clear()
            assertTrue(importer(e, watch(e)).listOtherAccounts().isEmpty())
        }
    }

    // ---- 结构面：零 schema / 零新增 settings 键 ----

    @Test
    fun `import touches exactly one settings key and no other table`() = runBlocking {
        LedgerTestEnv().use { e ->
            val accountA = e.login("account-a")
            watch(e).addCoins(listOf("bitcoin"))
            val accountB = e.login("account-b")

            val keysBefore = e.settings.getAllGlobal().keys
            importer(e, watch(e)).importFrom(accountA.account.id)

            assertEquals(keysBefore, e.settings.getAllGlobal().keys, "导入不得新增/删除任何**全局**设置键")
            assertEquals(
                listOf("tether", "bitcoin"),
                decode(storedRow(e, accountB.account.id)!!),
                "唯一被写入的数据 = 当前账户的 watch.coins 行",
            )
            assertEquals(0, e.transactions().size)
            assertEquals(0, e.funds().size)
        }
    }
}
