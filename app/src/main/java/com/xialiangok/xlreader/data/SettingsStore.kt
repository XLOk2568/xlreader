package com.xialiangok.xlreader.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.util.concurrent.Executors

/**
 * 阅读偏好设置.
 *
 * 2026-10-31 起: 字号 / 边距 / 时间 UI / 上次目录 / 常亮 / 屏幕亮度写进
 * 私有数据目录根部的 `xlreader_settings.txt.xlr`; 颜色写进
 * `xlreader_settings_theme.txt.xlr`. 文件是普通文本, 用户可以直接改.
 *
 * 只剩两样还留在 SharedPreferences: 最近打开的书路径 (只是文件列表的兜底定位,
 * 不在设置文件格式里) 与体感手势 (SenseStore 自己那份).
 */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsFile = File(appContext.dataDir, SETTINGS_FILE_NAME)
    private val themeFile = File(appContext.dataDir, THEME_FILE_NAME)
    private val ioLock = Any()

    /**
     * 上次浏览的目录写得很频繁 (每进一个目录一次), 不在这条路上做同步 IO.
     * 单线程串行写, 后来的目录一定覆盖先前的.
     */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xlreader-settings").apply { isDaemon = true }
    }

    /** 读取全部偏好, 越界或损坏的值都会被夹回合法范围. */
    fun read(): ReaderPreferences = loadScreenSettings().toPreferences(readThemes().active)

    /** 写回字号 / 边距等活动设置, 同时把当前颜色收回主题文件里的当前主题. */
    fun save(value: ReaderPreferences) {
        val themes = readThemes()
        val active = themes.active
        val updatedThemes = themes.copy(
            themes = themes.themes.mapIndexed { index, theme ->
                if (index == themes.usingIndex) value.toTheme(value.themeName.ifBlank { active.name }) else theme
            },
        )
        writeText(themeFile, themeSetText(updatedThemes))

        val screen = loadScreenSettings().copy(
            titleFontSize = value.titleFontSize
                .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            fontSize = value.fontSize
                .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            smallFontSize = value.smallFontSize
                .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            fileListFontSize = value.fileListFontSize
                .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            spacingLevel = value.spacingLevel
                .coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL),
            marginTop = value.marginTop.coerceAtLeast(ReaderPreferences.MIN_MARGIN),
            marginBottom = value.marginBottom.coerceAtLeast(ReaderPreferences.MIN_MARGIN),
            controlSpacing = value.controlSpacing
                .coerceIn(ReaderPreferences.MIN_CONTROL_SPACING, ReaderPreferences.MAX_CONTROL_SPACING),
            homeHoverTop = value.homeHoverTop
                .coerceIn(ReaderPreferences.MIN_HOME_HOVER, ReaderPreferences.MAX_HOME_HOVER),
            homeHoverBottom = value.homeHoverBottom
                .coerceIn(ReaderPreferences.MIN_HOME_HOVER, ReaderPreferences.MAX_HOME_HOVER),
            keepScreenOn = value.keepScreenOn,
            timeDisplay = value.timeDisplay,
            screenBrightness = value.screenBrightness,
        )
        writeText(settingsFile, screenSettingsText(screen))
    }

    /** 主题页保存整套主题 (含 usingitem). */
    fun saveThemes(value: ReaderThemeSet) {
        writeText(themeFile, themeSetText(value))
    }

    /** 读主题文件, 没有就生成默认的一套 system. */
    fun readThemes(): ReaderThemeSet {
        ensureThemeFile()
        return runCatching { readThemeSet(themeFile) }.getOrElse { ReaderThemeSet.Default }
    }

    /** 上次浏览的目录, 没记录过 (或那本目录已经被删了) 返回 null. */
    fun readBrowserDir(): String? = loadScreenSettings().latestPath

    /**
     * 记下当前浏览的目录, 下次启动直接回到这里.
     *
     * 设置文件是普通文本, 每次进出目录都整份重写; 用单线程后台写避免在这条路径上做同步 IO.
     */
    fun saveBrowserDir(path: String) {
        val screen = loadScreenSettings().copy(latestPath = path)
        writer.execute {
            runCatching { writeText(settingsFile, screenSettingsText(screen)) }
        }
    }

    /**
     * 最近一次打开的那本 epub, 没记录过返回 null.
     *
     * 这**不是**「上次停在哪个目录」的替代品, 目录走 [readBrowserDir] (设置文件里的 latestpath).
     * 它只用来给文件列表做一次启动定位, 和书并排的 xlrLatest.txt 万一被删了,
     * 还能退回「上次打开的那本书」这个位置.
     */
    fun readLastBookPath(): String? = prefs.getString(KEY_LAST_BOOK, null)

    /** 记下最近一次打开的 epub. */
    fun saveLastBookPath(path: String) {
        prefs.edit { putString(KEY_LAST_BOOK, path) }
    }

    private fun loadScreenSettings(): ScreenSettings {
        ensureSettingsFile()
        return runCatching { readScreenSettings(settingsFile) }.getOrElse { ScreenSettings() }
    }

    private fun ensureSettingsFile() {
        if (settingsFile.exists()) return
        writeText(settingsFile, screenSettingsText(ScreenSettings()))
    }

    private fun ensureThemeFile() {
        if (themeFile.exists()) return
        writeText(themeFile, themeSetText(ReaderThemeSet.Default))
    }

    /**
     * 先写临时文件再改名, 读写并发时不会读到被截断的半份文件.
     * 改名在 Android 的 ext4 上是原子的, 失败就退回直接覆盖.
     */
    private fun writeText(file: File, text: String) {
        synchronized(ioLock) {
            runCatching {
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeText(text)
                if (!temp.renameTo(file)) {
                    file.writeText(text)
                    temp.delete()
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "xlreader_settings"
        // 最近一次打开的 epub, 仅用于启动时在文件列表里定位 (见 readLastBookPath).
        private const val KEY_LAST_BOOK = "last_book_path"
    }
}
