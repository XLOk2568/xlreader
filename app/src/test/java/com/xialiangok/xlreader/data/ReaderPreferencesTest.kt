package com.xialiangok.xlreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RGB 打包 / 亮度换算的单元测试。
 *
 * 这些函数是纯整数与浮点运算，不碰 Android 也不碰 Compose，所以能在本地 JVM 上直接跑。
 */
class ReaderPreferencesTest {

    @Test
    fun `packs and unpacks channels`() {
        val rgb = packRgb(0x12, 0x34, 0x56)

        assertEquals(0x123456, rgb)
        assertEquals(0x12, redOf(rgb))
        assertEquals(0x34, greenOf(rgb))
        assertEquals(0x56, blueOf(rgb))
    }

    @Test
    fun `packing clamps out of range channels`() {
        assertEquals(0x00FFFF, packRgb(-5, 300, 255))
        assertEquals(0x000000, packRgb(-1, -1, -1))
    }

    @Test
    fun `full brightness keeps the colour untouched`() {
        assertEquals(0x123456, applyBrightness(0x123456, 1f))
        assertEquals(0xFFFFFF, applyBrightness(0xFFFFFF, 1f))
    }

    /** 亮度只压暗，不外扩 —— 外扩会把颜色推向过曝。 */
    @Test
    fun `brightness above one is clamped and never brightens`() {
        assertEquals(0xFFFFFF, applyBrightness(0xFFFFFF, 5f))
        assertEquals(0x123456, applyBrightness(0x123456, 2f))
    }

    @Test
    fun `brightness below zero becomes black`() {
        assertEquals(0x000000, applyBrightness(0xFFFFFF, -1f))
        assertEquals(0x000000, applyBrightness(0x123456, 0f))
    }

    @Test
    fun `brightness darkens each channel proportionally`() {
        // 255 * 0.5 = 127.5，取整为 127
        assertEquals(0x7F7F7F, applyBrightness(0xFFFFFF, 0.5f))
        // 128 * 0.5 = 64
        assertEquals(0x404040, applyBrightness(0x808080, 0.5f))
    }

    @Test
    fun `effective colour combines the chosen colour and the brightness`() {
        val preferences = ReaderPreferences(textColor = 0x808080, textBrightness = 0.5f)

        assertEquals(0x404040, preferences.effectiveTextColor)
    }

    @Test
    fun `step channel walks one at a time and clamps at both ends`() {
        assertEquals(129, stepChannel(128, +1))
        assertEquals(127, stepChannel(128, -1))
        assertEquals(0, stepChannel(0, -1))
        assertEquals(255, stepChannel(255, +1))
    }

    @Test
    fun `step font size walks one pound and clamps at both ends`() {
        assertEquals(17, stepFontSize(16, +1))
        assertEquals(15, stepFontSize(16, -1))
        assertEquals(ReaderPreferences.MIN_FONT_SIZE, stepFontSize(ReaderPreferences.MIN_FONT_SIZE, -1))
        assertEquals(ReaderPreferences.MAX_FONT_SIZE, stepFontSize(ReaderPreferences.MAX_FONT_SIZE, +1))
    }

    @Test
    fun `step spacing walks one dp and clamps at both ends`() {
        assertEquals(10, stepSpacingLevel(9, +1))
        assertEquals(8, stepSpacingLevel(9, -1))
        assertEquals(
            ReaderPreferences.MIN_SPACING_LEVEL,
            stepSpacingLevel(ReaderPreferences.MIN_SPACING_LEVEL, -1),
        )
        assertEquals(
            ReaderPreferences.MAX_SPACING_LEVEL,
            stepSpacingLevel(ReaderPreferences.MAX_SPACING_LEVEL, +1),
        )
    }

    @Test
    fun `spacing label is the number itself, clamped to the legal range`() {
        assertEquals("0", spacingLabel(0))
        assertEquals("9", spacingLabel(9))
        assertEquals("999", spacingLabel(999))
        // 越界的值夹回范围，界面上不会出现负数或离谱的大数
        assertEquals(ReaderPreferences.MIN_SPACING_LEVEL.toString(), spacingLabel(-5))
        assertEquals(ReaderPreferences.MAX_SPACING_LEVEL.toString(), spacingLabel(9999))
    }

    @Test
    fun `formats screen brightness`() {
        assertEquals("跟随系统", formatScreenBrightness(ReaderPreferences.BRIGHTNESS_SYSTEM))
        assertEquals("50%", formatScreenBrightness(0.5f))
        assertEquals("100%", formatScreenBrightness(1f))
        // 负数一律按「跟随系统」这个哨兵值处理（这是刻意的约定）
        assertEquals("跟随系统", formatScreenBrightness(-2f))
        // 超过 1 的非法值会被夹住，而不是显示 150%
        assertEquals("100%", formatScreenBrightness(1.5f))
    }

    @Test
    fun `defaults match the theme`() {
        val defaults = ReaderPreferences()

        assertEquals(0xE4E4E4, defaults.textColor)
        assertEquals(1f, defaults.textBrightness)
        assertEquals(ReaderPreferences.BRIGHTNESS_SYSTEM, defaults.screenBrightness)
        assertEquals(0xE4E4E4, defaults.effectiveTextColor)
        // 默认 16 磅，也就是原来「中」那一档
        assertEquals(ReaderPreferences.DEFAULT_FONT_SIZE, defaults.fontSize)
    }
}
