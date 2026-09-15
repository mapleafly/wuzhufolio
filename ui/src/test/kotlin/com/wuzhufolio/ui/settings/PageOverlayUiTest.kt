package com.wuzhufolio.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.wuzhufolio.ui.components.PageOverlay
import com.wuzhufolio.ui.components.PageOverlayHost
import com.wuzhufolio.ui.components.WzButton
import com.wuzhufolio.ui.components.WzModal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 页面级叠加槽回归（P6 人工门第五轮 · DEF-22/23/24）。
 *
 * 人工实测症状：设置页「添加 API」「行情数据源」「恢复数据」弹窗**撑开页面 / 挤占后面内容 / 浮在某个区域上**。
 * 根因：`WzModal` 是就地叠加层，靠 `fillMaxSize()` 铺满页面；一旦被放进 `verticalScroll` 容器内部，
 * 该处高度约束是**无限**的 → `fillMaxSize()` 退化为内容高度 → 弹层变成滚动列里的一个普通块。
 * 修法：页面根放 [PageOverlayHost]，深层组件用 [PageOverlay] 把弹层提交到页面根。
 *
 * 本测试钉死两条：① 弹层由页面根渲染（覆盖整页，不落在滚动列内）；
 * ② 打开弹层**不改变**页面其它元素的布局位置（不再撑开/挤占）。
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class PageOverlayUiTest {

    /** 复刻设置页结构：页面根 Box → PageOverlayHost → 滚动列（内含提交弹层的深层组件）。 */
    @androidx.compose.runtime.Composable
    private fun SettingsLikePage(open: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
        Box(modifier = modifier.fillMaxSize().testTag("page-root")) {
            PageOverlayHost(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("scroll-column"),
                ) {
                    Text(text = "页面标题")
                    WzButton(text = "打开", onClick = onOpen, testTag = "open-modal")
                    Text(text = "尾部内容", modifier = Modifier.testTag("tail-text"))
                    PageOverlay {
                        if (open) {
                            WzModal(title = "弹层", onDismiss = {}, testTag = "probe-modal") {
                                Text(text = "弹层正文")
                                WzButton(text = "确定", onClick = {}, testTag = "probe-ok")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `modal is rendered by the page root and does not push page content`() = runComposeUiTest {
        var open by androidx.compose.runtime.mutableStateOf(false)
        setContent { SettingsLikePage(open = open, onOpen = { open = true }) }

        val tailBefore = onNodeWithTag("tail-text").getUnclippedBoundsInRoot()
        val buttonBefore = onNodeWithTag("open-modal").getUnclippedBoundsInRoot()

        onNodeWithTag("open-modal").performClick()
        waitForIdle()
        onNodeWithTag("probe-modal", useUnmergedTree = true).assertIsDisplayed()

        // ① 打开弹层后，页面内容位置不变（此前会被弹层挤下去 = 人工症状「撑开页面」）
        assertEquals(buttonBefore, onNodeWithTag("open-modal").getUnclippedBoundsInRoot(), "弹层不得挤占页面内容")
        assertEquals(tailBefore, onNodeWithTag("tail-text").getUnclippedBoundsInRoot(), "弹层不得挤占页面内容")

        // ② 弹层由页面根承载：卡片在**整页**居中（若仍插在滚动列里，卡片只会在其所在内容块内居中，
        //    纵向位置随分组位置漂移 = 人工症状「浮在备份区域上、看着错位」）
        val modal = onNodeWithTag("probe-modal", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val root = onNodeWithTag("page-root").getUnclippedBoundsInRoot()
        val modalCenterX = (modal.left + modal.right) / 2
        val modalCenterY = (modal.top + modal.bottom) / 2
        val rootCenterX = (root.left + root.right) / 2
        val rootCenterY = (root.top + root.bottom) / 2
        assertTrue(
            modalCenterX - rootCenterX < 1.dp && rootCenterX - modalCenterX < 1.dp,
            "弹层应在页面内水平居中（root=$root modal=$modal）",
        )
        assertTrue(
            modalCenterY - rootCenterY < 1.dp && rootCenterY - modalCenterY < 1.dp,
            "弹层应在**整页**垂直居中（而非其所在内容块内；root=$root modal=$modal）",
        )
    }

    @Test
    fun `overlay falls back to inline rendering without a host`() = runComposeUiTest {
        // 无宿主（独立组件/单测直接渲染某个 section）：就地渲染，弹层仍然可见可用
        var open by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                WzButton(text = "打开", onClick = { open = true }, testTag = "open-modal")
                PageOverlay {
                    if (open) {
                        WzModal(title = "弹层", onDismiss = {}, testTag = "probe-modal") {
                            Text(text = "弹层正文")
                        }
                    }
                }
            }
        }
        onNodeWithTag("open-modal").performClick()
        waitForIdle()
        onNodeWithTag("probe-modal", useUnmergedTree = true).assertIsDisplayed()
    }

}
