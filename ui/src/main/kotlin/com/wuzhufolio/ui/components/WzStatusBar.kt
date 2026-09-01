package com.wuzhufolio.ui.components

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
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 底部状态栏（design-tokens §4.1，约 28dp）：
 * 左 = 网络代理指示（PRD 故事 4.2，T11.3 接真实检测）；中 = 同步状态 + 数据源徽章；右 = 版本号。
 * M0 全部为占位指示，真实状态随 M5/M6/M11 接入。
 */
@Composable
fun WzStatusBar(
    proxyActive: Boolean,
    syncStatus: String,
    dataSource: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.surface2)
            .border(0.dp, colors.line)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 代理指示：绿点 = 直连，黄铜点 = 代理（文案同时表达，非颜色单载体）
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (proxyActive) colors.warn else colors.gain, CircleShape)
                .testTag("proxy-dot"),
        )
        Text(
            text = if (proxyActive) "代理" else "直连",
            color = colors.ink3,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(start = 6.dp).testTag("proxy-indicator"),
        )
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
