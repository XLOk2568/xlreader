package com.xialiangok.xlreader.data

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 页面顶部时间的显示方式.
 *
 * [Wear] 是库自带的弧形时间; [Phone] 是自定义的无弧度数字时间;
 * [None] 完全不显示.
 */
enum class TimeDisplay(val label: String) {
    Wear("WearUI"),
    Phone("PhoneUI"),
    None("None"),
}

/**
 * 阅读相关的全部偏好.
 *
 * 设置页和阅读界面里的快捷菜单操作的是同一份数据, 所以在菜单里改字号,
 * 设置页里看到的也是新值, 反之亦然.
 *
 * 2026-10-31 起颜色不再单独存在 SharedPreferences 里: 正文 / 边缘 / 文件名 /
 * 信息(强调) / 背景这五色属于「主题」, 存在 xlreader_settings_theme.txt.xlr,
 * 这里只放当前生效主题展开后的值. 当前章节 / 最近打开 / 强调 / 目录开关条
 * 四者合并成同一个强调色 ([effectiveAccentColor]).
 */
@Immutable
data class ReaderPreferences(
    /** 标题字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val titleFontSize: Int = DEFAULT_TITLE_FONT_SIZE,
    /** 正文字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    /** 小字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val smallFontSize: Int = DEFAULT_SMALL_FONT_SIZE,
    /** 文件列表里条目名的字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val fileListFontSize: Int = DEFAULT_FILE_LIST_FONT_SIZE,
    /** 段间距, 直接就是数值 (单位 dp, 0..999), 加减一次走 1 dp. */
    val spacingLevel: Int = DEFAULT_SPACING_LEVEL,
    /** 内容区上边距, 单位 dp, 最小 [MIN_MARGIN], 加减一次走 1 dp, 无上限. */
    val marginTop: Int = DEFAULT_MARGIN_TOP,
    /** 内容区下边距, 单位 dp, 最小 [MIN_MARGIN], 加减一次走 1 dp, 无上限. */
    val marginBottom: Int = DEFAULT_MARGIN_BOTTOM,
    /** 页内显式 Spacer 的统一高度, 单位 dp, 0..999, 加减一次走 1 dp. */
    val controlSpacing: Int = DEFAULT_CONTROL_SPACING,
    /** 顶部悬浮时间离屏幕上沿的距离, 单位 dp, 0..999, 只影响 PhoneUI 的顶部时间. */
    val homeHoverTop: Int = DEFAULT_HOME_HOVER,
    /** 页面右下那个悬浮按钮离屏幕下沿的距离, 单位 dp, 0..999. */
    val homeHoverBottom: Int = DEFAULT_HOME_HOVER,
    /** 阅读时保持屏幕常亮. */
    val keepScreenOn: Boolean = false,
    /** 页面顶部时间的显示方式, 只影响用默认时间的那几页. */
    val timeDisplay: TimeDisplay = TimeDisplay.Phone,
    /** 减少动效. 现在固定开启 (见 `XlReaderTheme`), 字段留着只是不改存储结构. */
    val reducedMotion: Boolean = true,
    /** 屏幕亮度 0f..1f, [BRIGHTNESS_SYSTEM] 表示跟随系统. */
    val screenBrightness: Float = BRIGHTNESS_SYSTEM,
    /** 当前生效的主题名, 对应主题文件里的 themename. */
    val themeName: String = DEFAULT_THEME_NAME,
    /** 正文文字颜色, 打包成 `0xRRGGBB`. */
    val textColor: Int = DEFAULT_TEXT_COLOR,
    /** 正文文字亮度系数, 只压暗不外扩. 主题里正文这一项没有 a 标志, 它就是亮度基准. */
    val textBrightness: Float = DEFAULT_TEXT_BRIGHTNESS,
    /** 强调色 (信息色), 打包成 `0xRRGGBB`; 当前章节 / 最近打开 / 目录开关条也共用它. */
    val accentColor: Int = DEFAULT_ACCENT_COLOR,
    /** 强调色原始亮度系数. */
    val accentBrightness: Float = DEFAULT_ACCENT_BRIGHTNESS,
    /** 强调色亮度是否跟随正文亮度. */
    val accentFollow: Boolean = true,
    /** 界面背景色, 打包成 `0xRRGGBB`. */
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    /** 背景色原始亮度系数. */
    val backgroundBrightness: Float = DEFAULT_BACKGROUND_BRIGHTNESS,
    /** 背景色亮度是否跟随正文亮度. */
    val backgroundFollow: Boolean = true,
    /** 边缘色 (按钮与列表项的描边), 打包成 `0xRRGGBB`. */
    val buttonColor: Int = DEFAULT_BUTTON_COLOR,
    /** 边缘色原始亮度系数. */
    val buttonBrightness: Float = DEFAULT_BUTTON_BRIGHTNESS,
    /** 边缘色亮度是否跟随正文亮度. */
    val buttonFollow: Boolean = true,
    /** 文件列表里文件 / 文件夹名的颜色, 打包成 `0xRRGGBB`. */
    val folderColor: Int = DEFAULT_FOLDER_COLOR,
    /** 文件名颜色原始亮度系数. */
    val folderBrightness: Float = DEFAULT_FOLDER_BRIGHTNESS,
    /** 文件名颜色亮度是否跟随正文亮度. */
    val folderFollow: Boolean = true,
) {

    /** 叠加文字亮度之后, 真正用来绘制的 `0xRRGGBB`. */
    val effectiveTextColor: Int
        get() = applyBrightness(textColor, textBrightness)

    /** 次要文字色: 把主文字色压暗一档, 不用再单独给用户一个设置项. */
    val effectiveSecondaryTextColor: Int
        get() = applyBrightness(textColor, textBrightness * SECONDARY_TEXT_FACTOR)

    /** 叠加亮度之后的强调色; 当前章节 / 最近打开 / 目录开关条都用它. */
    val effectiveAccentColor: Int
        get() = applyBrightness(accentColor, followedBrightness(accentBrightness, accentFollow, textBrightness))

    /** 叠加亮度之后的背景色. */
    val effectiveBackgroundColor: Int
        get() = applyBrightness(
            backgroundColor,
            followedBrightness(backgroundBrightness, backgroundFollow, textBrightness),
        )

    /** 叠加亮度之后的边缘色 (按钮 / 列表项描边). */
    val effectiveButtonColor: Int
        get() = applyBrightness(buttonColor, followedBrightness(buttonBrightness, buttonFollow, textBrightness))

    /** 叠加亮度之后的文件名颜色. */
    val effectiveFolderColor: Int
        get() = applyBrightness(
            folderColor,
            followedBrightness(folderBrightness, folderFollow, textBrightness),
        )

    /** 「正在读」章节颜色, 与强调色合并. */
    val effectiveCurrentChapterColor: Int
        get() = effectiveAccentColor

    /** 「最近打开」书名颜色, 与强调色合并. */
    val effectiveLatestColor: Int
        get() = effectiveAccentColor

    /** 目录开关条颜色, 与强调色合并. */
    val effectiveMenuBarColor: Int
        get() = effectiveAccentColor

    companion object {
        /** 默认主题名. */
        const val DEFAULT_THEME_NAME = "system"

        /** 默认正文文字色, 主题 system 的 r250 g220 b100. */
        const val DEFAULT_TEXT_COLOR = 0xFADC64

        /** 默认正文亮度 90%, 也是其他项「跟随亮度」的基准. */
        const val DEFAULT_TEXT_BRIGHTNESS = 0.9f

        /** 默认强调色 (信息色), 取主题 system 的 r132 g66 b160. */
        const val DEFAULT_ACCENT_COLOR = 0x8442A0

        /** 默认强调色亮度 80%. */
        const val DEFAULT_ACCENT_BRIGHTNESS = 0.8f

        /** 默认背景色, 纯黑. */
        const val DEFAULT_BACKGROUND_COLOR = 0x000000

        /** 默认背景亮度 0%. */
        const val DEFAULT_BACKGROUND_BRIGHTNESS = 0f

        /** 默认边缘色, 主题 system 的 r80 g80 b80. */
        const val DEFAULT_BUTTON_COLOR = 0x505050

        /** 默认边缘色亮度 60%. */
        const val DEFAULT_BUTTON_BRIGHTNESS = 0.6f

        /** 默认文件名颜色, 主题 system 的 r166 g132 b36. */
        const val DEFAULT_FOLDER_COLOR = 0xA68424

        /** 默认文件名颜色亮度 60%. */
        const val DEFAULT_FOLDER_BRIGHTNESS = 0.6f

        /** 颜色透明度范围, 0 = 全透明, 255 = 不透明. */
        const val MIN_COLOR_ALPHA = 0
        const val MAX_COLOR_ALPHA = 255
        const val DEFAULT_COLOR_ALPHA = 255

        /** 次要文字相对主文字压暗的比例. */
        const val SECONDARY_TEXT_FACTOR = 0.66f

        /** 屏幕亮度「跟随系统」. */
        const val BRIGHTNESS_SYSTEM = -1f

        /** 正文字号的取值范围, 默认 19 磅. */
        const val MIN_FONT_SIZE = 1
        const val MAX_FONT_SIZE = 999
        const val DEFAULT_FONT_SIZE = 19

        /** 标题字号的默认值. */
        const val DEFAULT_TITLE_FONT_SIZE = 20

        /** 小字号的默认值. */
        const val DEFAULT_SMALL_FONT_SIZE = 15

        /** 文件列表条目名默认字号. */
        const val DEFAULT_FILE_LIST_FONT_SIZE = 16

        /** 段间距的取值范围 (单位 dp), 和字号一样用「− / ＋」一次走 1. */
        const val MIN_SPACING_LEVEL = 0
        const val MAX_SPACING_LEVEL = 999
        const val DEFAULT_SPACING_LEVEL = 12

        /** 上下边距的取值范围, 最小 2 dp, 无上限. */
        const val MIN_MARGIN = 2
        const val DEFAULT_MARGIN_TOP = 18
        const val DEFAULT_MARGIN_BOTTOM = 86

        /** 大多控件间距 (页内显式 Spacer 的统一高度), 0..999, 默认 4 dp. */
        const val MIN_CONTROL_SPACING = 0
        const val MAX_CONTROL_SPACING = 999
        const val DEFAULT_CONTROL_SPACING = 4

        /** 顶部悬浮时间 / 底部悬浮按钮离屏幕边缘的距离, 0..999, 默认 4 dp. */
        const val MIN_HOME_HOVER = 0
        const val MAX_HOME_HOVER = 999
        const val DEFAULT_HOME_HOVER = 4

        /** 颜色亮度的可调范围, 所有颜色共用同一套档位: 1%..100%, 一次走 1%. */
        const val MIN_TEXT_BRIGHTNESS = 0.01f
        const val MAX_TEXT_BRIGHTNESS = 1f
        const val TEXT_BRIGHTNESS_STEP = 0.01f
    }
}

/** 所有颜色共用的亮度档位: 1% 到 100%, 一次走 1%. */
val BRIGHTNESS_STEPS: List<Float> = (1..100).map { it / 100f }

/*
 * 下面几个是纯整数 / 浮点运算, 不碰任何 Android 或 Compose 类型,
 * 所以能在本地 JVM 单元测试里直接验证.
 */

/** 把 0..255 的三通道打包成 `0xRRGGBB`, 越界会被夹住. */
fun packRgb(red: Int, green: Int, blue: Int): Int =
    (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

fun redOf(rgb: Int): Int = (rgb shr 16) and 0xFF

fun greenOf(rgb: Int): Int = (rgb shr 8) and 0xFF

fun blueOf(rgb: Int): Int = rgb and 0xFF

/**
 * 按亮度系数缩放颜色.
 *
 * 系数夹在 0f..1f, 只压暗, 不外扩, 外扩会把颜色推向过曝,
 * 反而看不出原来的色调.
 */
fun applyBrightness(rgb: Int, brightness: Float): Int {
    val factor = brightness.coerceIn(0f, 1f)
    return packRgb(
        (redOf(rgb) * factor).toInt(),
        (greenOf(rgb) * factor).toInt(),
        (blueOf(rgb) * factor).toInt(),
    )
}

/**
 * 项目实际使用的亮度.
 *
 * 主题里标了 a=1 的项目跟随正文亮度: 正文亮度相对默认 90% 变了多少倍,
 * 它的显示亮度就跟着乘多少倍; 没标 a 的项目一直用自己存的 s.
 */
fun followedBrightness(raw: Float, follow: Boolean, textBrightness: Float): Float {
    val base = if (follow) raw * (textBrightness / ReaderPreferences.DEFAULT_TEXT_BRIGHTNESS) else raw
    return base.coerceIn(0f, 1f)
}

/** 在 `0..255` 范围内走一步, 用于 RGB 步进. */
fun stepChannel(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 255)

/** 亮度走一档 (1%), 夹在 1%..100%; 找不到当前值时就近取. */
fun stepBrightness(current: Float, delta: Int): Float {
    val index = BRIGHTNESS_STEPS.indexOfFirst { abs(it - current) < 0.001f }
        .takeIf { it >= 0 }
        ?: BRIGHTNESS_STEPS.indices.minByOrNull { abs(BRIGHTNESS_STEPS[it] - current) }
        ?: 0
    return BRIGHTNESS_STEPS[(index + delta).coerceIn(0, BRIGHTNESS_STEPS.lastIndex)]
}

/** 亮度显示的文案, 例如 `80%`. */
fun formatBrightness(value: Float): String =
    "${(value.coerceIn(ReaderPreferences.MIN_TEXT_BRIGHTNESS, ReaderPreferences.MAX_TEXT_BRIGHTNESS) * 100).roundToInt()}%"

/** 字号走一磅, 夹在合法范围内. */
fun stepFontSize(size: Int, delta: Int): Int =
    (size + delta).coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE)

/** 边距走 1 dp, 只保底不封顶. */
fun stepMargin(value: Int, delta: Int): Int =
    (value + delta).coerceAtLeast(ReaderPreferences.MIN_MARGIN)

/** 段间距走 1 dp, 夹在合法范围内. */
fun stepSpacingLevel(level: Int, delta: Int): Int =
    (level + delta).coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL)

/** 大多控件间距走 1 dp, 夹在合法范围内. */
fun stepControlSpacing(value: Int, delta: Int): Int =
    (value + delta).coerceIn(ReaderPreferences.MIN_CONTROL_SPACING, ReaderPreferences.MAX_CONTROL_SPACING)

/** 顶部 / 底部悬浮距离走 1 dp, 夹在合法范围内. */
fun stepHomeHover(value: Int, delta: Int): Int =
    (value + delta).coerceIn(ReaderPreferences.MIN_HOME_HOVER, ReaderPreferences.MAX_HOME_HOVER)

/**
 * 段间距显示的文案: 就是那个数字本身 (dp 值).
 *
 * 只把越界值夹回合法范围, 免得界面上冒出负数或离谱的大数,
 * 阅读菜单与设置页显示的是同一份文案, 所以放在这里而不是各写一份.
 */
fun spacingLabel(level: Int): String =
    level.coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL).toString()

/** 把屏幕亮度格式化成百分比, 跟随系统返回 `跟随系统`. */
fun formatScreenBrightness(value: Float): String =
    if (value < 0f) "跟随系统" else "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
