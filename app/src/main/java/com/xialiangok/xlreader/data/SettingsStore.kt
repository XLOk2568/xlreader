package com.xialiangok.xlreader.data

import android.content.Context
import androidx.core.content.edit

/**
 * 阅读偏好设置。
 *
 * 用 SharedPreferences 做最轻量的持久化，不引入任何额外依赖；
 * 默认值都偏向「安静、省电、少动效」。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取全部偏好；越界或损坏的值都会被夹回合法范围。 */
    fun read(): ReaderPreferences = ReaderPreferences(
        fontSize = prefs.getInt(KEY_FONT, ReaderPreferences.DEFAULT_FONT_SIZE)
            .coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
        spacingLevel = prefs.getInt(KEY_SPACING, ReaderPreferences.DEFAULT_SPACING_LEVEL)
            .coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_ON, false),
        reducedMotion = prefs.getBoolean(KEY_MOTION, true),
        // 只保留低 24 位，避免被当成负数的 ARGB 写回去。
        textColor = prefs.getInt(KEY_TEXT_COLOR, ReaderPreferences.DEFAULT_TEXT_COLOR) and 0xFFFFFF,
        textBrightness = prefs
            .getFloat(KEY_TEXT_BRIGHTNESS, ReaderPreferences.MAX_TEXT_BRIGHTNESS)
            .coerceIn(ReaderPreferences.MIN_TEXT_BRIGHTNESS, ReaderPreferences.MAX_TEXT_BRIGHTNESS),
        screenBrightness = prefs
            .getFloat(KEY_SCREEN_BRIGHTNESS, ReaderPreferences.BRIGHTNESS_SYSTEM),
    )

    /** 写回全部偏好。 */
    fun save(value: ReaderPreferences) {
        prefs.edit {
            putInt(
                KEY_FONT,
                value.fontSize.coerceIn(ReaderPreferences.MIN_FONT_SIZE, ReaderPreferences.MAX_FONT_SIZE),
            )
            putInt(
                KEY_SPACING,
                value.spacingLevel
                    .coerceIn(ReaderPreferences.MIN_SPACING_LEVEL, ReaderPreferences.MAX_SPACING_LEVEL),
            )
            putBoolean(KEY_KEEP_ON, value.keepScreenOn)
            putBoolean(KEY_MOTION, value.reducedMotion)
            putInt(KEY_TEXT_COLOR, value.textColor and 0xFFFFFF)
            putFloat(KEY_TEXT_BRIGHTNESS, value.textBrightness)
            putFloat(KEY_SCREEN_BRIGHTNESS, value.screenBrightness)
        }
    }

    companion object {
        private const val PREFS_NAME = "xlreader_settings"
        // 字号曾经是 0..2 的档位，存的是另一套键；换成磅值后直接换个键，
        // 免得旧的 "0/1/2" 被当成 1 磅、2 磅读出来（那是根本看不见的字）。
        private const val KEY_FONT = "font_size"
        private const val KEY_MOTION = "reduced_motion"
        private const val KEY_KEEP_ON = "keep_screen_on"
        // 段间距曾经是 0..2 的三档，那套键存的是档位号；换成 dp 数值后直接换个键，
        // 免得旧的 "0/1/2" 被当成 0/1/2 dp 读出来（比「紧凑」还挤，等于悄悄改小了间距）。
        private const val KEY_SPACING = "paragraph_spacing"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_TEXT_BRIGHTNESS = "text_brightness"
        private const val KEY_SCREEN_BRIGHTNESS = "screen_brightness"
    }
}
