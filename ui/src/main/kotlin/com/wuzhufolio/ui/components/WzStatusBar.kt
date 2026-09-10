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
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 底部状态栏（design-tokens §4.1，约 28dp）：
 * 左 = 网络代理指示（PRD 故事 4.2 / interaction.md §1.1，M11 T11.3 接真实检测）；
 * 中 = 同步状态 + 数据源徽章；右 = 版本号。
 *
 * 代理指示口径（interaction.md §1.1）：常驻「代理：系统代理」/「直连」**文字**指示（非颜色单载体），
 * 悬停提示「当前网络通过系统代理连接」。颜色仅作辅助：绿点 = 直连，黄铜点 = 系统代理。
 *
 * 悬停用 `TooltipArea`（Compose Desktop 官方 tooltip）：纯展示、无输入无焦点，
 * 不涉及 `AGENTS.md §7.3` 的 Popup 键盘输入约束（该约束针对承载交互与文本输入的弹层）。
 * 同步状态与数据源文案的真实数据源归 M12（M5 遗留登记）。
 */
@OptIn(ExperimentalFoundationApi::class) // TooltipArea = Compose Desktop 官方 tooltip，仍标实验但为当前唯一悬停 API
@Composable
fun WzStatusBar(
    proxyStatus: ProxyStatus,
    syncStatus: String,
    dataSource: String,
    version: String,
    modifier: Modifier = Modifier,
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
                        text = proxyStatus.tooltip,
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
                    text = proxyStatus.indicator,
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
            Text(text = syncStatus, color = colors.ink3, style = WzTheme.typography.caption)
            Text(
                text = dataSource,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(start = 12.dp).testTag("datasource-badge"),
            )
        }
        Text(
            text = version,
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.testTag("version-label"),
        )
    }
}
