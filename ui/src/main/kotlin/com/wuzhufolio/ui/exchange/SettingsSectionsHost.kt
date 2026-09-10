package com.wuzhufolio.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.theme.WzTheme

/**
 * 设置页内容占位宿主（M6 · 挂载到 MainShell SETTINGS 页）。
 *
 * 现状：M5 的 MarketSettingsPage 以「独立设置占位宿主」托管行情数据源分组（M5 §5-6-④ 注「M10 将并入完整设置页」）；
 * M6 交易所同步的 API 管理同样属设置分组（ia.md §2.14 入口 = 设置 → API 管理）；M7 增加手续费费率分组
 * （T7.2 端到端补口，最小 CRUD）。M10 建完整设置页前，本宿主以分段切换挂载三组内容，默认选中行情数据源
 * 保持 M5 通过时的首屏口径；M10 整页接管后移除本宿主、按原型单页分组合流（登记见模块记录 M6 §5/M7 §8）。
 */
@Composable
fun SettingsSectionsHost(
    marketContent: @Composable () -> Unit,
    apiContent: @Composable () -> Unit,
    feeContent: (@Composable () -> Unit)? = null,
    /** M9：数据管理（备份恢复 + CSV 明文导出；null = 不显示分组）。 */
    backupContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = WzTheme.colors
    var section by remember { mutableStateOf(SettingsSection.MARKET) }
    Column(modifier = modifier.fillMaxSize().testTag("settings-sections-host")) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp).testTag("settings-section-switch"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "设置分组：", color = colors.ink3, style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 8.dp))
            WzButton(
                text = if (section == SettingsSection.MARKET) "● 行情数据源" else "○ 行情数据源",
                onClick = { section = SettingsSection.MARKET },
                variant = if (section == SettingsSection.MARKET) WzButtonVariant.Primary
                else WzButtonVariant.Secondary,
                testTag = "settings-section-market",
            )
            WzButton(
                text = if (section == SettingsSection.API) "● API 管理" else "○ API 管理",
                onClick = { section = SettingsSection.API },
                variant = if (section == SettingsSection.API) WzButtonVariant.Primary
                else WzButtonVariant.Secondary,
                testTag = "settings-section-api",
            )
            if (feeContent != null) {
                WzButton(
                    text = if (section == SettingsSection.FEE) "● 手续费" else "○ 手续费",
                    onClick = { section = SettingsSection.FEE },
                    variant = if (section == SettingsSection.FEE) WzButtonVariant.Primary
                    else WzButtonVariant.Secondary,
                    testTag = "settings-section-fee",
                )
            }
            if (backupContent != null) {
                WzButton(
                    text = if (section == SettingsSection.DATA) "● 数据管理" else "○ 数据管理",
                    onClick = { section = SettingsSection.DATA },
                    variant = if (section == SettingsSection.DATA) WzButtonVariant.Primary
                    else WzButtonVariant.Secondary,
                    testTag = "settings-section-data",
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                SettingsSection.MARKET -> marketContent()
                SettingsSection.API -> apiContent()
                SettingsSection.FEE -> if (feeContent != null) feeContent() else marketContent()
                SettingsSection.DATA -> if (backupContent != null) backupContent() else marketContent()
            }
        }
    }
}

private enum class SettingsSection { MARKET, API, FEE, DATA }
