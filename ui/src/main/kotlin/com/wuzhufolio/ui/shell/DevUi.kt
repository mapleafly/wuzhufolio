package com.wuzhufolio.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 开发期 UI 开关（**DEF-47**：组件走查页泄漏到正式发布版侧边栏）。
 *
 * 为什么需要它：M0 的组件走查页（T0.6 验收载体）自 P4 起就应当**只在开发构建可见**，
 * 但该门控从未实现——`ShellPage.GALLERY` 被无条件渲染进侧边栏，0.1.0 正式版（Windows/Linux 全形态）
 * 都能看到它。2026-09-24 人工拍板修复方案甲：**加构建期开关**。
 *
 * 取值链（单一真源）：
 * `app/build.gradle.kts` 的 `-Pwuzhufolio.devUi` → 生成的 `BuildInfo.DEV_UI` → app 装配层
 * （`MainWindowContent`）用 [androidx.compose.runtime.CompositionLocalProvider] 提供 → 本 CompositionLocal。
 * ui 模块不认识 Gradle 属性，只认这个布尔入参；两个分支都由 UI 测试覆盖。
 *
 * 默认 **false**（fail-closed）：任何未经装配层显式打开的组合（含 UI 测试、预览）都看不到走查页。
 */
val LocalDevUi = staticCompositionLocalOf { false }

/** 侧边栏可见页（[devUi] = true 时追加组件走查页；顺序 = ia.md 六页 + DEV 尾项）。 */
fun visibleShellPages(devUi: Boolean): List<ShellPage> =
    if (devUi) ShellPage.sidebarPages + ShellPage.GALLERY else ShellPage.sidebarPages
