package com.wuzhufolio.ui.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WzTheme

/** 通知级别（与 app 层 `NoticeLevel` 同构；ui 模块不反向依赖 app）。 */
enum class TrayNoticeLevel { INFO, WARNING, ERROR }

/**
 * 桌面通知卡片（**DEF-49**）：托盘手动动作与背景事件共用的可见反馈。
 *
 * 为什么需要它：0.1.0 的通知只走 AWT `TrayIcon.displayMessage`，而 Linux（GNOME/SNI 代理）下
 * XEmbed 气泡没有承载 —— 人工实测「托盘点『立即同步』后什么都没发生」，误判为「没执行同步」。
 * 该卡片由 app 层的独立小窗（`DesktopToastWindow`）承载，三平台一致、离屏可测。
 *
 * 语义：`liveRegion = Polite`（读屏朗读、不抢焦点）；级别用**色点 + 文案**双重表达
 * （PRD §6：颜色不作为唯一信息载体）。
 */
@Composable
fun TrayNoticeCard(
    title: String,
    message: String,
    level: TrayNoticeLevel,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    val dot = when (level) {
        TrayNoticeLevel.INFO -> colors.accent
        TrayNoticeLevel.WARNING -> colors.warn
        TrayNoticeLevel.ERROR -> colors.loss
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("tray-notice"),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(text = title, color = colors.ink, style = WzTheme.typography.bodyStrong)
                Text(text = message, color = colors.ink2, style = WzTheme.typography.caption)
            }
        }
    }
}

/** 提示窗存活时长（毫秒；比应用内 toast 略长，给用户读完「正在…」与结果的时间）。 */
const val TRAY_NOTICE_VISIBLE_MS: Long = 3200L
