package com.xialiangok.xlreader.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import com.xialiangok.xlreader.data.ReaderPreferences

/*
 * 配色刻意做得“低视觉负担”：
 * 纯黑底 + 灰阶文字 + 单一低饱和强调色，没有渐变、没有大面积亮色，
 * 目的是在 AMOLED 手表上省电，并让长时间阅读不刺眼。
 */

/** 纯黑背景，OLED 像素直接熄灭。 */
val ReaderBlack = Color(0xFF000000)

/** 卡片/容器底色，仅比背景亮一点点。 */
val ReaderSurface = Color(0xFF121212)
val ReaderSurfaceHigh = Color(0xFF1E1E1E)

/** 正文与次要文字。 */
val ReaderOnSurface = Color(0xFFE4E4E4)
val ReaderOnSurfaceVariant = Color(0xFF9A9A9A)

/** 唯一强调色：低饱和青绿，用于选中态与开关。 */
val ReaderAccent = Color(0xFF8FB8A8)

/** 极淡的分隔线。 */
val ReaderOutline = Color(0xFF353535)

/**
 * 按钮底色。
 *
 * 比背景（纯黑）和卡片（`#121212`）都亮一档，既不刺眼，又能让按钮从背景里浮出来。
 */
val ReaderButtonContainer = Color(0xFF2C2C2E)

val ReaderColorScheme = ColorScheme(
    primary = ReaderAccent,
    onPrimary = ReaderBlack,
    primaryContainer = ReaderSurfaceHigh,
    onPrimaryContainer = ReaderAccent,
    secondary = ReaderOnSurfaceVariant,
    onSecondary = ReaderBlack,
    secondaryContainer = ReaderSurfaceHigh,
    onSecondaryContainer = ReaderOnSurface,
    tertiary = ReaderAccent,
    onTertiary = ReaderBlack,
    tertiaryContainer = ReaderSurfaceHigh,
    onTertiaryContainer = ReaderAccent,
    surfaceContainerLow = ReaderSurface,
    surfaceContainer = ReaderSurface,
    surfaceContainerHigh = ReaderSurfaceHigh,
    onSurface = ReaderOnSurface,
    onSurfaceVariant = ReaderOnSurfaceVariant,
    outline = ReaderOutline,
    outlineVariant = ReaderOutline,
    background = ReaderBlack,
    onBackground = ReaderOnSurface,
)

/**
 * 全应用统一的按钮配色：底色 `#2C2C2E`。
 *
 * 注意**必须同时指定文字色**：Wear 默认按钮的文字色取自 `onPrimary`，而本主题里
 * `onPrimary` 是纯黑 —— 只改底色的话字就看不见了。所以这里配 `onSurface`（浅灰）。
 */
@Composable
fun readerButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = ReaderButtonContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * 应用主题。
 *
 * 动效在这里被**固定关掉**，不再由设置里的开关控制（设置页那个开关现在只作展示）：
 * - motionScheme 换成 [NoMotionScheme]（六个 spec 全是 [snap]）——Wear M3 组件的动画
 *   时长都取自它，于是任何状态切换只剩终态，没有位移、缩放、渐变；
 * - [LocalReduceMotion] 固定为 true —— 这是 Wear 官方用来表达「减少动效」的开关，
 *   滚动指示条的回弹、各类形变与淡入淡出都会跳过。
 *
 * 触摸反馈的水波纹（ripple）由组件库内部直接创建，只认「动画时长缩放」，
 * 把它压成 0 会连惯性滑动一起变成瞬移（衰减动画的时长就是滑动时长），所以这里不动它。
 */
@Composable
fun XlReaderTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        MaterialTheme(
            colorScheme = ReaderColorScheme,
            motionScheme = NoMotionScheme,
            content = content,
        )
    }
}

/**
 * 完全不动效的 [MotionScheme]：六个 spec 都用 [snap]（时长 0），
 * 组件从任何状态切到任何状态都是直接跳到终点。
 */
private val NoMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/**
 * 阅读排版参数。
 *
 * 把“字号 / 行高 / 段间距”三件事集中在一处，阅读页只管取值，
 * 这样界面代码里不会散落魔法数字。
 */
object ReadingMetrics {

    /** 正文字号：参数直接就是磅值（1..999），加减一次走 1 磅。 */
    fun bodyFontSize(size: Int): TextUnit =
        size.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE).sp

    /** 文章标题字号：正文的 1.25 倍，比正文大一点就够。 */
    fun titleFontSize(size: Int): TextUnit = bodyFontSize(size) * 1.25f

    /** 行高固定为字号的 1.7 倍，中文排版比较舒服的比例。 */
    fun lineHeight(size: Int): TextUnit = bodyFontSize(size) * 1.7f

    /** 段间距：参数直接就是 dp 值（0..999），不再折成三档。 */
    fun paragraphSpacing(level: Int): Dp =
        level.coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL).dp
}
