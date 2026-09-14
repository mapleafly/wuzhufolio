package com.wuzhufolio.ui.backup

import com.wuzhufolio.domain.backup.BackupExportException
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.ui.i18n.I18n
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P6 · P5-4 文案映射守护：导出侧类型化异常 → 中英双档可读文案（不再透出原始加密异常）。
 *
 * 对照 `BackupCopy.decodeErrorCopy`（导入侧三态）——两侧现在都有类型化→文案的映射。
 */
class BackupExportErrorCopyTest {

    @AfterTest
    fun restoreLanguage() {
        I18n.set(AppLanguage.ZH)
    }

    @Test
    fun `credential unreadable maps to actionable copy in both languages`() {
        val error = BackupExportException(
            reason = BackupExportException.Reason.CREDENTIAL_UNREADABLE,
            message = "api_keys credential unreadable: 主号 (id=7)",
            keyName = "主号",
            cause = IllegalStateException("AES-GCM authentication failed"),
        )

        I18n.set(AppLanguage.ZH)
        val zh = BackupCopy.exportErrorCopy(error)
        assertTrue(zh.contains("主号"), "中文文案应点名密钥别名：$zh")
        assertTrue(zh.contains("无法解密"), "中文文案应说明凭证无法解密：$zh")
        assertTrue(!zh.contains("AES-GCM"), "不得把原始加密异常透出给用户：$zh")

        I18n.set(AppLanguage.EN)
        val en = BackupCopy.exportErrorCopy(error)
        assertTrue(en.contains("主号"), "English copy should name the key: $en")
        assertTrue(en.contains("cannot be decrypted"), "English copy should explain the failure: $en")
        assertTrue(!en.contains("AES-GCM"), "raw crypto exception must not leak into UI copy: $en")
    }

    @Test
    fun `unknown export failure falls back to the original message`() {
        I18n.set(AppLanguage.ZH)
        val text = BackupCopy.exportErrorCopy(IllegalStateException("disk full"))
        assertTrue(text.contains("disk full"), "未知失败保留原始信息便于定位：$text")
    }
}
