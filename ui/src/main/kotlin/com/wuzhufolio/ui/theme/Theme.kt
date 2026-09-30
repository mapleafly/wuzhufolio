package com.wuzhufolio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.wuzhufolio.domain.settings.AppLanguage
import com.wuzhufolio.domain.settings.PnlColorScheme
import com.wuzhufolio.domain.settings.ThemeMode
import com.wuzhufolio.ui.i18n.I18n
import com.wuzhufolio.ui.i18n.WzFormat

val LocalWzColors = compositionLocalOf { lightWzColors() }
val LocalWzTypography = compositionLocalOf { wzTypography(wzM3Typography()) }

/** 当前界面语言（Composable 内需要按语言分支时的读取入口；文案本身经 I18n 动态取值）。 */
val LocalWzLanguage = compositionLocalOf { AppLanguage.ZH }

/** 组件取 token 的唯一入口（单一真源原则：组件一律经主题取色，不写死色值）。 */
object WzTheme {
    val colors: WzColors
        @Composable get() = LocalWzColors.current
    val typography: WzTypography
        @Composable get() = LocalWzTypography.current
}

/**
 * WuZhuFolio 主题（T0.6）：明/暗双主题 + 盈亏配色方案 + 界面语言（M12 T12.4）。
 * MaterialTheme 仅作 M3 控件基座映射，业务组件一律取 [WzTheme]。
 *
 * 语言切换的重组口径：文案对象全部是「动态取值」属性（非 Compose State），故切换语言必须由
 * [key] 强制整棵子树重建才能全量重读——这是本函数持有 language 的唯一原因，删除 `key` 会导致
 * 切换语言后界面文案不更新。
 */
@Composable
fun WuzhuTheme(
    themeMode: ThemeMode,
    pnlScheme: PnlColorScheme = PnlColorScheme.GREEN_UP,
    language: AppLanguage = AppLanguage.ZH,
    content: @Composable () -> Unit,
) {
    I18n.set(language)
    // 精度档同语言一样是「非 State 的全局读取器」，切换后同样需要强制重组（WzFormat 头注）
    val precision by WzFormat.precisionFlow.collectAsState()
    val base = if (themeMode == ThemeMode.DARK) darkWzColors() else lightWzColors()
    val colors = base.withPnlScheme(pnlScheme)
    val dark = themeMode == ThemeMode.DARK
    // D38：M3 字体层级（规范值 + 产品字族）→ 业务语义槽位投影
    val m3Typography = wzM3Typography()
    val typography = wzTypography(m3Typography)
    // M3 配色（官方算法从品牌种子色生成）+ 圆角阶梯 + 动效方案（expressive）
    val m3Colors = wzColorScheme(colors, dark)
    val m3Shapes = wzShapes()

    CompositionLocalProvider(
        LocalWzColors provides colors,
        LocalWzTypography provides typography,
        LocalWzLanguage provides language,
    ) {
        // 语言/精度档变化 → 整棵子树重建（文案与数值格式均为动态读取，见函数头注）
        key(language.code, precision.storageValue) {
            // typography/shapes 一并下发：此前未传 → M3 组件用 16sp 正文与 12dp 圆角，与自绘组件两套并存
            MaterialTheme(
                colorScheme = m3Colors,
                typography = m3Typography,
                shapes = m3Shapes,
                content = content,
            )
        }
    }
}
