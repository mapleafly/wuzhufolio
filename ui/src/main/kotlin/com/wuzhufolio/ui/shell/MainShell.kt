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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.proxy.ProxyStatus
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzStatusBar
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.gallery.ComponentGallery
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.theme.ProvideWzWindowClass
import com.wuzhufolio.ui.theme.WuzhuTheme
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 主壳（T0.6 骨架 → M12 T12.1 整合）：左 220dp 侧边栏（六页）+ 顶栏（标题/数据源徽章/手动刷新行情/
 * 手动同步/主题切换）+ 底部状态栏（代理指示 / 同步状态 / 数据源 / 额度·备份·断链提示 / 版本）。
 *
 * 语言（M12 T12.4）：由 [language] 驱动 [WuzhuTheme]，切换后整棵子树重建（文案为动态取值）。
 * 页槽：null = 未接线的占位页（仅开发期可见）；资产列表页内可切换到币种资产详情子页（[ShellViewModel.coinDetailId]）。
 */
@Suppress("LongParameterList", "LongMethod") // 主壳装配点：页槽 + 顶栏/状态栏数据源全部在此汇合，拆参数反而更难追
@Composable
fun MainShell(
    viewModel: ShellViewModel,
    modifier: Modifier = Modifier,
    /** M1 T1.1：钥匙串不可用降级提示等启动安全说明（非空时弹一次模态）。 */
    startupNotice: String? = null,
    /** M2：侧边栏底部账户区（原型 acctBtn：切换账户/登出入口）。 */
    accountArea: @Composable () -> Unit = {},
    /** 当前账户名（仪表盘副标题用；PRD §6 账户清晰性）。 */
    accountName: String = "",
    /**
     * M12 T12.4：界面语言的**初始档**（会话创建时由启动设置注入 [ShellViewModel]）。
     * 运行期以 VM 的 `language` StateFlow 为准——设置页切换后立即重组，无需重启
     * （2026-09-11 走查反馈修复轮：此前误用启动期常量，导致「切了没反应、重启才变」）。
     */
    language: AppLanguage = AppLanguage.ZH,
    /** M10：设置页内容（参数 = ShellViewModel，主题/盈亏配色/语言双向同步；null = 占位页）。 */
    settingsPageContent: (@Composable (ShellViewModel) -> Unit)? = null,
    /** D21：行情页内容（行情浏览 + 自选；null = 占位页）。 */
    watchPageContent: (@Composable () -> Unit)? = null,
    /** M12：仪表盘（聚合页；参数 = 当前账户名；null = 占位页）。 */
    dashboardPageContent: (@Composable (accountName: String) -> Unit)? = null,
    /** M12：资产列表（聚合页；参数 = 打开币种详情的回调；null = 占位页）。 */
    assetsPageContent: (@Composable (onOpenCoin: (String) -> Unit) -> Unit)? = null,
    /** M12：币种资产详情（参数 = cg_id + 返回回调；null = 占位页）。 */
    coinDetailPageContent: (@Composable (cgId: String, onBack: () -> Unit) -> Unit)? = null,
    /** M7：交易管理页内容（null = 占位页）。 */
    transactionsPageContent: (@Composable () -> Unit)? = null,
    /** M8：资金管理页内容（增资/撤资/校准；null = 占位页）。 */
    fundsPageContent: (@Composable () -> Unit)? = null,
    /** M7 补口：顶栏手动同步（null = 不显示按钮；PRD 故事 4.3 + ia.md 顶栏规范）。 */
    onManualSync: (() -> Unit)? = null,
    /** 顶栏手动同步进行中（按钮置「同步中…」并禁用）。 */
    manualSyncing: Boolean = false,
    /** 顶栏手动同步结果 toast（与主壳 toast 合并展示）。 */
    manualSyncToast: WzToast? = null,
    onManualSyncToastDismiss: () -> Unit = {},
    /** M12 补口：顶栏手动刷新行情（null = 不显示按钮）。 */
    onRefreshQuotes: (() -> Unit)? = null,
    /** 顶栏手动刷新行情进行中。 */
    refreshQuotesBusy: Boolean = false,
    /** 顶栏手动刷新行情结果 toast（与同步结果/主壳 toast 合并展示）。 */
    refreshQuotesToast: WzToast? = null,
    onRefreshQuotesToastDismiss: () -> Unit = {},
    /** M11 T11.3：状态栏代理指示（直连 / 系统代理 + 悬停提示；PRD 4.2 验收 3）。 */
    proxyStatus: ProxyStatus = ProxyStatus.DEFAULT,
    /** M12：状态栏**原始数据**（文案在渲染期按当前语言派生，语言切换即时生效）。 */
    shellStatus: ShellStatus = ShellStatus(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val pnlScheme by viewModel.pnlScheme.collectAsState()
    val activeLanguage by viewModel.language.collectAsState()
    val page by viewModel.page.collectAsState()
    val coinDetailId by viewModel.coinDetailId.collectAsState()
    val toast by viewModel.toast.collectAsState()
    var showStartupNotice by remember { mutableStateOf(startupNotice != null) }
    // 键盘焦点编排（2026-09-15 人工走查反馈）：
    // ① 选中导航项后焦点**进入页面内容**，不必再 Tab 逐个穿过顶栏（进入即用）；
    // ② 页面内未被控件消费的 Esc / ↑ / ↓ 把焦点交回侧边栏当前项，回到「侧边栏→顶栏→页面」外壳循环；
    // ③ 侧边栏内 ↑ / ↓ 在导航项之间移动。
    val pageEntryFocus = remember { FocusRequester() }
    // 页面自管入口焦点（DEF-27）：页面把 requester 附在首个可聚焦控件上，主壳优先请求它，
    // 避免依赖 Compose 子树遍历的「猜第一个可聚焦控件」（实测会落到页面中部的输入框）
    val pageEntryState = remember { PageEntryFocusState() }
    // DEF-47：开发期 UI 开关（构建期注入，默认 false）——决定侧边栏与键盘焦点序是否含组件走查页
    val devUi = LocalDevUi.current
    val navPages = remember(devUi) { navFocusOrder(devUi) }
    val navFocusRequesters = remember(navPages) { navPages.associateWith { FocusRequester() } }
    var lastEntryKey by remember { mutableStateOf<String?>(null) }
    var reentryNonce by remember { mutableStateOf(0) }
    val entryKey = page.name + "/" + (coinDetailId ?: "")
    // 首次组合不抢焦点（启动时焦点仍在侧边栏，与键盘走查记录一致）；此后每次切页/进入详情子页、
    // 或对当前项再次回车（reentryNonce）都把焦点交给页面内容。
    LaunchedEffect(entryKey, reentryNonce) {
        if (lastEntryKey != null) {
            val claimed = pageEntryState.hasClaim() &&
                runCatching { pageEntryState.requester.requestFocus() }.getOrDefault(false)
            if (!claimed) runCatching { pageEntryFocus.requestFocus() }
        }
        lastEntryKey = entryKey
    }

    WuzhuTheme(themeMode = themeMode, pnlScheme = pnlScheme, language = activeLanguage) {
        val colors = WzTheme.colors
        Box(modifier = modifier.fillMaxSize().background(colors.bg).testTag("main-shell")) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Sidebar(
                        currentPage = page,
                        onSelect = { selected ->
                            viewModel.selectPage(selected)
                            // 对当前项再次回车 = 直接进入页面内容（无需先切页再 Tab）
                            if (selected == page) reentryNonce++
                        },
                        accountArea = accountArea,
                        navFocusRequesters = navFocusRequesters,
                        navPages = navPages,
                    )
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            title = page.label,
                            themeMode = themeMode,
                            onToggleTheme = viewModel::toggleTheme,
                            onManualSync = onManualSync,
                            manualSyncing = manualSyncing,
                            onRefreshQuotes = onRefreshQuotes,
                            refreshQuotesBusy = refreshQuotesBusy,
                            status = shellStatus,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                // 页面进入焦点目标：优先用页面声明的入口控件（DEF-27），
                                // 容器自身**不加** focusable，避免多出一个无焦点环的 Tab 停靠点（DEF-13 教训）。
                                .focusRequester(pageEntryFocus)
                                // 冒泡阶段：页面内控件已消费的键（输入框方向键、弹层 Esc）不受影响
                                .onKeyEvent { event -> handlePageExitKey(event, page, navFocusRequesters) },
                        ) {
                            CompositionLocalProvider(LocalPageEntryFocus provides pageEntryState) {
                            // 窗口档（DEF-31）：布局按断点决定列宽/字号/弹窗策略，单一来源
                            ProvideWzWindowClass(modifier = Modifier.fillMaxSize()) {
                            PageHost(
                                page = page,
                                coinDetailId = coinDetailId,
                                accountName = accountName,
                                viewModel = viewModel,
                                settingsPageContent = settingsPageContent,
                                watchPageContent = watchPageContent,
                                dashboardPageContent = dashboardPageContent,
                                assetsPageContent = assetsPageContent,
                                coinDetailPageContent = coinDetailPageContent,
                                transactionsPageContent = transactionsPageContent,
                                fundsPageContent = fundsPageContent,
                            )
                            }
                            }
                        }
                    }
                }
                WzStatusBar(
                    proxyStatus = proxyStatus,
                    // 文案在渲染期按当前语言派生（语言切换即时生效，见 ShellStatusText）
                    syncStatus = shellStatus.syncText(syncing = manualSyncing),
                    dataSource = shellStatus.dataSourceText(),
                    version = shellStatus.version,
                    notice = shellStatus.noticeText(),
                    noticeWarn = shellStatus.noticeIsWarn(),
                    offline = shellStatus.marketOffline,
                )
            }
            // 顶栏同步/刷新结果与主壳 toast 共用宿主（同一位置；顶栏动作结果优先）
            WzToastHost(
                toast = manualSyncToast ?: refreshQuotesToast ?: toast,
                onDismiss = {
                    onManualSyncToastDismiss()
                    onRefreshQuotesToastDismiss()
                    viewModel.dismissToast()
                },
            )
            if (startupNotice != null && showStartupNotice) {
                StartupNoticeModal(notice = startupNotice) { showStartupNotice = false }
            }
        }
    }
}

/** 页槽分发（一级页面 + 资产列表内的币种详情子页）。 */
@Composable
private fun PageHost(
    page: ShellPage,
    coinDetailId: String?,
    accountName: String,
    viewModel: ShellViewModel,
    settingsPageContent: (@Composable (ShellViewModel) -> Unit)?,
    watchPageContent: (@Composable () -> Unit)?,
    dashboardPageContent: (@Composable (accountName: String) -> Unit)?,
    assetsPageContent: (@Composable (onOpenCoin: (String) -> Unit) -> Unit)?,
    coinDetailPageContent: (@Composable (cgId: String, onBack: () -> Unit) -> Unit)?,
    transactionsPageContent: (@Composable () -> Unit)?,
    fundsPageContent: (@Composable () -> Unit)?,
) {
    when (page) {
        ShellPage.GALLERY -> ComponentGallery(viewModel)
        ShellPage.DASHBOARD -> SlotOrPlaceholder(page, dashboardPageContent?.let { slot -> { slot(accountName) } })
        ShellPage.ASSETS -> {
            if (coinDetailId != null && coinDetailPageContent != null) {
                Box(modifier = Modifier.fillMaxSize().testTag("page-COIN-DETAIL")) {
                    coinDetailPageContent(coinDetailId, viewModel::closeCoinDetail)
                }
            } else {
                SlotOrPlaceholder(page, assetsPageContent?.let { slot -> { slot(viewModel::openCoinDetail) } })
            }
        }
        ShellPage.SETTINGS -> {
            val slot = settingsPageContent
            if (slot == null) {
                PlaceholderPage(page)
            } else {
                Box(modifier = Modifier.fillMaxSize().testTag("page-" + page.name)) { slot(viewModel) }
            }
        }
        ShellPage.QUOTES -> SlotOrPlaceholder(page, watchPageContent)
        ShellPage.TRANSACTIONS -> SlotOrPlaceholder(page, transactionsPageContent)
        ShellPage.FUNDS -> SlotOrPlaceholder(page, fundsPageContent)
    }
}

@Composable
private fun SlotOrPlaceholder(page: ShellPage, slot: (@Composable () -> Unit)?) {
    if (slot == null) {
        PlaceholderPage(page)
    } else {
        Box(modifier = Modifier.fillMaxSize().testTag("page-" + page.name)) { slot() }
    }
}

/** 启动安全说明模态（T1.1「无钥匙串降级提示」）。 */
@Composable
private fun StartupNoticeModal(notice: String, onDismiss: () -> Unit) {
    val colors = WzTheme.colors
    WzModal(title = shellStrings.startupNoticeTitle, onDismiss = onDismiss, testTag = "startup-notice") {
        Text(
            text = notice,
            color = colors.ink2,
            style = WzTheme.typography.body,
        )
        WzButton(
            text = shellStrings.startupNoticeOk,
            onClick = onDismiss,
            variant = WzButtonVariant.Primary,
            modifier = Modifier.padding(top = 16.dp).align(Alignment.End),
            testTag = "startup-notice-ok",
        )
    }
}

@Composable
private fun Sidebar(
    currentPage: ShellPage,
    onSelect: (ShellPage) -> Unit,
    accountArea: @Composable () -> Unit,
    navFocusRequesters: Map<ShellPage, FocusRequester>,
    /** DEF-47：开发构建才渲染组件走查页入口（正式构建侧边栏 = ia.md 的六页）。 */
    navPages: List<ShellPage>,
) {
    val colors = WzTheme.colors
    // 当前获得焦点的导航项（供 ↑/↓ 计算相邻项；鼠标点击不入此状态也无需入）
    var focusedNavPage by remember { mutableStateOf<ShellPage?>(null) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .border(0.dp, colors.line)
            .padding(vertical = 12.dp)
            .onKeyEvent { event -> handleSidebarArrowKey(event, focusedNavPage, navFocusRequesters, navPages) }
            .testTag("sidebar"),
    ) {
        Text(
            text = shellStrings.appName,
            color = colors.ink,
            style = WzTheme.typography.pageTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = shellStrings.tagline,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
        // 正式六页（ia.md 顺序）
        navPages.forEach { page ->
            if (page == ShellPage.GALLERY) return@forEach
            SidebarNavItem(
                label = page.label,
                active = page == currentPage,
                onClick = { onSelect(page) },
                testTag = "nav-" + page.name,
                focusRequester = navFocusRequesters[page],
                onFocused = { focusedNavPage = page },
            )
        }
        Box(modifier = Modifier.weight(1f))
        // 组件走查页入口（DEF-47）：只在开发构建出现（`-Pwuzhufolio.devUi=true`），正式版无此项
        if (ShellPage.GALLERY in navPages) {
            SidebarNavItem(
                label = shellStrings.navGallery,
                active = currentPage == ShellPage.GALLERY,
                onClick = { onSelect(ShellPage.GALLERY) },
                testTag = "nav-" + ShellPage.GALLERY.name,
                focusRequester = navFocusRequesters[ShellPage.GALLERY],
                onFocused = { focusedNavPage = ShellPage.GALLERY },
            )
        }
        accountArea()
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
) {
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
            // DEF-13 教训：clickable 自带唯一焦点目标，这里不得再加 .focusable()；
            // focusRequester/onFocusChanged 必须排在 clickable **之前**（焦点节点只向链上更外层上报）。
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
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
private fun TopBar(
    title: String,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onManualSync: (() -> Unit)?,
    manualSyncing: Boolean,
    onRefreshQuotes: (() -> Unit)?,
    refreshQuotesBusy: Boolean,
    status: ShellStatus,
) {
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
        // 行情数据源徽章（ia.md §1.1 顶栏：CoinGecko/CoinMarketCap/上次成功时间戳）
        Text(
            text = status.dataSourceText(),
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(end = 12.dp).testTag("topbar-datasource"),
        )
        // 手动刷新行情（PRD 故事 3.2-6；ia.md 顶栏「手动刷新行情按钮」）
        if (onRefreshQuotes != null) {
            WzButton(
                text = if (refreshQuotesBusy) shellStrings.refreshingShort else shellStrings.refreshQuotesShort,
                onClick = onRefreshQuotes,
                variant = WzButtonVariant.Secondary,
                enabled = !refreshQuotesBusy,
                testTag = "topbar-refresh-quotes",
            )
            Box(modifier = Modifier.width(8.dp))
        }
        // 手动同步交易（PRD 故事 4.3：任何页面常驻可达；同步中指示）
        if (onManualSync != null) {
            WzButton(
                text = if (manualSyncing) shellStrings.syncing else shellStrings.manualSync,
                onClick = onManualSync,
                variant = WzButtonVariant.Secondary,
                enabled = !manualSyncing,
                testTag = "topbar-sync",
            )
            Box(modifier = Modifier.width(12.dp))
        }
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

/** 未接线页占位（P4 垂直切片模块页均已接线，此分支仅剩开发期用途）。 */
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
            text = shellStrings.placeholder,
            color = colors.ink2,
            style = WzTheme.typography.body,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
