package com.wuzhufolio.app.tray

import java.awt.SystemTray

/**
 * 系统托盘可用性探测（M11 · T11.1）。
 *
 * 为什么直接问 AWT 而不复用 Compose 的 `isTraySupported()`：二者最终都是
 * `SystemTray.isSupported()`，但 Compose 侧该符号在 `ui-desktop` 的可见性随版本变动
 * （1.12.0 的 Kotlin 元数据未暴露为可直接引用的顶层函数）。AWT 是 JDK 标准 API，
 * 语义完全一致且不存在跨版本风险。
 *
 * 无头环境（CI/无 X11/Wayland 无 StatusNotifier 宿主）下 `isSupported()` 会抛
 * `HeadlessException`——按「不支持托盘」处理：应用仍可运行，仅关窗即退出（不静默藏窗口，
 * 见 [com.wuzhufolio.app.AppHost] 降级口径）。
 */
object TraySupport {

    /** 当前环境是否支持系统托盘（异常/无头一律 false）。 */
    fun isSupported(): Boolean = runCatching { SystemTray.isSupported() }.getOrDefault(false)
}
