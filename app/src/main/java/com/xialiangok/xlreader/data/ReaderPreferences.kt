package com.xialiangok.xlreader.data

import androidx.compose.runtime.Immutable

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
 */
@Immutable
data class ReaderPreferences(
    /** 标题字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val titleFontSize: Int = DEFAULT_TITLE_FONT_SIZE,
    /** 正文字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val fontSize: Int = DEFAULT_FONT_SIZE,
    /** 小字号, 直接就是磅值, 1..999, 加减一次走 1 磅. */
    val smallFontSize: Int = DEFAULT_SMALL_FONT_SIZE,
    /** 段间距, 直接就是数值 (单位 dp, 0..999), 加减一次走 1 dp. */
    val spacingLevel: Int = DEFAULT_SPACING_LEVEL,
    /** 内容区上边距, 单位 dp, 最小 [MIN_MARGIN], 加减一次走 1 dp, 无上限. */
    val marginTop: Int = DEFAULT_MARGIN,
    /** 内容区下边距, 单位 dp, 最小 [MIN_MARGIN], 加减一次走 1 dp, 无上限. */
    val marginBottom: Int = DEFAULT_MARGIN,
    /** 页内显式 Spacer 的统一高度, 单位 dp, 0..999, 加减一次走 1 dp. */
    val controlSpacing: Int = DEFAULT_CONTROL_SPACING,
    /** 阅读时保持屏幕常亮. */
    val keepScreenOn: Boolean = false,
    /** 页面顶部时间的显示方式, 只影响用默认时间的那几页. */
    val timeDisplay: TimeDisplay = TimeDisplay.Wear,
    /** 减少动效. 现在固定开启 (见 `XlReaderTheme`), 字段留着只是不改存储结构. */
    val reducedMotion: Boolean = true,
    /** 文字颜色, 打包成 `0xRRGGBB`. */
    val textColor: Int = DEFAULT_TEXT_COLOR,
    /** 文字颜色透明度, 0..255. */
    val textColorAlpha: Int = DEFAULT_COLOR_ALPHA,
    /** 文字亮度系数, 只压暗不外扩. */
    val textBrightness: Float = 1f,
    /** 强调色, 打包成 `0xRRGGBB`, 用于开关/进度/标签这类强调元素. */
    val accentColor: Int = DEFAULT_ACCENT_COLOR,
    /** 强调色透明度, 0..255. */
    val accentColorAlpha: Int = DEFAULT_COLOR_ALPHA,
    /** 界面背景色, 打包成 `0xRRGGBB`. */
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    /** 按钮底色, 打包成 `0xRRGGBB`. */
    val buttonColor: Int = DEFAULT_BUTTON_COLOR,
    /** 文件列表文件夹名颜色, 打包成 `0xRRGGBB`. */
    val folderColor: Int = DEFAULT_FOLDER_COLOR,
    /** 文件列表「最近打开」书名颜色, 打包成 `0xRRGGBB`. */
    val latestColor: Int = DEFAULT_LATEST_COLOR,
    /** 章节目录页「正在读」那一章的文字颜色, 打包成 `0xRRGGBB`. */
    val currentChapterColor: Int = DEFAULT_CURRENT_CHAPTER_COLOR,
    /** 章节目录页顶上那根开关条的底色, 打包成 `0xRRGGBB`. */
    val menuBarColor: Int = DEFAULT_MENU_BAR_COLOR,
    /** 屏幕亮度 0f..1f, [BRIGHTNESS_SYSTEM] 表示跟随系统. */
    val screenBrightness: Float = BRIGHTNESS_SYSTEM,
) {

    /** 叠加文字亮度之后, 真正用来绘制的 `0xRRGGBB`. */
    val effectiveTextColor: Int
        get() = applyBrightness(textColor, textBrightness)

    /** 次要文字色: 把主文字色压暗一档, 不用再单独给用户一个设置项. */
    val effectiveSecondaryTextColor: Int
        get() = applyBrightness(textColor, textBrightness * SECONDARY_TEXT_FACTOR)

    /** 强调色, 用于 Compose 的 `Color`. */
    val effectiveAccentColor: Int
        get() = accentColor

    companion object {
        /** 默认文字色, 与主题里的 onSurface 一致. */
        const val DEFAULT_TEXT_COLOR = 0xE4E4E4

        /** 默认强调色, 与旧版主题里的低饱和青绿一致. */
        const val DEFAULT_ACCENT_COLOR = 0x8FB8A8

        /** 默认背景色, 纯黑. */
        const val DEFAULT_BACKGROUND_COLOR = 0x000000

        /** 默认按钮底色. */
        const val DEFAULT_BUTTON_COLOR = 0x2C2C2E

        /** 默认文件夹名颜色, 橙. */
        const val DEFAULT_FOLDER_COLOR = 0xE09F00

        /** 默认「最近打开」书名颜色, 紫. */
        const val DEFAULT_LATEST_COLOR = 0xCC5BF6

        /** 默认「正在读」章节颜色, 紫. */
        const val DEFAULT_CURRENT_CHAPTER_COLOR = 0xB15FED

        /** 默认目录页开关条颜色, 深紫. */
        const val DEFAULT_MENU_BAR_COLOR = 0x6331BF

        /** 颜色透明度范围, 0 = 全透明, 255 = 不透明. */
        const val MIN_COLOR_ALPHA = 0
        const val MAX_COLOR_ALPHA = 255
        const val DEFAULT_COLOR_ALPHA = 255

        /** 次要文字相对主文字压暗的比例. */
        const val SECONDARY_TEXT_FACTOR = 0.66f

        /** 屏幕亮度「跟随系统」. */
        const val BRIGHTNESS_SYSTEM = -1f

        /** 正文字号的取值范围, 默认 16 磅. */
        const val MIN_FONT_SIZE = 1
        const val MAX_FONT_SIZE = 999
        const val DEFAULT_FONT_SIZE = 16

        /** 标题字号的默认值, 对应原来的 ListHeader 字号. */
        const val DEFAULT_TITLE_FONT_SIZE = 20

        /** 小字号的默认值, 对应原来的 bodyExtraSmall. */
        const val DEFAULT_SMALL_FONT_SIZE = 12

        /**
         * 段间距的取值范围 (单位 dp), 和字号一样用「− / ＋」一次走 1.
         * 默认 9 dp, 就是原来三档里的「适中」.
         */
        const val MIN_SPACING_LEVEL = 0
        const val MAX_SPACING_LEVEL = 999
        const val DEFAULT_SPACING_LEVEL = 9

        /** 上下边距的取值范围, 最小 2 dp, 无上限. */
        const val MIN_MARGIN = 2
        const val DEFAULT_MARGIN = 32

        /** 大多控件间距 (页内显式 Spacer 的统一高度), 0..999, 默认 10 dp. */
        const val MIN_CONTROL_SPACING = 0
        const val MAX_CONTROL_SPACING = 999
        const val DEFAULT_CONTROL_SPACING = 10

        /** 文字亮度的可调范围. */
        const val MIN_TEXT_BRIGHTNESS = 0.2f
        const val MAX_TEXT_BRIGHTNESS = 1f
        const val TEXT_BRIGHTNESS_STEP = 0.05f
    }
}

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

/** 在 `0..255` 范围内走一步, 用于 RGB 步进. */
fun stepChannel(value: Int, delta: Int): Int = (value + delta).coerceIn(0, 255)

/** 透明度走一步, 夹在 `0..255`. */
fun stepAlpha(value: Int, delta: Int): Int =
    (value + delta).coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA)

/** 把 `0xRRGGBB` 与透明度打包成 `0xAARRGGBB`. */
fun packArgb(rgb: Int, alpha: Int): Int =
    (alpha.coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA) shl 24) or
        (rgb and 0xFFFFFF)

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
