package com.wuzhufolio.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.market.MarketKeyStatus
import com.wuzhufolio.domain.market.MarketRefreshResult
import com.wuzhufolio.domain.market.MarketRefreshService
import com.wuzhufolio.domain.market.MarketSettingsService
import com.wuzhufolio.domain.market.PriceSource
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzSelect
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.theme.WzTheme
import java.net.URI

/**
 * 设置页 · 行情与同步分组 · 行情数据源段（T5.5 → M10 嵌入完整设置页）：
 * 原型「行情与同步」分组内容 = 刷新频率（下拉）+ CG/CMC Key 行 + 数据源指示/上次刷新/立即刷新。
 * M10 起由 SettingsPage 单页分组合流挂载（宿主 SettingsSectionsHost 已随 M10 移除——M5/M6 遗留闭环）。
 */
@Composable
fun MarketSettingsSection(
    settingsService: MarketSettingsService,
    refreshService: MarketRefreshService,
    modifier: Modifier = Modifier,
) {
    val vm = remember { MarketSettingsViewModel(settingsService, refreshService) }
    DisposableEffect(vm) {
        onDispose { vm.dispose() }
    }
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }

    Box(modifier = modifier.testTag("market-settings")) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MarketGroup(title = MarketCopy.GROUP_TITLE, testTag = "group-market") {
                FrequencyRow(
                    minutes = state.refreshMinutes,
                    onSelect = vm::selectRefreshMinutes,
                )
                KeyRow(
                    label = MarketCopy.CG_ROW_LABEL,
                    description = if (state.keyStatus.cgConfigured) {
                        MarketCopy.CG_ROW_SUB_CONFIGURED
                    } else {
                        MarketCopy.CG_ROW_SUB_KEYLESS
                    },
                    actionText = actionText(state.keyStatus.cgConfigured),
                    testTag = "row-cg-key",
                    onClick = { input = ""; vm.openDialog(MarketKeyDialog.CG) },
                )
                KeyRow(
                    label = MarketCopy.CMC_ROW_LABEL,
                    description = if (state.keyStatus.cmcConfigured) {
                        MarketCopy.CMC_ROW_SUB_CONFIGURED
                    } else {
                        MarketCopy.CMC_ROW_SUB_KEYLESS
                    },
                    actionText = actionText(state.keyStatus.cmcConfigured),
                    testTag = "row-cmc-key",
                    onClick = { input = ""; vm.openDialog(MarketKeyDialog.CMC) },
                )
            }
            SourceStatusStrip(
                keyStatus = state.keyStatus,
                lastRefresh = state.lastRefresh,
                refreshBusy = state.refreshBusy,
                onRefresh = vm::refreshNow,
            )
        }
        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }

    when (state.dialog) {
        MarketKeyDialog.NONE -> Unit
        MarketKeyDialog.CG -> KeyModal(
            title = MarketCopy.CG_MODAL_TITLE,
            configured = state.keyStatus.cgConfigured,
            hint = if (state.keyStatus.cgConfigured) MarketCopy.CG_MODAL_CONFIGURED else MarketCopy.CG_MODAL_KEYLESS,
            registerHint = MarketCopy.CG_REGISTER_HINT,
            registerUrl = MarketCopy.CG_REGISTER_URL,
            input = input,
            onInput = { input = it },
            error = state.dialogError,
            busy = state.dialogBusy,
            saveLabel = MarketCopy.BTN_SAVE_CG,
            onSave = { vm.saveCgKey(input) },
            onRemove = vm::removeCgKey,
            onClose = { vm.closeDialog(); input = "" },
        )
        MarketKeyDialog.CMC -> KeyModal(
            title = MarketCopy.CMC_MODAL_TITLE,
            configured = state.keyStatus.cmcConfigured,
            hint = if (state.keyStatus.cmcConfigured) MarketCopy.CMC_MODAL_CONFIGURED else MarketCopy.CMC_MODAL_KEYLESS,
            registerHint = MarketCopy.CMC_REGISTER_HINT,
            registerUrl = MarketCopy.CMC_REGISTER_URL,
            input = input,
            onInput = { input = it },
            error = state.dialogError,
            busy = state.dialogBusy,
            saveLabel = MarketCopy.BTN_SAVE_CMC,
            onSave = { vm.saveCmcKey(input) },
            onRemove = vm::removeCmcKey,
            onClose = { vm.closeDialog(); input = "" },
        )
    }
}

@Composable
private fun MarketGroup(title: String, testTag: String, content: @Composable () -> Unit) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(testTag),
    ) {
        Text(text = title, color = colors.ink2, style = WzTheme.typography.body)
        Column(modifier = Modifier.padding(top = 4.dp)) { content() }
    }
}

/** 设置行（原型 settingsRow）：标签 + 说明 + 动作按钮。 */
@Composable
private fun KeyRow(
    label: String,
    description: String,
    actionText: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = colors.ink, style = WzTheme.typography.body)
            Text(
                text = description,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        WzButton(
            text = actionText,
            onClick = onClick,
            variant = WzButtonVariant.Secondary,
            testTag = testTag + "-action",
        )
    }
}

/** 刷新频率行（M10 组件化：M5 分段按钮 → 下拉，M5 §5-6-② 登记项闭环）。 */
@Composable
private fun FrequencyRow(minutes: Int, onSelect: (Int) -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("row-freq"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = MarketCopy.FREQ_ROW_LABEL, color = colors.ink, style = WzTheme.typography.body)
            Text(
                text = MarketCopy.FREQ_ROW_SUB,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        WzSelect(
            options = listOf(5, 15, 30),
            selected = minutes,
            onSelect = onSelect,
            labelOf = { MarketCopy.freqOptionLabel(it) },
            testTag = "freq-select",
        )
    }
}

/** 数据源指示 + 上次价格 + 手动刷新（原型状态栏语义的子集；全量状态栏随 M12）。 */
@Composable
private fun SourceStatusStrip(
    keyStatus: MarketKeyStatus,
    lastRefresh: MarketRefreshResult?,
    refreshBusy: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = WzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("market-source-status"),
    ) {
        // 数据源指示以「当前 Key 配置」为基准（保存即生效，修复轮）；刷新注解区分从未刷新/尚未成功
        val sourceBase = when (lastRefresh?.source) {
            PriceSource.COINMARKETCAP -> MarketCopy.SOURCE_CMC
            else -> if (keyStatus.cgConfigured) MarketCopy.SOURCE_CG_KEYED else MarketCopy.SOURCE_CG_KEYLESS
        }
        val refreshNote = MarketCopy.refreshNote(
            hasResult = lastRefresh != null,
            hasSuccess = lastRefresh?.at != null,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = MarketCopy.SOURCE_PREFIX, color = colors.ink3, style = WzTheme.typography.caption)
            Text(
                text = sourceBase + refreshNote,
                color = colors.ink,
                style = WzTheme.typography.body,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        val quota = lastRefresh?.quotaPercentUsed
        if (quota != null && quota >= 80) {
            Text(
                text = MarketCopy.quotaHint(quota),
                color = colors.warn,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = MarketCopy.lastRefreshTime(timeOrDash(lastRefresh?.at)),
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.weight(1f),
            )
            WzButton(
                text = if (refreshBusy) MarketCopy.REFRESHING else MarketCopy.REFRESH_BUTTON,
                onClick = onRefresh,
                enabled = !refreshBusy,
                testTag = "market-refresh-now",
            )
        }
    }
}

private fun timeOrDash(at: java.time.Instant?): String {
    if (at == null) return MarketCopy.NO_REFRESH_YET
    val zoned = at.atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(zoned.hour, zoned.minute)
}

private fun actionText(configured: Boolean): String =
    if (configured) MarketCopy.STATUS_CONFIGURED else MarketCopy.STATUS_UNCONFIGURED

/** Key 弹窗（原型 openCgKey/openCmcKey：说明/注册链接/保存并切换/移除）。 */
@Composable
private fun KeyModal(
    title: String,
    configured: Boolean,
    hint: String,
    registerHint: String,
    registerUrl: String,
    input: String,
    onInput: (String) -> Unit,
    error: String?,
    busy: Boolean,
    saveLabel: String,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = WzTheme.colors
    // GUI 共性约束 7.3-②：含输入框的弹窗打开即聚焦首输入框（键盘/IME 立即可用，无需先点按）
    val fieldFocus = remember { FocusRequester() }
    WzModal(
        title = title,
        onDismiss = onClose,
        width = 460.dp,
        testTag = "market-key-modal",
        initialFocusRequester = fieldFocus,
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = hint, color = colors.ink2, style = WzTheme.typography.caption)
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RegisterHint(text = registerHint, url = registerUrl)
            }
            WzTextField(
                value = input,
                onValueChange = onInput,
                label = MarketCopy.INPUT_LABEL,
                placeholder = "",
                isPassword = true,
                error = error,
                modifier = Modifier.padding(top = 8.dp),
                testTag = "market-key-input",
                fieldFocusRequester = fieldFocus,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (configured) {
                    WzButton(
                        text = MarketCopy.BTN_REMOVE_KEY,
                        onClick = onRemove,
                        variant = WzButtonVariant.Danger,
                        enabled = !busy,
                        testTag = "market-key-remove",
                    )
                    WzButton(
                        text = MarketCopy.BTN_CANCEL,
                        onClick = onClose,
                        variant = WzButtonVariant.Secondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    WzButton(
                        text = MarketCopy.BTN_CANCEL,
                        onClick = onClose,
                        variant = WzButtonVariant.Secondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    WzButton(
                        text = saveLabel,
                        onClick = onSave,
                        enabled = !busy,
                        testTag = "market-key-save",
                    )
                }
            }
        }
    }
}

/** 注册链接（浏览器打开；桌面环境 AWT Desktop，失败静默——错误体验随 M12 提示）。 */
@Composable
private fun RegisterHint(text: String, url: String) {
    val colors = WzTheme.colors
    Text(
        text = text,
        color = colors.accent,
        style = WzTheme.typography.caption,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clickable {
                runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
            }
            .padding(vertical = 2.dp),
    )
}
