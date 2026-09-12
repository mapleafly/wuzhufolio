package com.wuzhufolio.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.TwentyFourHour
import com.wuzhufolio.domain.engine.PortfolioMetrics
import com.wuzhufolio.domain.portfolio.PortfolioService
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.i18n.WzFormat
import com.wuzhufolio.ui.i18n.portfolioStrings
import com.wuzhufolio.ui.theme.WzTheme
import java.time.Instant

/**
 * 仪表盘（M12 T12.1 · ia.md §2.4 · PRD §7.2 模块 1 · 原型 `pageDashboard`）。
 *
 * 覆盖内容：总览卡片（净值 / 24h 盈亏 / ROI / 投入本金（净）+ 可用现金 / 总收益 / 累计已实现盈亏）
 * + 资产分布环形图（小额阈值归并「其他」、点击扇区高亮与浮窗）+ 安全与隐私说明 + 手动刷新行情。
 *
 * 数据一律来自 [PortfolioService.snapshot]（全量重放派生，单一真源）；本页不做任何写入。
 */
@Composable
fun DashboardPage(
    portfolioService: PortfolioService,
    refreshService: MarketRefreshService,
    generalSettings: GeneralSettingsService,
    /** 当前账户名（副标题「账户 X · …」；PRD §6 账户清晰性）。 */
    accountName: String,
    /** 最近备份时刻（null = 未知/从未备份，则不展示该行；原型「安全与隐私」面板末行）。 */
    lastBackupAt: Instant? = null,
    modifier: Modifier = Modifier,
) {
    val vm = remember { PortfolioViewModel(portfolioService, refreshService, generalSettings) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors
    var selectedSlice by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().testTag("page-DASHBOARD")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(text = portfolioStrings.dashboardTitle, color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = portfolioStrings.dashboardSubtitle(accountName, state.fiat),
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            val metrics = state.snapshot?.metrics
            val unreliable = state.snapshot?.rows?.count { !it.costReliable } ?: 0
            if (unreliable > 0) {
                Text(
                    text = portfolioStrings.costUnreliableNotice(unreliable),
                    color = colors.warn,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(bottom = 10.dp).testTag("dashboard-cost-notice"),
                )
            }
            if (state.loading) {
                Text(
                    text = portfolioStrings.loading,
                    color = colors.ink3,
                    style = WzTheme.typography.body,
                    modifier = Modifier.testTag("dashboard-loading"),
                )
            }
            OverviewCards(metrics = metrics, fiat = state.fiat, change24h = state.snapshot?.change24h)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .background(colors.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    PanelTitle(portfolioStrings.distributionTitle)
                    if (state.distribution.isEmpty()) {
                        EmptyHint(portfolioStrings.donutEmpty, testTag = "donut-empty")
                    } else {
                        AssetDonut(
                            slices = state.distribution,
                            totalValue = metrics?.netValueFiat ?: java.math.BigDecimal.ZERO,
                            fiat = state.fiat,
                            selectedId = selectedSlice,
                            onSelect = { selectedSlice = it },
                        )
                    }
                }
                SecurityPanel(
                    lastBackupAt = lastBackupAt,
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WzButton(
                    text = if (state.refreshing) portfolioStrings.refreshing else portfolioStrings.refreshQuotes,
                    onClick = vm::refreshQuotes,
                    variant = WzButtonVariant.Primary,
                    enabled = !state.refreshing,
                    testTag = "dashboard-refresh",
                )
            }
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }
}

/** 总览卡片两组（原型：第一行 4 张大卡 + 第二行 3 张小卡）。 */
@Composable
private fun OverviewCards(metrics: PortfolioMetrics?, fiat: String, change24h: TwentyFourHour.Result?) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dashboard-cards"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatCard(
            label = portfolioStrings.cardNetValue,
            value = WzFormat.money(metrics?.netValueFiat, fiat),
            modifier = Modifier.weight(1f),
            testTag = "card-net-value",
        )
        StatCard(
            label = portfolioStrings.card24h,
            value = signedMoneyOrDash(change24h?.pnlFiat, fiat),
            modifier = Modifier.weight(1f),
            delta = change24hSubtitle(change24h),
            deltaColor = pnlColor(change24h?.pnlFiat),
            testTag = "card-24h",
        )
        StatCard(
            label = portfolioStrings.cardRoi,
            value = WzFormat.signedPercent(metrics?.roiPercent),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.cumulativeDeposits(WzFormat.money(metrics?.cumulativeDepositsFiat, fiat)),
            deltaColor = pnlColor(metrics?.roiPercent),
            testTag = "card-roi",
        )
        StatCard(
            label = portfolioStrings.cardInvestedNet,
            value = WzFormat.money(metrics?.investedNetFiat, fiat),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.cumulativeWithdrawals(WzFormat.money(metrics?.cumulativeWithdrawalsFiat, fiat)),
            testTag = "card-invested-net",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatCard(
            label = portfolioStrings.cardAvailableCash,
            value = WzFormat.money(metrics?.availableCashFiat, fiat),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.cashSubLabel,
            small = true,
            testTag = "card-cash",
        )
        StatCard(
            label = portfolioStrings.cardTotalReturn,
            value = signedMoneyOrDash(metrics?.totalReturnFiat, fiat),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.totalReturnFormula,
            deltaColor = pnlColor(metrics?.totalReturnFiat),
            small = true,
            testTag = "card-total-return",
        )
        StatCard(
            label = portfolioStrings.cardRealizedPnl,
            value = WzFormat.signedMoney(metrics?.realizedPnlFiat, fiat),
            modifier = Modifier.weight(1f),
            delta = portfolioStrings.realizedSubLabel,
            deltaColor = pnlColor(metrics?.realizedPnlFiat),
            small = true,
            testTag = "card-realized",
        )
    }
}

/** 安全与隐私面板（原型 `pageDashboard` 右栏；PRD §6「安全感与信任建立」）。 */
@Composable
private fun SecurityPanel(lastBackupAt: Instant?, modifier: Modifier = Modifier) {
    val colors = WzTheme.colors
    Column(modifier = modifier) {
        PanelTitle(portfolioStrings.securityTitle)
        listOf(
            portfolioStrings.securityLine1,
            portfolioStrings.securityLine2,
            portfolioStrings.securityLine3,
            portfolioStrings.securityLine4,
        ).forEach { line ->
            Text(
                text = line,
                color = colors.ink2,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (lastBackupAt != null) {
            Text(
                text = portfolioStrings.lastBackup(WzFormat.dateTime(lastBackupAt)),
                color = colors.ink2,
                style = WzTheme.typography.caption,
                modifier = Modifier.testTag("last-backup"),
            )
        }
    }
}

/** 24h 盈亏副行：百分比 + 覆盖标注 + 混合数据源标注（interaction.md §2.3）。 */
internal fun change24hSubtitle(result: TwentyFourHour.Result?): String? {
    if (result == null) return null
    val parts = ArrayList<String>()
    parts += WzFormat.signedPercent(result.pct)
    if (result.total > 0 && result.covered < result.total) {
        parts += portfolioStrings.coverage(result.covered, result.total)
    }
    if (result.mixedSource) parts += portfolioStrings.mixedSource
    return parts.joinToString(" · ")
}

/** 金额或 "--"（带符号）。 */
internal fun signedMoneyOrDash(value: java.math.BigDecimal?, fiat: String): String =
    if (value == null) WzFormat.DASH else WzFormat.signedMoney(value, fiat)
