package com.xialiangok.xlreader.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CardColors
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Typography
import com.xialiangok.xlreader.data.ReaderPreferences

/*
 * 配色刻意做得“低视觉负担”:
 * 纯黑底 + 灰阶文字 + 单一低饱和强调色, 没有渐变, 没有大面积亮色,
 * 目的是在 AMOLED 手表上省电, 并让长时间阅读不刺眼.
 */

/** 纯黑背景, OLED 像素直接熄灭. */
val ReaderBlack = Color(0xFF000000)

/** 卡片/容器底色, 仅比背景亮一点点. */
val ReaderSurface = Color(0xFF121212)
val ReaderSurfaceHigh = Color(0xFF1E1E1E)

/** 正文与次要文字 (默认值, 实际颜色由 ReaderPreferences 覆盖). */
val ReaderOnSurface = Color(0xFFE4E4E4)
val ReaderOnSurfaceVariant = Color(0xFF9A9A9A)

/** 默认强调色: 低饱和青绿, 用于选中态与开关. */
val ReaderAccent = Color(0xFF8FB8A8)

/** 极淡的分隔线. */
val ReaderOutline = Color(0xFF353535)

/** 按钮底色, 比背景和卡片都亮一档. */
val ReaderButtonContainer = Color(0xFF2C2C2E)

/** 静态的基准配色, 实际使用的配色在 [readerColorScheme] 里按设置覆盖. */
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
 * 当前这份阅读偏好, 供页面骨架读取上下边距这类全局排版参数.
 *
 * 用 CompositionLocal 而不是层层透传, 页面调用点不用为了两个 dp 值再加参数.
 */
val LocalReaderPreferences = staticCompositionLocalOf { ReaderPreferences() }

/**
 * 把 `0xRRGGBB` 转成不透明的 Compose 颜色.
 */
fun rgbColor(rgb: Int): Color = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/**
 * 按用户设置拼一套配色.
 *
 * onSurface / onSurfaceVariant 换成主题里的正文色与压暗一档的次要色,
 * 于是绝大多数控件文字自动跟随设置; 边缘色 (按钮描边) 与文件名色由各自控件直接读.
 * primary 用于开关, 进度, 标签这类强调元素, 由主题的信息色决定;
 * 当前章节 / 最近打开 / 目录开关条也都读同一个信息色 (2026-10-31 合并).
 */
fun readerColorScheme(preferences: ReaderPreferences): ColorScheme {
    val text = rgbColor(preferences.effectiveTextColor)
    val secondary = rgbColor(preferences.effectiveSecondaryTextColor)
    val accent = rgbColor(preferences.effectiveAccentColor)
    return ReaderColorScheme.copy(
        primary = accent,
        tertiary = accent,
        onSurface = text,
        onSurfaceVariant = secondary,
        onBackground = text,
        background = rgbColor(preferences.effectiveBackgroundColor),
    )
}

/** 按钮与列表项的描边粗细. 1dp 在手表屏上就是两个像素, 够看清又不会抢眼. */
private val ReaderOutlineWidth = 1.dp

/**
 * 按钮底色.
 *
 * 2026-10-31 起统一为**纯黑**: 按钮不再靠一块灰底和背景区分, 只留一圈描边,
 * 这样按钮、列表项、页面背景都是同一种黑, AMOLED 上整屏只有文字和边框在发光.
 */
@Composable
fun readerButtonContainerColor(): Color = ReaderBlack

/**
 * 按钮与列表项的描边颜色: 取设置里的按钮色.
 *
 * 原来按钮是把按钮色铺满整个底面, 现在底面改成纯黑, 按钮色降级成那一圈边框,
 * 于是「按钮色」这个设置项仍然管着所有按钮和列表项的外观.
 */
@Composable
fun readerButtonBorder(): BorderStroke =
    BorderStroke(ReaderOutlineWidth, rgbColor(LocalReaderPreferences.current.effectiveButtonColor))

/**
 * 全应用统一的按钮配色: 纯黑底 + 主题文字色.
 *
 * 注意**必须同时指定文字色**: Wear 默认按钮的文字色取自 `onPrimary`,
 * 而本主题里 `onPrimary` 是纯黑, 只改底色的话字就看不见了, 所以这里配 `onSurface`.
 */
@Composable
fun readerButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = readerButtonContainerColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * 列表项 (Card) 的配色: 与按钮一样的纯黑底.
 *
 * 列表项原来用 `surfaceContainer` (比背景亮一档的灰), 和按钮的按钮色对不上;
 * 现在两者都改纯黑, 只靠边框区分, 颜色由 [readerCardBorder] 统一给出.
 */
@Composable
fun readerCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = ReaderBlack,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** 列表项描边: 与按钮同一条边、同一种颜色. */
@Composable
fun readerCardBorder(): BorderStroke = readerButtonBorder()

/**
 * 应用主题.
 *
 * 动效在这里被**固定关掉**, 不再由设置里的开关控制:
 * - motionScheme 换成 [NoMotionScheme] (六个 spec 全是 [snap]);
 * - [LocalReduceMotion] 固定为 true.
 *
 * 触摸反馈的水波纹只认「动画时长缩放」, 把它压成 0 会连惯性滑动一起变成瞬移, 所以不动它.
 *
 * 字号与颜色也从这里统一注入: 标题 / 正文 / 小字三档磅值重写整套 Typography,
 * 字色与强调色重写 ColorScheme, 所有控件的文字因此自动跟随 ReaderPreferences.
 */
@Composable
fun XlReaderTheme(
    preferences: ReaderPreferences = ReaderPreferences(),
    content: @Composable () -> Unit,
) {
    val typography = remember(
        preferences.titleFontSize,
        preferences.fontSize,
        preferences.smallFontSize,
    ) {
        Typography().withFontSizes(
            title = preferences.titleFontSize,
            body = preferences.fontSize,
            small = preferences.smallFontSize,
        )
    }
    // 颜色现在全部来自主题 (含每项的「亮度跟随」开关), 直接按整份偏好算,
    // 免得漏掉某个 follow 字段导致改主题后颜色不刷新.
    val colorScheme = remember(preferences) {
        readerColorScheme(preferences)
    }
    CompositionLocalProvider(
        LocalReduceMotion provides true,
        LocalReaderPreferences provides preferences,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            motionScheme = NoMotionScheme,
            content = content,
        )
    }
}

/**
 * 把整套 Typography 压成三档字号.
 *
 * 标题组: display / title / labelLarge / numeral, 对应 ListHeader 这类大标题,
 * 正文组: bodyLarge / bodyMedium / labelMedium, 对应按钮文字和正文,
 * 小字组: bodySmall / bodyExtraSmall / labelSmall, 对应说明与脚注.
 * 行高按各档原来的比例跟着缩放, 换字号不会把行距挤坏.
 */
private fun Typography.withFontSizes(title: Int, body: Int, small: Int): Typography {
    fun TextStyle.fit(size: Int): TextStyle {
        val ratio = if (lineHeight.isSpecified && fontSize.isSpecified && fontSize.value > 0f) {
            lineHeight.value / fontSize.value
        } else {
            1.25f
        }
        return copy(fontSize = size.sp, lineHeight = (size * ratio).sp)
    }
    return copy(
        displayLarge = displayLarge.fit(title),
        displayMedium = displayMedium.fit(title),
        displaySmall = displaySmall.fit(title),
        titleLarge = titleLarge.fit(title),
        titleMedium = titleMedium.fit(title),
        titleSmall = titleSmall.fit(title),
        labelLarge = labelLarge.fit(title),
        labelMedium = labelMedium.fit(body),
        labelSmall = labelSmall.fit(small),
        bodyLarge = bodyLarge.fit(body),
        bodyMedium = bodyMedium.fit(body),
        bodySmall = bodySmall.fit(small),
        bodyExtraSmall = bodyExtraSmall.fit(small),
        numeralExtraLarge = numeralExtraLarge.fit(title),
        numeralLarge = numeralLarge.fit(title),
        numeralMedium = numeralMedium.fit(title),
        numeralSmall = numeralSmall.fit(title),
        numeralExtraSmall = numeralExtraSmall.fit(small),
    )
}

/**
 * 完全不动效的 [MotionScheme]: 六个 spec 都用 [snap] (时长 0),
 * 组件从任何状态切到任何状态都是直接跳到终点.
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
 * 阅读排版参数.
 *
 * 把“字号 / 行高 / 段间距”集中在一处, 阅读页只管取值,
 * 界面代码里不会散落魔法数字.
 */
object ReadingMetrics {

    /** 正文字号: 参数直接就是磅值 (1..999). */
    fun bodyFontSize(size: Int): TextUnit =
        size.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE).sp

    /** 行高固定为字号的 1.7 倍, 中文排版比较舒服的比例. */
    fun lineHeight(size: Int): TextUnit = bodyFontSize(size) * 1.7f

    /** 段间距: 参数直接就是 dp 值 (0..999). */
    fun paragraphSpacing(level: Int): Dp =
        level.coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL).dp
}
