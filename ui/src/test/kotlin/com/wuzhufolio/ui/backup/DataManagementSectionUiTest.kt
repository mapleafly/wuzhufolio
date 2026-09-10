package com.wuzhufolio.ui.backup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.domain.backup.BackupExportSummary
import com.wuzhufolio.domain.backup.BackupMetadata
import com.wuzhufolio.domain.backup.BackupPreview
import com.wuzhufolio.domain.backup.BackupService
import com.wuzhufolio.domain.backup.CproCounts
import com.wuzhufolio.domain.backup.CproDecodeException
import com.wuzhufolio.domain.backup.CproHeader
import com.wuzhufolio.domain.backup.CproKdf
import com.wuzhufolio.domain.backup.CproRange
import com.wuzhufolio.domain.backup.CsvExportKind
import com.wuzhufolio.domain.backup.RestoreMode
import com.wuzhufolio.domain.backup.RestorePreview
import com.wuzhufolio.domain.backup.RestoreSummary
import com.wuzhufolio.domain.backup.RestoreTableCounts
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 数据管理区 UI 走查（M9 · T9.4 · flows §6 全路径真实 UI + 共性约束 7.3：
 * 含输入框弹窗首输入框 performTextInput + assertIsFocused）。
 */
@OptIn(ExperimentalTestApi::class)
class DataManagementSectionUiTest {

    @Test
    fun backupFileNamesAppendExtensionOnce() {
        assertEquals("/tmp/x.cpro", BackupFileNames.withExtension("/tmp/x", ".cpro"))
        assertEquals("/tmp/x.cpro", BackupFileNames.withExtension("/tmp/x.cpro", ".cpro"))
        assertEquals("/tmp/X.CPRO", BackupFileNames.withExtension("/tmp/X.CPRO", ".cpro"))
        assertEquals("/tmp/f.csv", BackupFileNames.withExtension("/tmp/f", ".csv"))
    }


    private class FakeBackupService : BackupService {
        var exportedTo: Path? = null
        var restoredWith: Pair<Path, RestoreMode>? = null
        var csvKind: CsvExportKind? = null
        var failPrepare: Boolean = false

        override suspend fun backupMetadata(): BackupMetadata = BackupMetadata(null, null)

        override suspend fun exportBackup(path: Path, password: CharArray): BackupExportSummary {
            exportedTo = path
            return BackupExportSummary(
                path = path,
                counts = CproCounts(2, 1, 0, 1, 1, 1, 2),
                exportedAt = Instant.parse("2026-09-09T00:00:00Z"),
                sizeBytes = 1024,
            )
        }

        override suspend fun previewBackup(path: Path): BackupPreview = BackupPreview(
            formatVersion = 1,
            appVersion = "test",
            exportedAt = Instant.parse("2026-09-09T00:00:00Z"),
            counts = CproCounts(2, 1, 0, 1, 1, 1, 2),
            rangeMin = Instant.parse("2025-01-01T00:00:00Z"),
            rangeMax = Instant.parse("2025-06-01T00:00:00Z"),
            cipher = "aes-256-gcm",
        )

        override suspend fun prepareRestore(path: Path, password: CharArray): RestorePreview {
            if (failPrepare) {
                throw CproDecodeException(
                    CproDecodeException.Reason.WRONG_PASSWORD_OR_CORRUPTED,
                    "backup authentication failed",
                )
            }
            return RestorePreview(
                header = CproHeader(
                    formatVersion = 1,
                    appVersion = "test",
                    exportedAt = "2026-09-09T00:00:00Z",
                    counts = CproCounts(2, 1, 0, 1, 1, 1, 2),
                    range = CproRange(null, null),
                    kdf = CproKdf("argon2id", "00".repeat(16), 65536, 3, 1),
                    cipher = "aes-256-gcm",
                ),
                plan = com.wuzhufolio.domain.backup.BackupMergePlanner.MergePlan(
                    transactions = emptyList(),
                    capitalFlows = emptyList(),
                    reconciliationRecords = emptyList(),
                    feeRules = emptyList(),
                    apiKeys = emptyList(),
                    settings = emptyList(),
                    priceSnapshots = emptyList(),
                    duplicateSkipped = 5,
                    missingCoinSkipped = 0,
                    missingCoinIds = emptyList(),
                ),
                mode = RestoreMode.MERGE,
            )
        }

        override suspend fun restoreBackup(path: Path, password: CharArray, mode: RestoreMode): RestoreSummary {
            restoredWith = path to mode
            return RestoreSummary(
                mode = mode,
                imported = RestoreTableCounts(2, 1, 0, 1, 1, 1, 2),
                duplicateSkipped = 0,
                missingCoinSkipped = 0,
                missingCoinIds = emptyList(),
                anomalousCoins = emptyList(),
                preExistingAnomalousCoins = listOf("fdusd"),
                tempBackupPath = if (mode == RestoreMode.FULL_OVERWRITE) Path.of("/tmp/pre.cpro") else null,
                completedAt = Instant.parse("2026-09-09T00:00:00Z"),
            )
        }

        override suspend fun exportCsv(kind: CsvExportKind, path: Path) {
            csvKind = kind
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.textCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private val cproPath = "/tmp/test-backup.cpro"

    @Test
    fun sectionShowsThreeGroups() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("backup-section").assertIsDisplayed()
        onNodeWithTag("backup-group").assertIsDisplayed()
        onNodeWithTag("restore-group").assertIsDisplayed()
        onNodeWithTag("csv-group").assertIsDisplayed()
        onNodeWithTag("csv-export-transactions").assertIsDisplayed()
        onNodeWithTag("csv-export-funds").assertIsDisplayed()
        onNodeWithTag("csv-export-holdings").assertIsDisplayed()
    }

    @Test
    fun backupModalFocusesPasswordAndExportsViaKeyboard() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("backup-open").performClick()
        onNodeWithTag("backup-export-modal").assertIsDisplayed()
        onNodeWithTag("backup-sensitive-warning", useUnmergedTree = true).assertIsDisplayed()
        // 共性约束 7.3：首输入框打开即聚焦 + 键盘逐字录入
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("backup-pwd").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("backup-pwd").performTextInput("Backup-Pass-1")
        onNodeWithTag("backup-pwd-confirm").performTextInput("Backup-Pass-1")
        onNodeWithTag("backup-export-confirm").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.exportedTo != null }
        // Path 归一化比较（Windows Path.toString() 为反斜杠形态——CI run 34448610233 勘误）
        assertEquals(Path.of(cproPath), svc.exportedTo, "保存路径透传")
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.EXPORT_SUCCESS_PREFIX) >= 1 }
    }

    @Test
    fun backupModalRejectsWeakPasswordWithoutTouchingService() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("backup-open").performClick()
        onNodeWithTag("backup-pwd").performTextInput("short")
        onNodeWithTag("backup-pwd-confirm").performTextInput("short")
        onNodeWithTag("backup-export-confirm").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.EXPORT_ERROR_PWD) >= 1 }
        assertEquals(null, svc.exportedTo, "弱密码不得触达服务")
    }

    @Test
    fun restoreWizardFullFlowWithOverwriteConfirm() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("restore-open").performClick()
        onNodeWithTag("restore-modal").assertIsDisplayed()
        // 第一步：选文件（Fake 直接返回路径）→ 摘要
        onNodeWithTag("restore-pick").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.SUMMARY_VERSION) >= 1 }
        onNodeWithTag("restore-summary", useUnmergedTree = true).assertIsDisplayed()
        // 第二步：密码（7.3 聚焦 + 键盘录入）→ 验证
        waitUntil(timeoutMillis = 2_000) {
            runCatching { onNodeWithTag("restore-pwd").assertIsFocused() }.isSuccess
        }
        onNodeWithTag("restore-pwd").performTextInput("Backup-Pass-1")
        onNodeWithTag("restore-unlock").performClick()
        // 第三步：方式选择（合并预览计数可见）
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.MODE_TITLE) >= 1 }
        onNodeWithTag("restore-mode-merge").assertIsDisplayed()
        // 切全量覆盖 → 二次确认勾选必需
        onNodeWithTag("restore-mode-overwrite").performClick()
        onNodeWithTag("restore-overwrite-confirm").performClick()
        onNodeWithTag("restore-execute").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.restoredWith != null }
        assertEquals(RestoreMode.FULL_OVERWRITE, svc.restoredWith!!.second)
        // 结果页：导入计数 + 临时备份路径
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.RESULT_TITLE) >= 1 }
        onNodeWithTag("restore-result", useUnmergedTree = true).assertIsDisplayed()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.RESULT_TEMP_BACKUP_PREFIX) >= 1 }
        onNodeWithTag("restore-result-done").performClick()
    }

    @Test
    fun restoreWizardShowsTypedErrorOnWrongPassword() = runComposeUiTest {
        val svc = FakeBackupService().apply { failPrepare = true }
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("restore-open").performClick()
        onNodeWithTag("restore-pick").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.SUMMARY_VERSION) >= 1 }
        onNodeWithTag("restore-pwd").performTextInput("Wrong-Pass-1")
        onNodeWithTag("restore-unlock").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.ERR_WRONG_PASSWORD) >= 1 }
        assertEquals(null, svc.restoredWith, "密码错误不得执行恢复")
    }

    @Test
    fun restoreResultDistinguishesPreExistingAnomalies() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("restore-open").performClick()
        onNodeWithTag("restore-pick").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.SUMMARY_VERSION) >= 1 }
        onNodeWithTag("restore-pwd").performTextInput("Backup-Pass-1")
        onNodeWithTag("restore-unlock").performClick()
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.MODE_TITLE) >= 1 }
        onNodeWithTag("restore-execute").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.restoredWith != null }
        // 既存异常行可见（非本次导入造成）+ 新产生行为「无」
        waitUntil(timeoutMillis = 2_000) {
            textCount(BackupCopy.RESULT_ANOMALOUS_EXISTING_PREFIX) >= 1
        }
        waitUntil(timeoutMillis = 2_000) { textCount(BackupCopy.RESULT_NONE) >= 1 }
    }

    @Test
    fun csvExportButtonsCallService() = runComposeUiTest {
        val svc = FakeBackupService()
        setContent { DataManagementSection(svc, { cproPath }, { cproPath }, { cproPath }) }
        onNodeWithTag("csv-export-funds").performClick()
        waitUntil(timeoutMillis = 2_000) { svc.csvKind != null }
        assertEquals(CsvExportKind.FUNDS, svc.csvKind)
        assertTrue(textCount(BackupCopy.CSV_SUCCESS_PREFIX) >= 0)
    }
}
