package com.wuzhufolio.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.proxy.ProxyMode
import com.wuzhufolio.domain.proxy.ProxyStatus
import com.wuzhufolio.ui.i18n.shellStrings
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 底部状态栏（design-tokens §4.1，约 28dp；M12 T12.1 数据源接入）：
 * 左 = 网络代理指示（PRD 故事 4.2 / interaction.md §1.1，M11 T11.3 接真实检测）；
 * 中 = 同步状态 + 行情数据源徽章 + 异常提示（额度/限流/备份提醒）+ 断链标记；
 * 右 = 版本号。
 *
 * 代理指示口径（interaction.md §1.1）：常驻「代理：系统代理」/「直连」**文字**指示（非颜色单载体），
 * 悬停提示「当前网络通过系统代理连接」。颜色仅作辅助：绿点 = 直连，黄铜点 = 系统代理。
 * 文案经 i18n 目录（M12 T12.4），领域层 [ProxyStatus] 只承载状态不承载文案。
 *
 * 网络断开（interaction.md §1.1 N1）：[offline] 时在数据源前显示「网络断开」文字标记（红字，非仅颜色）。
 *
 * 悬停用 `TooltipArea`（Compose Desktop 官方 tooltip）：纯展示、无输入无焦点，
 * 不涉及 `AGENTS.md §7.3` 的 Popup 键盘输入约束（该约束针对承载交互与文本输入的弹层）。
 */
@OptIn(ExperimentalFoundationApi::class) // TooltipArea = Compose Desktop 官方 tooltip，仍标实验但为当前唯一悬停 API
@Composable
fun WzStatusBar(
    proxyStatus: ProxyStatus,
    syncStatus: String,
    dataSource: String,
    version: String,
    modifier: Modifier = Modifier,
    /** 异常/提醒提示（额度 80% / 429 频发 / 备份提醒；PRD 6.1、interaction.md §2.5）。 */
    notice: String? = null,
    /** true = 提示以警示色呈现（额度/限流）；false = 常规色（备份提醒）。 */
    noticeWarn: Boolean = false,
    /** 行情源网络不可达（N1 断链指示）。 */
    offline: Boolean = false,
) {
    val colors = WzTheme.colors
    val proxyActive = proxyStatus.mode == ProxyMode.SYSTEM
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.surface2)
            .border(0.dp, colors.line)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TooltipArea(
            tooltip = {
                Box(
                    modifier = Modifier
                        .background(colors.surface)
                        .border(1.dp, colors.line)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = shellStrings.proxyTooltip(proxyStatus),
                        color = colors.ink2,
                        style = WzTheme.typography.caption,
                    )
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (proxyActive) colors.warn else colors.gain, CircleShape)
                        .testTag("proxy-dot"),
                )
                Text(
                    text = shellStrings.proxyIndicator(proxyStatus),
                    color = colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(start = 6.dp).testTag("proxy-indicator"),
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (offline) {
                Text(
                    text = "⚠ " + shellStrings.networkOffline(),
                    color = colors.loss,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(end = 10.dp).testTag("offline-badge"),
                )
            }
            Text(text = syncStatus, color = colors.ink3, style = WzTheme.typography.caption)
            Text(
                text = dataSource,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(start = 12.dp).testTag("datasource-badge"),
            )
            if (notice != null) {
                Text(
                    text = notice,
                    color = if (noticeWarn) colors.warn else colors.ink3,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(start = 12.dp).testTag("status-notice"),
                )
            }
        }
        Text(
            text = version,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.testTag("version-label"),
        )
    }
}
