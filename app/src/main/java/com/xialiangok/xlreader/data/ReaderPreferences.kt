package com.xialiangok.xlreader.data

import androidx.compose.runtime.Immutable

/**
 * 阅读相关的全部偏好。
 *
 * 设置页和阅读界面里的快捷菜单操作的是**同一份**数据，所以在菜单里改字号，
 * 设置页里看到的也是新值，反之亦然。
 */
@Immutable
data class ReaderPreferences(
    /** 正文字号：直接就是磅值，1..999，加减一次走 1 磅。 */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    /** 段间距：直接就是数值（单位 dp，0..999），加减一次走 1 dp。 */
    val spacingLevel: Int = DEFAULT_SPACING_LEVEL,
    /** 阅读时保持屏幕常亮。 */
    val keepScreenOn: Boolean = false,
    /** 减少动效。现在固定开启（见 `XlReaderTheme`），字段留着只是不改存储结构。 */
    val reducedMotion: Boolean = true,
    /** 文字颜色，打包成 `0xRRGGBB`。 */
    val textColor: Int = DEFAULT_TEXT_COLOR,
    /** 文字亮度系数，只压暗不外扩。 */
    val textBrightness: Float = 1f,
    /** 屏幕亮度 0f..1f；[BRIGHTNESS_SYSTEM] 表示跟随系统。 */
    val screenBrightness: Float = BRIGHTNESS_SYSTEM,
) {

    /** 叠加文字亮度之后、真正用来绘制的 `0xRRGGBB`。 */
    val effectiveTextColor: Int
        get() = applyBrightness(textColor, textBrightness)

    companion object {
        /** 默认文字色，与主题里的 onSurface 一致。 */
        const val DEFAULT_TEXT_COLOR = 0xE4E4E4

        /** 屏幕亮度「跟随系统」。 */
        const val BRIGHTNESS_SYSTEM = -1f

        /** 正文字号的取值范围；默认 16 磅，就是原来「中」那一档。 */
        const val MIN_FONT_SIZE = 1
        const val MAX_FONT_SIZE = 999
        const val DEFAULT_FONT_SIZE = 16

        /**
         * 段间距的取值范围（单位 dp）；和字号一样用「− / ＋」一次走 1。
         * 默认 9 dp —— 就是原来三档里的「适中」，所以老用户看到的默认排版没变。
         */
        const val MIN_SPACING_LEVEL = 0
        const val MAX_SPACING_LEVEL = 999
        const val DEFAULT_SPACING_LEVEL = 9

        /** 文字亮度的可调范围。 */
        const val MIN_TEXT_BRIGHTNESS = 0.2f
        const val MAX_TEXT_BRIGHTNESS = 1f
        const val TEXT_BRIGHTNESS_STEP = 0.05f
    }
}

/*
 * 下面几个是纯整数 / 浮点运算，不碰任何 Android 或 Compose 类型，
 * 所以能在本地 JVM 单元测试里直接验证。
 */

/** 把 0..255 的三通道打包成 `0xRRGGBB`，越界会被夹住。 */
fun packRgb(red: Int, green: Int, blue: Int): Int =
    (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

fun redOf(rgb: Int): Int = (rgb shr 16) and 0xFF

fun greenOf(rgb: Int): Int = (rgb shr 8) and 0xFF

fun blueOf(rgb: Int): Int = rgb and 0xFF

/**
 * 按亮度系数缩放颜色。
 *
 * 系数夹在 0f..1f：只压暗、不外扩 —— 外扩会把颜色推向过曝，
 * 反而看不出原来的色调。
 */
fun applyBrightness(rgb: Int, brightness: Float): Int {
    val factor = brightness.coerceIn(0f, 1f)
    return packRgb(
        (redOf(rgb) * factor).toInt(),
        (greenOf(rgb) * factor).toInt(),
        (blueOf(rgb) * factor).toInt(),
    )
}

/** 在 `0..255` 范围内走一步，用于 RGB 步进。 */
fun stepChannel(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 255)

/** 字号走一磅，夹在合法范围内。 */
fun stepFontSize(size: Int, delta: Int): Int =
    (size + delta).coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE)

/** 段间距走 1 dp，夹在合法范围内。 */
fun stepSpacingLevel(level: Int, delta: Int): Int =
    (level + delta).coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL)

/**
 * 段间距显示的文案：就是那个数字本身（dp 值）。
 *
 * 只把越界值夹回合法范围，免得界面上冒出负数或离谱的大数；
 * 阅读菜单与设置页显示的是同一份文案，所以放在这里而不是各写一份。
 */
fun spacingLabel(level: Int): String =
    level.coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL).toString()

/** 把屏幕亮度格式化成百分比；跟随系统返回 `跟随系统`。 */
fun formatScreenBrightness(value: Float): String =
    if (value < 0f) "跟随系统" else "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
