package com.wuzhufolio.domain.proxy

/**
 * 系统代理领域模型（M11 · T11.3 · PRD 用户故事 4.2 / §7.2 模块 6.2、9.2；interaction.md §1.1 代理常态指示）。
 *
 * 口径（M10 T10.1 落开关、M11 落行为）：
 * - 开关 = settings 全局键 `network.proxy.enabled`（"on"/"off"，默认 on）；
 * - **开** = 自动检测并使用操作系统代理设置（Windows Internet 选项 / macOS 网络设置 /
 *   Linux 桌面设置或环境变量），行情（CoinGecko/CMC）与交易所（Binance）请求全部经其发出；
 * - **关** = 直连（忽略系统代理）；
 * - 应用内不提供代理地址/端口填写——PRD §7.2-6.2 范围即「自动检测并使用系统代理」，
 *   地址与端口在操作系统/VPN 客户端层面配置（M10 走查反馈修复轮答复口径，模块记录 M10 §7-3）。
 *
 * 本文件为纯规则：不触网、不读环境、不碰 JVM 系统属性（检测实现见 data 模块 SystemProxyDetector）。
 */

/** 代理类型（仅用于指示与日志；本应用不持有任何代理凭据）。 */
enum class ProxyKind { HTTP, SOCKS }

/** 检测到的代理端点（host:port；**不含凭据**——脱敏口径，日志与 UI 均只暴露此二元组）。 */
data class ProxyEndpoint(val kind: ProxyKind, val host: String, val port: Int) {
    /** 展示串（状态栏悬停提示用）。 */
    val label: String = host + ":" + port
}

/**
 * 请求实际走向。仅两态（interaction.md §1.1：状态栏常驻「代理：系统代理」/「直连」文字指示）：
 * 开关关闭、或开关开启但系统未配置代理，均为 [DIRECT]——两者都确实直连，文案不撒谎。
 */
enum class ProxyMode {
    DIRECT,
    SYSTEM,
    ;

    val indicator: String
        get() = if (this == SYSTEM) "代理：系统代理" else "直连"
}

/**
 * 代理运行期状态（状态栏数据源）。
 *
 * @param enabled 用户开关（settings `network.proxy.enabled`）。
 * @param endpoint 检测到的系统代理；null = 未检测到（或开关关闭时不做检测）。
 */
data class ProxyStatus(
    val enabled: Boolean,
    val endpoint: ProxyEndpoint?,
) {
    val mode: ProxyMode
        get() = if (enabled && endpoint != null) ProxyMode.SYSTEM else ProxyMode.DIRECT

    /** 状态栏文字（PRD 4.2 验收 3 的可见载体，非颜色单载体）。 */
    val indicator: String get() = mode.indicator

    /** 悬停提示（interaction.md §1.1「当前网络通过系统代理连接」）。 */
    val tooltip: String
        get() = when {
            mode == ProxyMode.SYSTEM -> "当前网络通过系统代理连接（" + endpoint?.label + "）"
            !enabled -> "系统代理已关闭——所有请求直连（设置 → 网络）"
            else -> "已开启系统代理，但系统未配置代理——当前直连"
        }

    companion object {
        /** 默认态（开关开、未检测）——UI 预览与测试基线。 */
        val DEFAULT: ProxyStatus = ProxyStatus(enabled = true, endpoint = null)

        /** 开关关闭态。 */
        val DISABLED: ProxyStatus = ProxyStatus(enabled = false, endpoint = null)
    }
}

/**
 * 系统代理环境变量口径（Linux/容器惯例；Windows/macOS 由 JDK 原生读取系统设置，不经此路径）。
 *
 * 纯解析：输入 = 环境变量快照（`System.getenv()` 由调用方注入，便于测试），输出 = 端点或 null。
 * 优先级 = https_proxy > HTTPS_PROXY > all_proxy > ALL_PROXY > http_proxy > HTTP_PROXY
 * （与 curl 惯例一致：HTTPS 流量优先，全协议兜底）。
 */
object ProxyEnvironment {

    /** 候选变量名（按优先级排列）。 */
    val KEYS: List<String> = listOf(
        "https_proxy", "HTTPS_PROXY", "all_proxy", "ALL_PROXY", "http_proxy", "HTTP_PROXY",
    )

    /** 按优先级取首个可解析的代理端点；全部缺失/不可解析 → null。 */
    fun parse(env: Map<String, String>): ProxyEndpoint? =
        KEYS.asSequence().mapNotNull { parseUrl(env[it]) }.firstOrNull()

    /**
     * 解析单个代理 URL。接受 `http://host:port`、`socks5://host:port`、`socks://host:port`、
     * 以及裸 `host:port`；凭据段（user:pass@）忽略——本应用不存储代理凭据。
     * 无端口或空串 → null（端口未知则不猜测，宁可不指示）。
     */
    fun parseUrl(raw: String?): ProxyEndpoint? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val schemeEnd = trimmed.indexOf("://")
        val scheme = if (schemeEnd >= 0) trimmed.take(schemeEnd).lowercase() else ""
        val body = if (schemeEnd >= 0) trimmed.substring(schemeEnd + 3) else trimmed
        // 去路径与凭据（凭据仅用于定位 host:port，本应用不保留）
        val hostPort = body.substringBefore('/').substringAfterLast('@')
        // IPv6 字面量 [::1]:8080 —— 去方括号
        val host = hostPort.substringBeforeLast(':', missingDelimiterValue = "").removeSurrounding("[", "]")
        val port = hostPort.substringAfterLast(':', missingDelimiterValue = "").toIntOrNull()
        return if (host.isNotEmpty() && port != null && port in 1..65535) {
            ProxyEndpoint(
                kind = if (scheme.startsWith("socks")) ProxyKind.SOCKS else ProxyKind.HTTP,
                host = host,
                port = port,
            )
        } else {
            null
        }
    }
}
