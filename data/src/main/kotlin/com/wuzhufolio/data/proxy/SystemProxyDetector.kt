package com.wuzhufolio.data.proxy

import com.wuzhufolio.domain.proxy.ProxyEndpoint
import com.wuzhufolio.domain.proxy.ProxyEnvironment
import com.wuzhufolio.domain.proxy.ProxyKind
import com.wuzhufolio.domain.proxy.ProxyStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/**
 * JVM 系统代理开关（M11 · T11.3 · PRD §7.2 模块 6.2「默认自动检测并使用系统代理」）。
 *
 * JDK 的 `sun.net.spi.DefaultProxySelector` **仅在 `java.net.useSystemProxies=true` 时**才去读
 * 操作系统代理设置（Windows Internet 选项注册表 / macOS SystemConfiguration / Linux GNOME gsettings）；
 * 否则只认 `http.proxyHost` 一类 JVM 系统属性（本应用不使用，用户不会在 JVM 层面配置）。
 * 该属性在 `DefaultProxySelector` **类初始化时读取一次**，因此必须在任何网络类加载前设置——
 * 调用点 = `Main.main` 首行（同时 `app/build.gradle.kts` 的 jvmArgs 为打包版兜底）。
 */
object JdkSystemProxy {

    /** 系统属性名（JDK 标准）。 */
    const val USE_SYSTEM_PROXIES: String = "java.net.useSystemProxies"

    /** 幂等开启（已显式配置为其他值时尊重既有配置——打包版 jvmArgs 可能已置）。 */
    fun enable() {
        if (System.getProperty(USE_SYSTEM_PROXIES) == null) {
            System.setProperty(USE_SYSTEM_PROXIES, "true")
        }
    }
}

/**
 * 系统代理检测（T11.3）：JDK 系统代理优先，环境变量兜底。
 *
 * 优先级说明：JDK 路径覆盖 Windows/macOS/GNOME 桌面（用户真正在「系统设置」里配的代理）；
 * `https_proxy` 等环境变量覆盖 Linux 命令行/容器惯例（GNOME 之外的桌面与 systemd 服务场景），
 * 亦即 M10 走查答复中的「Linux 环境变量或桌面设置」。两者都没有 → 直连。
 *
 * 本类不触网：`ProxySelector.select` 只解析本机代理配置，不发请求。
 */
class SystemProxyDetector(
    /** 环境变量快照（注入便于测试；生产 = `System.getenv()`）。 */
    private val env: Map<String, String> = System.getenv(),
    /** 探测 URI，仅取 scheme/host 供 selector 解析；不发起连接。 */
    private val probeUri: URI = URI.create(PROBE_URI),
    /** JDK 默认 selector 提供者（注入便于测试）。 */
    private val defaultSelector: () -> ProxySelector? = { ProxySelector.getDefault() },
) {

    /**
     * 检测当前生效的代理并组装状态（[enabled] = 用户开关；关闭时不检测——开关即直连，不做无谓 IO）。
     */
    fun detect(enabled: Boolean): ProxyStatus =
        ProxyStatus(enabled = enabled, endpoint = if (enabled) detectEndpoint() else null)

    /** 检测端点：JDK 系统代理 → 环境变量 → null。 */
    fun detectEndpoint(): ProxyEndpoint? = fromJdkSelector() ?: ProxyEnvironment.parse(env)

    /** JDK 默认 selector 报告的代理；DIRECT / 异常 / 空列表 → null。 */
    private fun fromJdkSelector(): ProxyEndpoint? = runCatching {
        defaultSelector()?.select(probeUri)?.firstOrNull()
    }.getOrNull()?.let { toEndpoint(it) }

    /** 系统 selector，供 [ToggleableProxySelector] 委派（可能为 null = 平台不支持）。 */
    fun systemSelector(): ProxySelector? = defaultSelector()

    private fun toEndpoint(proxy: Proxy): ProxyEndpoint? {
        if (proxy == Proxy.NO_PROXY || proxy.type() == Proxy.Type.DIRECT) return null
        val address = proxy.address() as? InetSocketAddress
        val host = address?.hostString?.takeIf { it.isNotBlank() }
        return if (host == null) {
            null
        } else {
            ProxyEndpoint(
                kind = if (proxy.type() == Proxy.Type.SOCKS) ProxyKind.SOCKS else ProxyKind.HTTP,
                host = host,
                port = address.port,
            )
        }
    }

    companion object {
        /** 探测 URI（CoinGecko 主源域名——与实际请求同域，代理规则匹配一致）。 */
        const val PROBE_URI: String = "https://api.coingecko.com/api/v3/ping"
    }
}

/**
 * 开关感知的 [ProxySelector]（注入两个 HTTP 客户端；每次请求读取当前开关，开关切换**无需重建客户端**）：
 * 开 → 委派系统 selector；关 → 一律 DIRECT（忽略系统代理，PRD 6.2 / M10 §7-3 答复口径）。
 */
class ToggleableProxySelector(
    private val enabled: () -> Boolean,
    private val systemSelector: () -> ProxySelector?,
) : ProxySelector() {

    override fun select(uri: URI): List<Proxy> {
        if (!enabled()) return DIRECT
        val selected = runCatching { systemSelector()?.select(uri) }.getOrNull()
        return selected?.takeIf { it.isNotEmpty() } ?: DIRECT
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        runCatching { systemSelector()?.connectFailed(uri, sa, ioe) }
    }

    private companion object {
        val DIRECT: List<Proxy> = listOf(Proxy.NO_PROXY)
    }
}

/**
 * 代理运行期状态（T11.3）：持有开关的易变副本 + 检测结果流，供状态栏指示与设置页消费。
 *
 * 为什么单独持开关副本：HTTP 客户端每请求都要问一次代理（[ToggleableProxySelector]），
 * 直接查数据库会阻塞网络线程；故由引导层在启动与开关切换时同步写入。
 * [refresh] 在启动、开关切换、以及调度 tick 时调用（系统代理可能在应用运行期间被用户改动）。
 */
class ProxyRuntime(
    private val detector: SystemProxyDetector = SystemProxyDetector(),
    private val logger: org.slf4j.Logger = LoggerFactory.getLogger("wuzhufolio.proxy"),
) {

    @Volatile
    private var enabledFlag: Boolean = true

    private val _status = MutableStateFlow(ProxyStatus.DEFAULT)

    /** 状态栏数据源（启动时先 [refresh] 一次）。 */
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    /** 注入两个 HTTP 客户端的 selector（构造后不变）。 */
    val selector: ProxySelector = ToggleableProxySelector({ enabledFlag }, { detector.systemSelector() })

    /** 设置页切换开关（写设置成功后调用）。 */
    fun setEnabled(on: Boolean) {
        enabledFlag = on
        refresh()
    }

    /** 重新检测并广播（返回最新状态）。 */
    fun refresh(): ProxyStatus {
        val next = runCatching { detector.detect(enabledFlag) }
            .getOrElse {
                logger.warn("proxy detection failed: " + it.javaClass.simpleName)
                ProxyStatus(enabled = enabledFlag, endpoint = null)
            }
        _status.value = next
        return next
    }

    /** 当前状态快照。 */
    fun current(): ProxyStatus = _status.value
}
