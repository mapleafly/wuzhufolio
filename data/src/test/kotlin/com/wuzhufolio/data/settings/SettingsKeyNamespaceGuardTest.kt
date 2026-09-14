package com.wuzhufolio.data.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **settings 键命名空间守护**（P6 闭环 `docs/test/security-checklist.md` §7-5）。
 *
 * 背景：`settings` 表用 `account_id IS NULL` 区分**全局键**与**账户级键**，唯一索引
 * `UNIQUE(key, COALESCE(account_id,''))` 允许同名字符串在两个命名空间**并存**（`MigratorTest` 已固化该行为）。
 * 现状无冲突、无越权路径，但缺一条机器可验证的**代码层约定**：新增账户级键不得与任何全局键同名
 * （否则「账户级同名列」与全局列同时存在，读取方极易拿错语义——例如 `backup.reminder`（全局）与
 * `backup.last_at`（账户级）这类同前缀反向作用域的命名）。
 *
 * 三层守护（fail-closed：**扫不清就红**，不静默放过）：
 * 1. **源码扫描**：四个模块 `src/main` 的全部 settings 读写点 → 键集合（区分全局/账户级）；
 *    键来源 = 字符串字面量 或 常量符号（含 `Qualifier.CONST` 限定名解析）。
 * 2. **登记表**：扫到的每个键必须在 [globalKeys] / [accountKeys] 登记；反向也检查登记表里的键
 *    仍能在源码中找到（防登记表腐化）。
 * 3. **核心断言**：`GLOBAL_KEYS ∩ ACCOUNT_KEYS == ∅`。
 *
 * 若将来确需同名（或引入前缀约定），先按 `AGENTS.md §8` 变更控制定级——重命名既有账户级键需要
 * 数据迁移 + 旧 `.cpro` 兼容，命中 C2 红线，故本测试的默认方向是「禁止同名」而非引入前缀。
 */
class SettingsKeyNamespaceGuardTest {

    /** 全局键登记表（`account_id IS NULL`）——新增键必须在此登记。 */
    private val globalKeys = setOf(
        "theme", "fiat", "locale", "pnl_scheme", // M002 种子（theme/pnl_scheme 无常量，只有字面量）
        "display.precision", "login.username.enum", "cash.coins", "small.threshold", "network.proxy.enabled",
        "market.coingecko_key", "market.cmc_key", "market.refresh_minutes", "market.quota",
        "market.directory.last", "watch.coins", "sync.interval_minutes",
        "tray.minimize_on_close", "autostart.enabled", "tray.sync_notification", "backup.reminder",
    )

    /** 账户级键登记表（`account_id = <id>`）——生产键共 2 个。 */
    private val accountKeys = setOf("backup.last_at", "restore.last_at")

    /**
     * 已知的动态键调用点（该行键不可解析，但键在别处以字面量出现，已由其他规则收进集合）：
     * - `app/.../Main.kt`：`putGlobal(key, value)` —— key 来自 `ShellViewModel.onPreferenceChange("…")`
     *   （由 [PREFERENCE_KEY] 规则单独抽取）；
     * - `data/.../DefaultDesktopSettingsService.kt`：`getGlobal(key)` —— 私有助手 `flagOr(key, default)`，
     *   调用方传的是 `DesktopSettingsKeys.*` 常量，已由 [GLOBAL_CALL] 规则收进集合。
     */
    private val dynamicCallAllowlist = setOf(
        "app/src/main/kotlin/com/wuzhufolio/app/Main.kt" to "key",
        "src/main/kotlin/com/wuzhufolio/data/settings/DefaultDesktopSettingsService.kt" to "key",
        "src/main/kotlin/com/wuzhufolio/data/market/DeviceSecretStore.kt" to "key",
    )

    private val sourceRoots = listOf(
        "src/main/kotlin",
        "../domain/src/main/kotlin",
        "../ui/src/main/kotlin",
        "../app/src/main/kotlin",
    )

    private class Scan {
        val global = linkedSetOf<String>()
        val account = linkedSetOf<String>()
        val unresolved = mutableListOf<String>()
        var files = 0
    }

    /** 单次源码扫描（两个测试共用；纯文本分析，不依赖反射）。 */
    private fun scan(): Scan {
        val result = Scan()
        val files = sourceRoots.map { File(it) }.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        result.files = files.size
        val symbols = indexSymbols(files)
        files.forEach { collectCallSites(it, symbols, result) }
        return result
    }

    /**
     * ① 符号索引：`Qualifier.NAME` / 顶层 `NAME` → 该名字**出现过的全部表达式集合**。
     *
     * 关键：按「限定名 → 表达式集合」建索引，**不依赖文件遍历顺序**（CI windows-latest 曾暴露：
     * 早期实现用 HashMap<NAME, 表达式> 让同名常量互相覆盖，解析结果随目录顺序变化 → 未按限定名解析的
     * `watch.coins` 在 Windows 上丢失，见 `docs/test/defects.md` **DEF-12**）。
     * 解析规则：限定名唯一命中即用；裸名要求**跨全部限定符只对应一个字面量**，否则视为歧义（fail-closed）。
     */
    private class SymbolIndex {
        private val byQualified = HashMap<String, MutableSet<String>>()

        fun add(qualifier: String?, name: String, expression: String) {
            val key = if (qualifier == null) name else qualifier + "." + name
            byQualified.getOrPut(key) { linkedSetOf() }.add(expression)
        }

        /** 解析符号为字面量或别名表达式；歧义/未知名 → null（fail-closed）。 */
        fun resolve(symbol: String): String? {
            byQualified[symbol]?.let { return it.singleOrNull() }
            val bare = symbol.substringAfterLast('.')
            val candidates = byQualified.entries
                .filter { it.key == bare || it.key.endsWith("." + bare) }
                .flatMap { it.value }
                .toSet()
            return candidates.singleOrNull()
        }
    }

    /** ① 符号索引构建（逐文件、逐行；限定符 = 最近一次出现的 object/class/interface 名）。 */
    private fun indexSymbols(files: List<File>): SymbolIndex {
        val index = SymbolIndex()
        files.forEach { file ->
            var qualifier: String? = null
            file.readText().lineSequence().forEach { line ->
                QUALIFIER.find(line)?.let { qualifier = it.groupValues[1] }
                indexLine(line, qualifier, index)
            }
        }
        return index
    }

    /** 单行索引：字面量声明与别名声明各一条规则。 */
    private fun indexLine(line: String, qualifier: String?, index: SymbolIndex) {
        SYMBOL.find(line)?.let { m ->
            val literal = m.groupValues[2]
            if (KEY_SHAPE.matches(literal)) index.add(qualifier, m.groupValues[1], literal)
        }
        ALIAS.find(line)?.let { m ->
            if (m.groupValues[2].substringAfterLast('.') != m.groupValues[1]) {
                index.add(qualifier, m.groupValues[1], m.groupValues[2])
            }
        }
    }

    /** ② 调用点抽取（一行内可能有多类调用，逐条记录）。 */
    private fun collectCallSites(file: File, symbols: SymbolIndex, result: Scan) {
        val relative = file.path.replace('\\', '/')
        file.readText().lineSequence().forEachIndexed { index, line ->
            if (DECLARATION.containsMatchIn(line)) return@forEachIndexed // 声明行不是调用点
            GLOBAL_CALL.findAll(line).forEach { m ->
                record(result, m, symbols, intoGlobal = true, relative, index, line)
            }
            ACCOUNT_CALL.findAll(line).forEach { m ->
                record(result, m, symbols, intoGlobal = false, relative, index, line)
            }
            SEED_KEY.findAll(line).forEach { result.global += it.groupValues[1] }
            PREFERENCE_KEY.findAll(line).forEach { result.global += it.groupValues[1] }
            // 设备密钥条目（行情 Key，ADR-002 §2.1 方案甲）：`store.put(MarketConfig.KEY_CG, …)`
            DEVICE_SECRET_CALL.findAll(line).forEach { m ->
                record(result, m, symbols, intoGlobal = true, relative, index, line)
            }
        }
    }

    @Suppress("LongParameterList") // 扫描上下文一次传全（行号 + 原文用于报错定位）
    private fun record(
        result: Scan,
        match: MatchResult,
        symbols: SymbolIndex,
        intoGlobal: Boolean,
        path: String,
        lineIndex: Int,
        line: String,
    ) {
        val literal = match.groupValues[1]
        val symbol = match.groupValues[2]
        val target = if (intoGlobal) result.global else result.account
        when {
            literal.isNotEmpty() -> target += literal
            else -> {
                val resolved = resolveSymbol(symbol, symbols)
                if (resolved != null) {
                    target += resolved
                } else if (dynamicCallAllowlist.none { (file, name) -> path.endsWith(file) && symbol == name }) {
                    result.unresolved += path + ":" + (lineIndex + 1) + " 无法解析的键表达式：" + symbol +
                        "（" + line.trim() + "）"
                }
            }
        }
    }

    /**
     * 解析符号：限定名优先，裸名要求跨全部限定符唯一；别名（`val A = B.C`）按 [MAX_ALIAS_HOPS] 跳数继续解析。
     * 解析不到 → null（fail-closed：宁可红，也不静默放过）。
     */
    private fun resolveSymbol(symbol: String, symbols: SymbolIndex): String? {
        var current = symbol
        var resolved: String? = null
        var hops = 0
        while (resolved == null && hops < MAX_ALIAS_HOPS) {
            val expression = symbols.resolve(current)
            if (expression == null) {
                hops = MAX_ALIAS_HOPS
            } else if (KEY_SHAPE.matches(expression)) {
                resolved = expression
            } else {
                current = expression
                hops++
            }
        }
        return resolved
    }

    @Test
    fun `global and account scoped settings keys never share a name`() {
        val scan = scan()
        assertTrue(scan.files > 0, "源码扫描未命中任何文件（工作目录应为 data 模块）")
        assertTrue(
            scan.unresolved.isEmpty(),
            "存在无法解析的 settings 键表达式（fail-closed，请登记常量或加入白名单）：\n" +
                scan.unresolved.joinToString("\n"),
        )
        val overlap = scan.global intersect scan.account
        assertTrue(
            overlap.isEmpty(),
            "账户级键与全局键同名（命名空间冲突）：$overlap —— 新增账户级键不得与全局键同名",
        )
    }

    @Test
    fun `every scanned key is registered and every registered key still exists`() {
        val scan = scan()
        assertTrue(
            (scan.global - globalKeys).isEmpty(),
            "源码里出现了未登记的全局键（请在 globalKeys 登记表补一行）：${scan.global - globalKeys}",
        )
        assertTrue(
            (scan.account - accountKeys).isEmpty(),
            "源码里出现了未登记的账户级键（请在 accountKeys 登记表补一行）：${scan.account - accountKeys}",
        )
        assertTrue(
            (globalKeys - scan.global).isEmpty(),
            "登记表里的全局键在源码中已找不到（登记表腐化）：${globalKeys - scan.global}",
        )
        assertTrue(
            (accountKeys - scan.account).isEmpty(),
            "登记表里的账户级键在源码中已找不到（登记表腐化）：${accountKeys - scan.account}",
        )
    }

    private companion object {
        /**
         * 类型声明（限定名跟踪）：`object X` / `class X` / `interface X` / `enum class X` / `data class X` /
         * `sealed interface X` …（修饰词统一由 `(?:\w+\s+)*` 吸收）。
         * **无名 `companion object`** 不匹配 → 保留外层类型名（`interface MarketWatchService` 内的
         * `companion object { const val SETTINGS_KEY }` 因此解析为 `MarketWatchService.SETTINGS_KEY`）。
         */
        val QUALIFIER = Regex(
            """^\s*(?:\w+\s+)*(?:object|class|interface)\s+([A-Za-z_][A-Za-z0-9_]*)""",
        )

        /** `const val X = "…"` / `val X: String = "…"` / `val X = "…"`。 */
        val SYMBOL = Regex("""(?:const\s+)?val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String\s*)?=\s*"([^"]+)"""")

        /** 接口/函数声明行（形参与 settings API 同名，不构成调用点）。 */
        val DECLARATION = Regex("""^\s*(?:override\s+|private\s+|public\s+|internal\s+|suspend\s+)*fun\s""")

        /** 全局读写：`getGlobal("k")` / `putGlobal(Sym.K)` / `deleteGlobal(…)`。 */
        val GLOBAL_CALL = Regex("""(?:getGlobal|putGlobal|deleteGlobal)\(\s*(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_.]*))""")

        /** 账户级读写：`getAccountSetting(id, "k")` / `putAccountSetting(id, Sym.K)`。 */
        val ACCOUNT_CALL =
            Regex("""(?:getAccountSetting|putAccountSetting)\([^,()]+,\s*(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_.]*))""")

        /** M002 种子：`"theme" to "light",`。 */
        val SEED_KEY = Regex("""^\s*"([a-z][a-z0-9._]*)"\s+to\s+"""")

        /** 设备密钥条目读写：`store.put(MarketConfig.KEY_CG, …)` / `configured(KEY_CMC)`（全局键，方案甲）。 */
        val DEVICE_SECRET_CALL = Regex(
            """\b(?:store|secrets|secretStore|deviceSecrets)\.(?:configured|put|remove)\(\s*(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_.]*))""",
        )

        /** 别名声明：`val LANGUAGE = AppLanguage.SETTINGS_KEY`（右值为符号引用而非字面量）。 */
        val ALIAS = Regex(
            """(?:const\s+)?val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String\s*)?=\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)\s*$""",
        )

        /** 别名解析最大跳数。 */
        const val MAX_ALIAS_HOPS = 4

        /** UI 偏好透传：`onPreferenceChange("theme", …)`（键只在此处以字面量出现）。 */
        val PREFERENCE_KEY = Regex("""onPreferenceChange\(\s*"([^"]+)"""")

        /** settings 键形状：小写字母开头 + 小写字母/数字/点/下划线（剔除普通字符串字面量）。 */
        val KEY_SHAPE = Regex("""[a-z][a-z0-9._]*""")
    }
}
