package com.wuzhufolio.app

import org.slf4j.Logger

/**
 * 辅助技术（Assistive Technology / Java Access Bridge）前置校验 —— **DEF-43**。
 *
 * AWT 在首次 `Toolkit.getDefaultToolkit()` 时按系统属性 `javax.accessibility.assistive_technologies`
 * 反射加载辅助技术类。该属性可能来自：
 * - 用户目录 `~/.accessibility.properties`（运行过 `jabswitch -enable`、安装读屏软件时会写）；
 * - `$JAVA_HOME/conf/accessibility.properties`。
 *
 * 若被配置的类在当前运行时不可用（打包版是 **jlink 裁剪**的运行时），AWT 会抛
 * `java.awt.AWTError: Assistive Technology not found: ...` —— 该异常发生在 `application { }` 内部
 * （Skiko → `UIManager` 静态初始化），因此**应用在日志文件已创建之后、任何业务日志之前即终止**，
 * 打包启动器只会弹出无细节的 `Failed to launch JVM`。
 *
 * 处置分两层：
 * 1. **正向修复**：运行时模块集补齐 `jdk.accessibility`（`app/build.gradle.kts`），标准场景下读屏功能完整可用；
 * 2. **兜底**（本文件）：AWT 初始化前校验一次可用性，**不可用时清空该属性并告警**——宁可「辅助技术降级 + 明确日志」，
 *    也不要「应用完全无法启动」；可用时不动该属性。
 */
internal object AssistiveTech {

    /** AWT 读取的辅助技术属性名。 */
    const val PROPERTY: String = "javax.accessibility.assistive_technologies"

    /**
     * 纯函数：判断是否需要清空属性，并把决定交给 [apply]。
     *
     * @param configured 属性当前值（逗号分隔的类名；null/空 = 未配置）
     * @param canLoad 类可用性判定（生产实现 = 反射探测；测试注入假实现）
     * @param apply 属性写入动作（生产实现 = `System.setProperty`；测试注入假实现，避免污染全局状态）
     * @param logger 可选告警出口
     * @return 不可用的类名列表（空列表 = 无需处理、属性保持不变）
     */
    fun sanitize(
        configured: String?,
        canLoad: (String) -> Boolean,
        apply: (String) -> Unit,
        logger: Logger? = null,
    ): List<String> {
        val names = configured
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val missing = names.filterNot(canLoad)
        if (missing.isNotEmpty()) {
            apply("")
            logger?.warn(
                "assistive technology unavailable; property cleared so the app can start | missing={}",
                missing.joinToString(","),
            )
        }
        return missing
    }

    /** 生产入口：读取系统属性 → 校验 → 必要时清空（详见类注释）。 */
    fun sanitizeFromSystemProperty(logger: Logger? = null): List<String> =
        sanitize(
            configured = System.getProperty(PROPERTY),
            canLoad = ::canLoad,
            apply = { System.setProperty(PROPERTY, it) },
            logger = logger,
        )

    /** 只探测「类是否存在」—— `initialize = false`，避免触发辅助技术自身的静态初始化（可能加载本地库）。 */
    private fun canLoad(className: String): Boolean =
        runCatching { Class.forName(className, false, AssistiveTech::class.java.classLoader) }.isSuccess
}
