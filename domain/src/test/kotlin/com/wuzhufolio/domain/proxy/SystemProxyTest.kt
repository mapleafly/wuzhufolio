package com.wuzhufolio.domain.proxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 系统代理纯规则（M11 T11.3）：环境变量解析 + 状态栏指示文案（interaction.md §1.1 口径）。 */
class SystemProxyTest {

    @Test
    fun `parses http and socks urls`() {
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 7890),
            ProxyEnvironment.parseUrl("http://127.0.0.1:7890"),
        )
        assertEquals(
            ProxyEndpoint(ProxyKind.SOCKS, "10.0.0.2", 1080),
            ProxyEnvironment.parseUrl("socks5://10.0.0.2:1080"),
        )
        // 裸 host:port 按 HTTP 处理（curl 惯例）
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "proxy.lan", 8080),
            ProxyEnvironment.parseUrl("proxy.lan:8080"),
        )
    }

    @Test
    fun `ignores credentials path and ipv6 brackets`() {
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "proxy.lan", 3128),
            ProxyEnvironment.parseUrl("http://user:secret@proxy.lan:3128/"),
        )
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "::1", 8888),
            ProxyEnvironment.parseUrl("http://[::1]:8888"),
        )
    }

    @Test
    fun `rejects unusable values`() {
        assertNull(ProxyEnvironment.parseUrl(null), "null 不猜测")
        assertNull(ProxyEnvironment.parseUrl("   "))
        assertNull(ProxyEnvironment.parseUrl("http://proxy.lan"), "无端口不指示")
        assertNull(ProxyEnvironment.parseUrl("http://proxy.lan:notaport"))
        assertNull(ProxyEnvironment.parseUrl("http://proxy.lan:70000"), "端口越界")
    }

    @Test
    fun `environment priority prefers https then all then http`() {
        val env = mapOf(
            "http_proxy" to "http://http-only:1",
            "all_proxy" to "socks5://all:2",
            "https_proxy" to "http://https-first:3",
        )
        assertEquals(ProxyEndpoint(ProxyKind.HTTP, "https-first", 3), ProxyEnvironment.parse(env))
        // 去掉 https_proxy → all_proxy 兜底
        assertEquals(
            ProxyEndpoint(ProxyKind.SOCKS, "all", 2),
            ProxyEnvironment.parse(env - "https_proxy"),
        )
        // 只剩 http_proxy
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "http-only", 1),
            ProxyEnvironment.parse(mapOf("http_proxy" to "http://http-only:1")),
        )
        assertNull(ProxyEnvironment.parse(emptyMap()))
    }

    @Test
    fun `uppercase variants are accepted`() {
        assertEquals(
            ProxyEndpoint(ProxyKind.HTTP, "upper", 9),
            ProxyEnvironment.parse(mapOf("HTTPS_PROXY" to "http://upper:9")),
        )
    }

    @Test
    fun `status indicator matches interaction wording`() {
        val proxied = ProxyStatus(enabled = true, endpoint = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 7890))
        assertEquals(ProxyMode.SYSTEM, proxied.mode)
        assertEquals("代理：系统代理", proxied.indicator)
        assertEquals("当前网络通过系统代理连接（127.0.0.1:7890）", proxied.tooltip)

        val noSystemProxy = ProxyStatus(enabled = true, endpoint = null)
        assertEquals(ProxyMode.DIRECT, noSystemProxy.mode)
        assertEquals("直连", noSystemProxy.indicator, "开关开但系统未配代理 = 确实直连，文案不得撒谎")

        val disabled = ProxyStatus(enabled = false, endpoint = ProxyEndpoint(ProxyKind.HTTP, "127.0.0.1", 7890))
        assertEquals(ProxyMode.DIRECT, disabled.mode, "开关关闭时即使系统有代理也走直连")
        assertEquals("直连", disabled.indicator)
    }
}
