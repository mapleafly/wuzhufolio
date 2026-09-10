package com.wuzhufolio.ui.i18n

import com.wuzhufolio.domain.settings.AppLanguage
import java.io.File
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * i18n 目录一致性与「无漏译」守护（M12 T12.4）。
 *
 * 三项守护：
 * 1. **双档齐备**：每个文案目录的 zh/en 两档对同一接口方法均有实现（编译期保证）+ 取值非空白；
 * 2. **英文档无中文**：en 档不得残留 CJK（漏译最典型的形态）；
 * 3. **源码无内联中文文案**：`ui/src/main` 下除 i18n 目录外，不得出现中文字符串字面量
 *    （KDoc/`//` 注释按项目约定仍为中文，扫描前剥离）——防止后续模块把文案又写回调用点。
 */
class I18nCatalogTest {

    @Test
    fun `every catalogue entry is non blank in both languages`() {
        catalogs().forEach { (zh, en) ->
            stringGetters(zh).forEach { method ->
                    val zhValue = invoke(method, zh)
                    val enValue = invoke(method, en)
                    assertTrue(zhValue.isNotBlank(), "zh 文案为空：" + interfaceOf(zh).simpleName + "." + method.name)
                    assertTrue(enValue.isNotBlank(), "en 文案为空：" + interfaceOf(en).simpleName + "." + method.name)
                }
        }
    }

    @Test
    fun `english catalogue never contains cjk characters`() {
        catalogs().forEach { (_, en) ->
            stringGetters(en).forEach { method ->
                    val value = invoke(method, en)
                    assertTrue(
                        !value.containsCjk(),
                        "en 档残留中文：" + interfaceOf(en).simpleName + "." + method.name + " = " + value,
                    )
                }
        }
    }

    @Test
    fun `chinese catalogue is used when the language is chinese`() {
        try {
            I18n.set(AppLanguage.ZH)
            assertTrue(I18n.isZh)
            assertEquals("仪表盘", shellStrings.navDashboard)
            I18n.set(AppLanguage.EN)
            assertEquals("Dashboard", shellStrings.navDashboard)
        } finally {
            I18n.set(AppLanguage.ZH)
        }
    }

    @Test
    fun `ui sources keep no inline chinese copy outside the i18n package`() {
        val root = File("src/main/kotlin/com/wuzhufolio/ui")
        assertTrue(root.isDirectory, "工作目录应为 ui 模块（实际：" + root.absolutePath + "）")
        val offenders = ArrayList<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains(File.separator + "i18n" + File.separator) }
            .forEach { file ->
                val literal = chineseLiteralIn(stripComments(file.readText()))
                if (literal != null) offenders += file.name + " → " + literal
            }
        assertTrue(
            offenders.isEmpty(),
            "以下文件仍内联中文文案（应移入 ui/i18n 目录）：\n" + offenders.joinToString("\n"),
        )
    }

    // ---------- 目录清单与反射工具 ----------

    /** (zh, en) 两档实例对（新增目录时在此登记）。 */
    private fun catalogs(): List<Pair<Any, Any>> = listOf(
        CommonStringsZh to CommonStringsEn,
        ShellStringsZh to ShellStringsEn,
        MarketStringsZh to MarketStringsEn,
        LedgerStringsZh to LedgerStringsEn,
        AuthStringsZh to AuthStringsEn,
        SettingsStringsZh to SettingsStringsEn,
        BackupStringsZh to BackupStringsEn,
        ExchangeStringsZh to ExchangeStringsEn,
        GalleryStringsZh to GalleryStringsEn,
        PortfolioStringsZh to PortfolioStringsEn,
    )

    private fun interfaceOf(instance: Any): Class<*> =
        instance.javaClass.interfaces.firstOrNull { it.simpleName.endsWith("Strings") }
            ?: instance.javaClass.interfaces.first()

    /**
     * 可安全反射调用的文案读取方法：返回 String，且参数只含 String/Int/Boolean
     * （其余参数类型——如领域错误对象——无法在此合成，由各自模块的单测覆盖）。
     */
    private fun stringGetters(instance: Any): List<Method> = interfaceOf(instance).methods
        .filter { it.returnType == String::class.java && it.name !in OBJECT_METHODS }
        .filter { method ->
            method.parameterTypes.all {
                it == String::class.java ||
                    it == Int::class.javaPrimitiveType ||
                    it == Boolean::class.javaPrimitiveType
            }
        }

    @Suppress("SwallowedException") // 见下方 catch：原始异常经 AssertionError 的 cause 透出，非吞掉
    private fun invoke(method: Method, target: Any): String {
        val args: Array<Any?> = method.parameterTypes.map { type: Class<*> ->
            when {
                type == String::class.java -> "X"
                type == Int::class.javaPrimitiveType -> 1
                type == Boolean::class.javaPrimitiveType -> true
                else -> null
            }
        }.toTypedArray()
        return try {
            method.invoke(target, *args) as? String ?: ""
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 原始原因经 AssertionError 的 cause 透出（detekt SwallowedException 在此为误判）
            throw AssertionError(
                "文案取值抛异常：" + interfaceOf(target).simpleName + "." + method.name,
                e.targetException,
            )
        }
    }

    private fun String.containsCjk(): Boolean = any { it.code in 0x4E00..0x9FFF }

    /**
     * 剥离 `/* */` 与 `//` 注释（KDoc 按项目约定为中文，不应计入漏译扫描）。
     * 手写小词法器：分支与嵌套来自「块注释/行注释/字符串/普通字符」四态，拆函数反而更难读。
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == '/' && index + 1 < source.length && source[index + 1] == '*' -> {
                    val end = source.indexOf("*/", index + 2)
                    index = if (end < 0) source.length else end + 2
                }
                ch == '/' && index + 1 < source.length && source[index + 1] == '/' -> {
                    val end = source.indexOf('\n', index)
                    index = if (end < 0) source.length else end
                }
                ch == '"' -> {
                    // 字符串字面量整体保留（含中文则命中），跳过转义
                    out.append(ch)
                    index++
                    while (index < source.length) {
                        val c = source[index]
                        out.append(c)
                        index++
                        if (c == '\\' && index < source.length) {
                            out.append(source[index])
                            index++
                        } else if (c == '"') {
                            break
                        }
                    }
                }
                else -> {
                    out.append(ch)
                    index++
                }
            }
        }
        return out.toString()
    }

    /** 返回首个含中文的字符串字面量（无则 null）。 */
    private fun chineseLiteralIn(source: String): String? {
        val regex = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        return regex.findAll(source)
            .map { it.groupValues[1] }
            .firstOrNull { it.containsCjk() }
    }

    private companion object {
        val OBJECT_METHODS = setOf("toString", "hashCode", "equals")
    }
}
