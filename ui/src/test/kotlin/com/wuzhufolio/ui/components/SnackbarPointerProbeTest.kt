package com.wuzhufolio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.wuzhufolio.ui.theme.WuzhuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 守护测试（T14.3 尾项决策依据 / **D38 例外清单**）：M3 `Snackbar` **会吞掉**其覆盖区域的点击。
 *
 * 背景：本项目 M11 §6 曾实测「M3 `Surface` 内部消费指针事件」导致右下角轻提示存活期内点击被吞。
 * T14.3 尾项评估「`WzToast` → M3 `Snackbar`」时用本测试复测 M3 1.9.0：**仍然吞点击**（断言 0 次命中），
 * 因此 `WzToast` **有意不改用**该组合项，只对齐其视觉 token（`inverseSurface` / `Shapes.small` / 排版）。
 *
 * 本测试锁住该结论：若将来 M3 修正了该行为（断言变红），即可安全地把 `WzToast` 换成官方 `Snackbar`。
 */
@OptIn(ExperimentalTestApi::class)
class SnackbarPointerProbeTest {

    @Test
    fun `m3 snackbar swallows clicks in its area (why WzToast keeps a custom box)`() = runComposeUiTest {
        var clicks = 0
        setContent {
            WuzhuTheme(themeMode = com.wuzhufolio.domain.settings.ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    // 下层可点区域：与 snackbar 位置重叠（右下角）
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(200.dp)
                            .clickable { clicks++ }
                            .testTag("under"),
                    )
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomEnd).size(200.dp).testTag("snack"),
                        action = { Text("OK") },
                    ) { Text("提示") }
                }
            }
        }
        onNodeWithTag("snack").assertIsDisplayed()
        onNodeWithTag("under").performClick()
        assertEquals(
            0,
            clicks,
            "M3 Snackbar 已不再吞点击——此时可考虑把 WzToast 换成官方 Snackbar（见 D38 例外清单）",
        )
    }
}
