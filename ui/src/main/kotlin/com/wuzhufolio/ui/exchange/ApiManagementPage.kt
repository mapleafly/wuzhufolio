package com.wuzhufolio.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wuzhufolio.domain.exchange.ApiKeyInfo
import com.wuzhufolio.domain.exchange.ApiKeyInput
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncLogRow
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzButtonVariant
import com.wuzhufolio.ui.components.WzModal
import com.wuzhufolio.ui.components.WzTextField
import com.wuzhufolio.ui.components.WzToastHost
import com.wuzhufolio.ui.theme.WzTheme
import java.time.Instant

/**
 * API 管理页（T6.4 · task-breakdown §3 M6；挂载到设置页占位宿主，见 app/Main 组合）。
 * 原型 API 管理走查：列表（别名/交易所/掩码/最后同步/状态）+ 添加弹窗（验证后保存 + 保存即首次同步）+
 * 状态与同步记录展示（ia.md §2.14）；API 同步间隔 15/30/60（设置键 sync.interval_minutes）。
 */
@Composable
fun ApiManagementPage(
    service: ExchangeSyncService,
    modifier: Modifier = Modifier,
) {
    val vm = remember { ApiManagementViewModel(service) }
    DisposableEffect(vm) { onDispose { vm.dispose() } }
    val state by vm.state.collectAsState()
    val colors = WzTheme.colors

    Box(modifier = modifier.fillMaxSize().testTag("api-management")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(text = "API 管理（交易所同步）", color = colors.ink, style = WzTheme.typography.pageTitle)
            Text(
                text = ApiCopy.PAGE_SUB,
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            SyncIntervalRow(
                minutes = state.intervalMinutes,
                onSelect = vm::selectInterval,
            )

            Text(text = "已保存 API 密钥", color = colors.ink2, style = WzTheme.typography.body,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (state.keys.isEmpty()) {
                Text(
                    text = "尚未添加 API · 点击下方按钮添加 Binance 只读密钥",
                    color = colors.ink3,
                    style = WzTheme.typography.body,
                    modifier = Modifier.padding(vertical = 8.dp).testTag("api-empty"),
                )
            } else {
                state.keys.forEach { key ->
                    ApiKeyRow(
                        key = key,
                        syncing = state.syncingKeyId == key.id,
                        onEdit = { vm.openEdit(key) },
                        onRemove = { vm.remove(key) },
                        onSync = { vm.syncNow(key) },
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 16.dp)) {
                WzButton(
                    text = ApiCopy.ADD_BUTTON,
                    onClick = vm::openAdd,
                    testTag = "api-add",
                )
            }

            SyncLogSection(logs = state.syncLogs)
        }

        WzToastHost(toast = state.toast, onDismiss = vm::dismissToast)
    }

    when (state.dialog) {
        ApiDialog.NONE -> Unit
        ApiDialog.ADD -> ApiKeyModal(
            title = ApiCopy.ADD_TITLE,
            busy = state.dialogBusy,
            error = state.dialogError,
            onSave = vm::save,
            onTest = vm::test,
            onClose = vm::closeDialog,
        )
        ApiDialog.EDIT -> {
            val key = state.editingKey
            if (key != null) {
                ApiKeyModal(
                    title = ApiCopy.EDIT_TITLE,
                    initialName = key.name,
                    busy = state.dialogBusy,
                    error = state.dialogError,
                    onSave = vm::save,
                    onTest = vm::test,
                    onClose = vm::closeDialog,
                )
            }
        }
    }
}

/** 间隔档位（15/30/60，原型 settingsRow 分段口径）。 */
@Composable
private fun SyncIntervalRow(minutes: Int, onSelect: (Int) -> Unit) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("api-sync-interval"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = ApiCopy.SYNC_INTERVAL_LABEL, color = colors.ink, style = WzTheme.typography.body)
            Text(text = ApiCopy.SYNC_INTERVAL_SUB, color = colors.ink3, style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60).forEach { candidate ->
                val selected = candidate == minutes
                WzButton(
                    text = if (selected) "● " + candidate + " 分钟" else "○ " + candidate + " 分钟",
                    onClick = { onSelect(candidate) },
                    variant = if (selected) WzButtonVariant.Primary else WzButtonVariant.Secondary,
                    testTag = "api-interval-" + candidate,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyRow(
    key: ApiKeyInfo,
    syncing: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onSync: () -> Unit,
) {
    val colors = WzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("api-key-row-" + key.id),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = key.name + " · " + key.exchangeName, color = colors.ink, style = WzTheme.typography.body)
            Text(
                text = buildString {
                    append(key.maskedKey)
                    append("  ")
                    append(ApiCopy.LAST_SYNC_PREFIX)
                    append(": ")
                    append(formatTime(key.lastSyncTime))
                },
                color = colors.ink3,
                style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = if (key.status == SyncStatus.OK.storageValue) "OK" else "FAILED",
            color = if (key.status == SyncStatus.OK.storageValue) colors.gain else colors.loss,
            style = WzTheme.typography.caption,
            modifier = Modifier.padding(end = 10.dp).testTag("api-key-status-" + key.id),
        )
        WzButton(text = if (syncing) ApiCopy.SYNCING else ApiCopy.SYNC_NOW, onClick = onSync, enabled = !syncing,
            variant = WzButtonVariant.Secondary, testTag = "api-key-sync-" + key.id)
        WzButton(text = "编辑", onClick = onEdit, variant = WzButtonVariant.Secondary,
            modifier = Modifier.padding(start = 8.dp), testTag = "api-key-edit-" + key.id)
        WzButton(text = ApiCopy.REMOVE, onClick = onRemove, variant = WzButtonVariant.Danger,
            modifier = Modifier.padding(start = 8.dp), testTag = "api-key-remove-" + key.id)
    }
}

@Composable
private fun SyncLogSection(logs: List<SyncLogRow>) {
    val colors = WzTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp).testTag("api-sync-logs")) {
        Text(text = "最近同步记录", color = colors.ink2, style = WzTheme.typography.body)
        if (logs.isEmpty()) {
            Text(text = "暂无同步记录", color = colors.ink3, style = WzTheme.typography.caption,
                modifier = Modifier.padding(top = 6.dp))
        } else {
            logs.forEach { log ->
                Text(
                    text = buildString {
                        append(formatFull(log.syncTime))
                        append("  ")
                        append(if (log.status == SyncStatus.OK) "成功" else "失败")
                        append(" · 新增 ").append(log.newTradesCount)
                        append("  ").append(log.message)
                    },
                    color = if (log.status == SyncStatus.OK) colors.ink2 else colors.loss,
                    style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 4.dp).testTag("api-sync-log-" + log.id),
                )
            }
        }
    }
}

/** 添加/编辑弹窗（GUI 共性约束 7.3：就地叠加 + 首输入框聚焦；保存 = 校验→落库→首次同步）。 */
@Composable
private fun ApiKeyModal(
    title: String,
    busy: Boolean,
    error: String?,
    onSave: (ApiKeyInput) -> Unit,
    onTest: (ApiKeyInput) -> Unit,
    onClose: () -> Unit,
    initialName: String = "",
) {
    val colors = WzTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var apiKey by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val keyFocus = remember { FocusRequester() }
    val secretFocus = remember { FocusRequester() }
    WzModal(
        title = title,
        onDismiss = onClose,
        width = 480.dp,
        testTag = "api-modal",
        initialFocusRequester = nameFocus,
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(text = ApiCopy.HINT_READONLY + "\n" + ApiCopy.HINT_ENCRYPTED + "\n" + ApiCopy.HINT_ACCOUNT,
                color = colors.ink3, style = WzTheme.typography.caption)
            WzTextField(value = name, onValueChange = { name = it }, label = ApiCopy.NAME_LABEL,
                placeholder = ApiCopy.NAME_PLACEHOLDER, modifier = Modifier.padding(top = 10.dp),
                testTag = "api-name-input", fieldFocusRequester = nameFocus)
            WzTextField(value = apiKey, onValueChange = { apiKey = it }, label = ApiCopy.API_KEY_LABEL,
                placeholder = ApiCopy.API_KEY_PLACEHOLDER, modifier = Modifier.padding(top = 10.dp),
                testTag = "api-key-input", fieldFocusRequester = keyFocus)
            WzTextField(value = secret, onValueChange = { secret = it }, label = ApiCopy.SECRET_LABEL,
                placeholder = ApiCopy.SECRET_PLACEHOLDER, isPassword = true, modifier = Modifier.padding(top = 10.dp),
                testTag = "api-secret-input", fieldFocusRequester = secretFocus)
            if (error != null) {
                Text(text = error, color = colors.loss, style = WzTheme.typography.caption,
                    modifier = Modifier.padding(top = 6.dp).testTag("api-dialog-error"))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                WzButton(text = ApiCopy.TEST_BUTTON, onClick = { onTest(inputOf(name, apiKey, secret)) },
                    enabled = !busy, variant = WzButtonVariant.Secondary,
                    modifier = Modifier.padding(end = 8.dp), testTag = "api-test")
                WzButton(text = ApiCopy.SAVE_BUTTON, onClick = { onSave(inputOf(name, apiKey, secret)) },
                    enabled = !busy, testTag = "api-save")
            }
        }
    }
}

private fun inputOf(name: String, apiKey: String, secret: String): ApiKeyInput =
    ApiKeyInput(name = name, exchangeName = "BINANCE", apiKey = apiKey, secretKey = secret)

private fun formatTime(at: Instant?): String {
    if (at == null) return ApiCopy.LAST_SYNC_NEVER
    val zoned = at.atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(zoned.hour, zoned.minute)
}

private fun formatFull(at: Instant): String {
    val zoned = at.atZone(java.time.ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute)
}
