package com.wuzhufolio.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzStatusBar
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.gallery.ComponentGallery
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 主壳导航骨架（T0.6）：左 220dp 侧边栏（五页空壳 + 走查页）+ 顶栏（标题/代理指示/主题切换）+ 底部状态栏。
 * 主题由 ViewModel 驱动，切换即时重渲染（单一真源 + 双主题，已决策事项 16）。
 */
@Composable
fun MainShell(
    viewModel: ShellViewModel,
    modifier: Modifier = Modifier,
    /** M1 T1.1：钥匙串不可用降级提示等启动安全说明（非空时弹一次模态）。 */
    startupNotice: String? = null,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val pnlScheme by viewModel.pnlScheme.collectAsState()
    val page by viewModel.page.collectAsState()
    val toast by viewModel.toast.collectAsState()
    var showStartupNotice by remember { mutableStateOf(startupNotice != null) }

    WuzhuTheme(themeMode = themeMode, pnlScheme = pnlScheme) {
        val colors = WzTheme.colors
        Box(modifier = modifier.fillMaxSize().background(colors.bg).testTag("main-shell")) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Sidebar(currentPage = page, onSelect = viewModel::selectPage)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            title = page.label,
                            themeMode = themeMode,
                            onToggleTheme = viewModel::toggleTheme,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            when (page) {
                                ShellPage.GALLERY -> ComponentGallery(viewModel)
                                else -> PlaceholderPage(page)
                            }
                        }
                    }
                }
                WzStatusBar(
                    proxyActive = false,
                    syncStatus = "同步：空闲（M5/M6 接入）",
                    dataSource = "数据源：CoinGecko",
                    version = "v0.1.0-m0",
                )
            }
            WzToastHost(toast = toast, onDismiss = viewModel::dismissToast)
            if (startupNotice != null && showStartupNotice) {
                StartupNoticeModal(notice = startupNotice) { showStartupNotice = false }
            }
        }
    }
}

/** 启动安全说明模态（T1.1「无钥匙串降级提示」）。 */
@Composable
private fun StartupNoticeModal(notice: String, onDismiss: () -> Unit) {
    val colors = WzTheme.colors
    WzModal(title = "安全提示", onDismiss = onDismiss, testTag = "startup-notice") {
        Text(
            text = notice,
            color = colors.ink2,
            style = WzTheme.typography.body,
        )
        WzButton(
            text = "我知道了",
            onClick = onDismiss,
            variant = WzButtonVariant.Primary,
            modifier = Modifier.padding(top = 16.dp).align(Alignment.End),
            testTag = "startup-notice-ok",
        )
    }
}

@Composable
private fun Sidebar(currentPage: ShellPage, onSelect: (ShellPage) -> Unit) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .border(0.dp, colors.line)
            .padding(vertical = 12.dp)
            .testTag("sidebar"),
    ) {
        Text(
            text = "WuZhuFolio",
            color = colors.ink,
            style = WzTheme.typography.pageTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = "本地 · 隐私 · 账本",
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
        ShellPage.sidebarPages.forEach { page ->
            SidebarNavItem(
                label = page.label,
                active = page == currentPage,
                onClick = { onSelect(page) },
                testTag = "nav-" + page.name,
            )
        }
        Box(modifier = Modifier.weight(1f))
        SidebarNavItem(
            label = ShellPage.GALLERY.label + "（DEV）",
            active = currentPage == ShellPage.GALLERY,
            onClick = { onSelect(ShellPage.GALLERY) },
            testTag = "nav-" + ShellPage.GALLERY.name,
        )
    }
}

@Composable
private fun SidebarNavItem(label: String, active: Boolean, onClick: () -> Unit, testTag: String) {
    val colors = WzTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val bg = when {
        active -> colors.surface2
        hovered -> colors.surface2
        else -> colors.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    if (active) colors.accent else colors.surface,
                    RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = label,
            color = if (active) colors.ink else colors.ink2,
            style = if (active) WzTheme.typography.bodyStrong else WzTheme.typography.body,
            modifier = Modifier.padding(start = 13.dp),
        )
    }
}

@Composable
private fun TopBar(title: String, themeMode: ThemeMode, onToggleTheme: () -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface)
            .border(0.dp, colors.line)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = colors.ink, style = WzTheme.typography.pageTitle)
        Box(modifier = Modifier.weight(1f))
        Text(
            text = "直连",
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = if (themeMode == ThemeMode.LIGHT) "☾" else "☀",
            color = colors.ink,
            style = WzTheme.typography.pageTitle,
            modifier = Modifier
                .clickable(onClick = onToggleTheme)
                .padding(8.dp)
                .testTag("theme-toggle"),
        )
    }
}

/** 五页空壳（P4 模块页面按垂直切片计划填充，task-breakdown §2）。 */
@Composable
private fun PlaceholderPage(page: ShellPage) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("page-" + page.name),
    ) {
        Text(
            text = page.label,
            color = colors.ink,
            style = WzTheme.typography.display,
        )
        Text(
            text = "P4 模块页面占位（依赖顺序见 docs/tech/task-breakdown.md §2）",
            color = colors.ink2,
            style = WzTheme.typography.body,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
