package com.xialiangok.xlreader.data

import android.content.Context
import androidx.core.content.edit

/**
 * 阅读偏好设置.
 *
 * 用 SharedPreferences 做最轻量的持久化, 不引入任何额外依赖,
 * 默认值都偏向「安静, 省电, 少动效」.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取全部偏好, 越界或损坏的值都会被夹回合法范围. */
    fun read(): ReaderPreferences = ReaderPreferences(
        titleFontSize = prefs.getInt(KEY_TITLE_FONT, ReaderPreferences.DEFAULT_TITLE_FONT_SIZE)
            .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
        fontSize = prefs.getInt(KEY_FONT, ReaderPreferences.DEFAULT_FONT_SIZE)
            .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
        smallFontSize = prefs.getInt(KEY_SMALL_FONT, ReaderPreferences.DEFAULT_SMALL_FONT_SIZE)
            .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
        spacingLevel = prefs.getInt(KEY_SPACING, ReaderPreferences.DEFAULT_SPACING_LEVEL)
            .coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL),
        marginTop = prefs.getInt(KEY_MARGIN_TOP, ReaderPreferences.DEFAULT_MARGIN)
            .coerceAtLeast(ReaderPreferences.MIN_MARGIN),
        marginBottom = prefs.getInt(KEY_MARGIN_BOTTOM, ReaderPreferences.DEFAULT_MARGIN)
            .coerceAtLeast(ReaderPreferences.MIN_MARGIN),
        controlSpacing = prefs.getInt(KEY_CONTROL_SPACING, ReaderPreferences.DEFAULT_CONTROL_SPACING)
            .coerceIn(ReaderPreferences.MIN_CONTROL_SPACING, ReaderPreferences.MAX_CONTROL_SPACING),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_ON, false),
        timeDisplay = TimeDisplay.entries
            .getOrNull(prefs.getInt(KEY_TIME_DISPLAY, TimeDisplay.Wear.ordinal))
            ?: TimeDisplay.Wear,
        reducedMotion = prefs.getBoolean(KEY_MOTION, true),
        // 只保留低 24 位, 避免被当成负数的 ARGB 写回去.
        textColor = prefs.getInt(KEY_TEXT_COLOR, ReaderPreferences.DEFAULT_TEXT_COLOR) and 0xFFFFFF,
        textColorAlpha = prefs.getInt(KEY_TEXT_COLOR_ALPHA, ReaderPreferences.DEFAULT_COLOR_ALPHA)
            .coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA),
        textBrightness = prefs
            .getFloat(KEY_TEXT_BRIGHTNESS, ReaderPreferences.MAX_TEXT_BRIGHTNESS)
            .coerceIn(ReaderPreferences.MIN_TEXT_BRIGHTNESS, ReaderPreferences.MAX_TEXT_BRIGHTNESS),
        accentColor = prefs.getInt(KEY_ACCENT_COLOR, ReaderPreferences.DEFAULT_ACCENT_COLOR) and 0xFFFFFF,
        accentColorAlpha = prefs.getInt(KEY_ACCENT_COLOR_ALPHA, ReaderPreferences.DEFAULT_COLOR_ALPHA)
            .coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA),
        backgroundColor = prefs.getInt(KEY_BACKGROUND_COLOR, ReaderPreferences.DEFAULT_BACKGROUND_COLOR) and 0xFFFFFF,
        buttonColor = prefs.getInt(KEY_BUTTON_COLOR, ReaderPreferences.DEFAULT_BUTTON_COLOR) and 0xFFFFFF,
        folderColor = prefs.getInt(KEY_FOLDER_COLOR, ReaderPreferences.DEFAULT_FOLDER_COLOR) and 0xFFFFFF,
        latestColor = prefs.getInt(KEY_LATEST_COLOR, ReaderPreferences.DEFAULT_LATEST_COLOR) and 0xFFFFFF,
        currentChapterColor = prefs.getInt(
            KEY_CURRENT_CHAPTER_COLOR,
            ReaderPreferences.DEFAULT_CURRENT_CHAPTER_COLOR,
        ) and 0xFFFFFF,
        menuBarColor = prefs.getInt(KEY_MENU_BAR_COLOR, ReaderPreferences.DEFAULT_MENU_BAR_COLOR) and 0xFFFFFF,
        screenBrightness = prefs
            .getFloat(KEY_SCREEN_BRIGHTNESS, ReaderPreferences.BRIGHTNESS_SYSTEM),
    )

    /** 写回全部偏好. */
    fun save(value: ReaderPreferences) {
        prefs.edit {
            putInt(
                KEY_TITLE_FONT,
                value.titleFontSize.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            )
            putInt(
                KEY_FONT,
                value.fontSize.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            )
            putInt(
                KEY_SMALL_FONT,
                value.smallFontSize.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            )
            putInt(
                KEY_SPACING,
                value.spacingLevel
                    .coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL),
            )
            putInt(KEY_MARGIN_TOP, value.marginTop.coerceAtLeast(ReaderPreferences.MIN_MARGIN))
            putInt(KEY_MARGIN_BOTTOM, value.marginBottom.coerceAtLeast(ReaderPreferences.MIN_MARGIN))
            putInt(
                KEY_CONTROL_SPACING,
                value.controlSpacing
                    .coerceIn(ReaderPreferences.MIN_CONTROL_SPACING, ReaderPreferences.MAX_CONTROL_SPACING),
            )
            putBoolean(KEY_KEEP_ON, value.keepScreenOn)
            putInt(KEY_TIME_DISPLAY, value.timeDisplay.ordinal)
            putBoolean(KEY_MOTION, value.reducedMotion)
            putInt(KEY_TEXT_COLOR, value.textColor and 0xFFFFFF)
            putInt(
                KEY_TEXT_COLOR_ALPHA,
                value.textColorAlpha
                    .coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA),
            )
            putFloat(KEY_TEXT_BRIGHTNESS, value.textBrightness)
            putInt(KEY_ACCENT_COLOR, value.accentColor and 0xFFFFFF)
            putInt(
                KEY_ACCENT_COLOR_ALPHA,
                value.accentColorAlpha
                    .coerceIn(ReaderPreferences.MIN_COLOR_ALPHA, ReaderPreferences.MAX_COLOR_ALPHA),
            )
            putInt(KEY_BACKGROUND_COLOR, value.backgroundColor and 0xFFFFFF)
            putInt(KEY_BUTTON_COLOR, value.buttonColor and 0xFFFFFF)
            putInt(KEY_FOLDER_COLOR, value.folderColor and 0xFFFFFF)
            putInt(KEY_LATEST_COLOR, value.latestColor and 0xFFFFFF)
            putInt(KEY_CURRENT_CHAPTER_COLOR, value.currentChapterColor and 0xFFFFFF)
            putInt(KEY_MENU_BAR_COLOR, value.menuBarColor and 0xFFFFFF)
            putFloat(KEY_SCREEN_BRIGHTNESS, value.screenBrightness)
        }
    }

    /** 上次浏览的目录, 没记录过 (或那本目录已经被删了) 返回 null. */
    fun readBrowserDir(): String? = prefs.getString(KEY_BROWSER_DIR, null)

    /**
     * 记下当前浏览的目录, 下次启动直接回到这里.
     *
     * 用 `apply()` (`edit {}` 的默认行为) 异步落盘, 进出目录, 切页面, 退出应用时都会写一次,
     * 不能在这条路径上做同步 IO.
     */
    fun saveBrowserDir(path: String) {
        prefs.edit { putString(KEY_BROWSER_DIR, path) }
    }

    /**
     * 最近一次打开的那本 epub, 没记录过返回 null.
     *
     * 这**不是**「上次停在哪个目录」的替代品, 目录仍然走 [readBrowserDir] 那一套.
     * 它只用来给文件列表做一次启动定位, 和书并排的 xlrLatest.txt 万一被删了,
     * 还能退回「上次打开的那本书」这个位置.
     */
    fun readLastBookPath(): String? = prefs.getString(KEY_LAST_BOOK, null)

    /** 记下最近一次打开的 epub. */
    fun saveLastBookPath(path: String) {
        prefs.edit { putString(KEY_LAST_BOOK, path) }
    }

    companion object {
        private const val PREFS_NAME = "xlreader_settings"
        // 文件浏览器上次停留在哪个目录 (应用重启后从它继续, 而不是回到存储根).
        private const val KEY_BROWSER_DIR = "browser_dir"
        // 最近一次打开的 epub, 仅用于启动时在文件列表里定位 (见 readLastBookPath).
        private const val KEY_LAST_BOOK = "last_book_path"
        // 字号曾经是 0..2 的档位, 存的是另一套键, 换成磅值后直接换个键,
        // 免得旧的 "0/1/2" 被当成 1 磅 / 2 磅读出来 (那是根本看不见的字).
        private const val KEY_FONT = "font_size"
        private const val KEY_TITLE_FONT = "title_font_size"
        private const val KEY_SMALL_FONT = "small_font_size"
        private const val KEY_MOTION = "reduced_motion"
        private const val KEY_KEEP_ON = "keep_screen_on"
        private const val KEY_TIME_DISPLAY = "time_display"
        // 段间距曾经是 0..2 的三档, 那套键存的是档位号, 换成 dp 数值后直接换个键.
        private const val KEY_SPACING = "paragraph_spacing"
        private const val KEY_MARGIN_TOP = "margin_top"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom"
        private const val KEY_CONTROL_SPACING = "control_spacing"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_TEXT_COLOR_ALPHA = "text_color_alpha"
        private const val KEY_TEXT_BRIGHTNESS = "text_brightness"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_ACCENT_COLOR_ALPHA = "accent_color_alpha"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_BUTTON_COLOR = "button_color"
        private const val KEY_FOLDER_COLOR = "folder_color"
        private const val KEY_LATEST_COLOR = "latest_color"
        private const val KEY_CURRENT_CHAPTER_COLOR = "current_chapter_color"
        private const val KEY_MENU_BAR_COLOR = "menu_bar_color"
        private const val KEY_SCREEN_BRIGHTNESS = "screen_brightness"
    }
}
