package com.wuzhufolio.data.proxy

import com.wuzhufolio.domain.proxy.ProxyEndpoint
import com.wuzhufolio.domain.proxy.ProxyKind
import com.wuzhufolio.domain.proxy.ProxyMode
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 系统代理检测与开关感知 selector（M11 T11.3）：JDK 系统代理优先、环境变量兜底、关闭即直连。 */
class SystemProxyDetectorTest {

    private val probe = URI.create("https://api.coingecko.com/api/v3/ping")

    /** 假 selector：报告固定代理（JDK 系统代理路径的可注入替身，不触网）。 */
    private fun fixedSelector(proxy: Proxy?): ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> = listOf(proxy ?: Proxy.NO_PROXY)
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
    }

    @Test
    fun `jdk system proxy wins over environment`() {
        val detector = SystemProxyDetector(
            env = mapOf("https_proxy" to "http://env-proxy:1"),
            probeUri = probe,
            defaultSelector = {
                fixedSelector(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("os-proxy", 8080)))
            },
        )
        assertEquals(ProxyEndpoint(ProxyKind.HTTP, "os-proxy", 8080), detector.detectEndpoint())
    }

    @Test
    fun `environment used when jdk reports direct`() {
        val detector = SystemProxyDetector(
            env = mapOf("https_proxy" to "socks5://env-proxy:1080"),
            probeUri = probe,
            defaultSelector = { fixedSelector(null) },
        )
        assertEquals(ProxyEndpoint(ProxyKind.SOCKS, "env-proxy", 1080), detector.detectEndpoint())
    }

    @Test
    fun `direct when neither jdk nor environment configures a proxy`() {
        val detector = SystemProxyDetector(
            env = emptyMap(),
            probeUri = probe,
            defaultSelector = { fixedSelector(null) },
        )
        assertNull(detector.detectEndpoint())
        val status = detector.detect(enabled = true)
        assertEquals(ProxyMode.DIRECT, status.mode)
        assertEquals("直连", status.indicator)
    }

    @Test
    fun `disabled switch skips detection entirely`() {
        var called = false
        val detector = SystemProxyDetector(
            env = mapOf("https_proxy" to "http://env-proxy:1"),
            probeUri = probe,
            defaultSelector = {
                called = true
                fixedSelector(null)
            },
        )
        val status = detector.detect(enabled = false)
        assertEquals(ProxyMode.DIRECT, status.mode)
        assertEquals(false, status.enabled)
        assertNull(status.endpoint, "开关关闭 = 直连，不做无谓检测")
        assertTrue(!called, "关闭态不得触发 selector 调用")
    }

    @Test
    fun `selector failures degrade to direct instead of throwing`() {
        val detector = SystemProxyDetector(
            env = emptyMap(),
            probeUri = probe,
            defaultSelector = { throw IllegalStateException("boom") },
        )
        assertNull(detector.detectEndpoint())
        assertEquals(ProxyMode.DIRECT, detector.detect(enabled = true).mode)
    }

    @Test
    fun `toggleable selector delegates when on and forces direct when off`() {
        var enabled = true
        val target = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("os-proxy", 8080))
        val selector = ToggleableProxySelector({ enabled }, { fixedSelector(target) })
        assertEquals(listOf(target), selector.select(probe))
        enabled = false
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(probe), "关闭 = 忽略系统代理（PRD 6.2）")
        enabled = true
    }

    @Test
    fun `toggleable selector tolerates missing platform selector`() {
        val selector = ToggleableProxySelector({ true }, { null })
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(probe))
        // connectFailed 不得抛（委派目标缺失）
        selector.connectFailed(probe, InetSocketAddress.createUnresolved("h", 1), IOException("x"))
    }

    @Test
    fun `runtime holds switch copy and publishes status`() {
        val runtime = ProxyRuntime(
            detector = SystemProxyDetector(
                env = mapOf("https_proxy" to "http://env-proxy:1"),
                probeUri = probe,
                defaultSelector = { fixedSelector(null) },
            ),
        )
        runtime.setEnabled(true)
        assertEquals(ProxyMode.SYSTEM, runtime.status.value.mode)
        assertEquals("代理：系统代理", runtime.current().indicator)
        runtime.setEnabled(false)
        assertEquals(ProxyMode.DIRECT, runtime.status.value.mode, "关闭后状态流即时更新（状态栏）")
        runtime.setEnabled(true)
        assertEquals(ProxyMode.SYSTEM, runtime.current().mode)
    }
}
