package com.xialiangok.xlreader.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 体感手势的纯计算部分：角度换算、滤波、录制结果整理、命中判定、步进夹取。
 *
 * 这些函数不碰 Android 也不碰 Compose（传感器注册与采样在 `TiltSensor.kt` 里），
 * 所以能在本地 JVM 上直接跑。
 */
class SensorGestureTest {

    /** 平放（重力全在 z 轴）时两个角度都是 0。 */
    @Test
    fun `flat watch is zero degrees`() {
        assertEquals(0f, tiltRoll(0f, 9.8f), 0.001f)
        assertEquals(0f, tiltPitch(0f, 9.8f), 0.001f)
    }

    /** 向右立起来是 +90°，向前翻起来也是 +90°。 */
    @Test
    fun `right and forward tilt are ninety degrees`() {
        assertEquals(90f, tiltRoll(9.8f, 0f), 0.001f)
        assertEquals(90f, tiltPitch(9.8f, 0f), 0.001f)
    }

    /** 反方向是 -90°。 */
    @Test
    fun `opposite tilt is minus ninety degrees`() {
        assertEquals(-90f, tiltRoll(-9.8f, 0f), 0.001f)
        assertEquals(-90f, tiltPitch(-9.8f, 0f), 0.001f)
    }

    /** 低通滤波：新值只占 1-alpha 的权重，所以抖动进不来。 */
    @Test
    fun `filter only takes a fifth of the new value`() {
        assertEquals(2f, smoothTilt(0f, 10f), 0.001f)
        assertEquals(10f, smoothTilt(10f, 10f), 0.001f)
    }

    /** 一个点都没采到时没有角度范围可存。 */
    @Test
    fun `summary of no samples is null`() {
        assertNull(summarizeTilt(emptyList()))
    }

    /** 整理只留最大和最小的角度。 */
    @Test
    fun `summary keeps only the extremes`() {
        val range = summarizeTilt(
            listOf(
                TiltSample(0f, 0f, 9.8f), // roll 0 / pitch 0
                TiltSample(9.8f, 0f, 9.8f), // roll 45 / pitch 0
                TiltSample(0f, 9.8f, 9.8f), // roll 0 / pitch 45
            ),
        )

        assertEquals(0f, range!!.minRoll, 0.001f)
        assertEquals(45f, range.maxRoll, 0.001f)
        assertEquals(0f, range.minPitch, 0.001f)
        assertEquals(45f, range.maxPitch, 0.001f)
    }

    /** 录得越窄，容差越大（不然静止的姿势根本复现不了）。 */
    @Test
    fun `narrow range gets the floor tolerance`() {
        assertEquals(
            SensorSettings.MIN_TOLERANCE_DEG,
            gestureTolerance(30f, 31f),
            0.001f,
        )
    }

    /** 录到一大片动作时容差有上限，不会把半个球面都算进来。 */
    @Test
    fun `wide range tolerance is capped`() {
        assertEquals(SensorSettings.MAX_TOLERANCE_DEG, gestureTolerance(-90f, 90f), 0.001f)
    }

    /** 落在范围内算命中。 */
    @Test
    fun `angle inside the recorded range matches`() {
        assertTrue(gesture(30f, 40f, -5f, 5f).matches(35f, 0f))
    }

    /** 离得太远就不算。 */
    @Test
    fun `angle far outside the recorded range does not match`() {
        assertFalse(gesture(30f, 40f, -5f, 5f).matches(80f, 0f))
    }

    /** 静止录到的范围只有一两度，靠容差才命中得了一个「差不多」的姿势。 */
    @Test
    fun `tolerance lets a slightly different pose match`() {
        val recorded = gesture(30f, 31f, 0f, 1f)

        assertTrue(recorded.matches(36f, 5f))
        assertFalse(recorded.matches(45f, 20f))
    }

    /** roll 对了但 pitch 差很远，不算命中（两个角度都要落在范围里）。 */
    @Test
    fun `both angles must be inside the range`() {
        assertFalse(gesture(30f, 40f, -5f, 5f).matches(35f, 60f))
    }

    /** 翻页速度夹在 1..50。 */
    @Test
    fun `speed steps stay in range`() {
        assertEquals(SensorGesture.MIN_SPEED, stepSpeed(SensorGesture.MIN_SPEED, -1))
        assertEquals(SensorGesture.MAX_SPEED, stepSpeed(SensorGesture.MAX_SPEED, +1))
        assertEquals(7, stepSpeed(6, +1))
    }

    /** 检测间隔夹在 50..2000 毫秒，一步 10 毫秒。 */
    @Test
    fun `interval steps stay in range`() {
        assertEquals(SensorSettings.MIN_INTERVAL_MS, stepIntervalMs(SensorSettings.MIN_INTERVAL_MS, -10))
        assertEquals(SensorSettings.MAX_INTERVAL_MS, stepIntervalMs(SensorSettings.MAX_INTERVAL_MS, +10))
        assertEquals(210, stepIntervalMs(200, +10))
    }

    /** 总开关关掉时，任何页面都不检测。 */
    @Test
    fun `master switch off disables every page`() {
        val settings = SensorSettings(enabled = false, gestures = listOf(gesture(0f, 1f, 0f, 1f)))

        assertTrue(settings.activeOn(GesturePage.Reader).isEmpty())
        assertTrue(settings.activeOn(GesturePage.FileList).isEmpty())
    }

    /** 没勾上的页面不检测。 */
    @Test
    fun `page not enabled gets no gestures`() {
        val settings = SensorSettings(
            pages = setOf(GesturePage.Reader),
            gestures = listOf(gesture(0f, 1f, 0f, 1f)),
        )

        assertEquals(1, settings.activeOn(GesturePage.Reader).size)
        assertTrue(settings.activeOn(GesturePage.Catalog).isEmpty())
    }

    /** 单独关掉的手势不参与检测；全关掉就等于没有手势可检测。 */
    @Test
    fun `gesture switch filters the list`() {
        val on = gesture(0f, 1f, 0f, 1f)
        val off = gesture(0f, 1f, 0f, 1f).copy(id = "b", enabled = false)
        val settings = SensorSettings(gestures = listOf(on, off))

        assertEquals(listOf("a"), settings.activeOn(GesturePage.Reader).map { it.id })

        val allOff = settings.copy(gestures = listOf(off))
        assertTrue(allOff.activeOn(GesturePage.Reader).isEmpty())
    }

    /** 默认设置覆盖三个内容页，且新增时动作是「单击」、速度在范围内。 */
    @Test
    fun `defaults cover the three content pages`() {
        val settings = SensorSettings()

        assertEquals(GesturePage.entries.toSet(), settings.pages)
        assertEquals(SensorSettings.DEFAULT_INTERVAL_MS, settings.intervalMs)
        assertEquals(GestureAction.Tap, SensorGesture(id = "x", name = "x").action)
        assertEquals(SensorGesture.DEFAULT_SPEED, SensorGesture(id = "x", name = "x").speed)
        assertTrue(GestureAction.ScrollDown.hasSpeed)
        assertFalse(GestureAction.Tap.hasSpeed)
    }

    /** 手改角度下限：一次 1°，且不会越过上限（区间反过来这个手势就永远不会命中）。 */
    @Test
    fun `angle min steps clamp to the upper bound`() {
        assertEquals(31f, stepAngleMin(30f, 60f, +1f), 0.001f)
        assertEquals(60f, stepAngleMin(60f, 60f, +1f), 0.001f)
        assertEquals(
            SensorGesture.MIN_ANGLE_DEG,
            stepAngleMin(SensorGesture.MIN_ANGLE_DEG, 60f, -1f),
            0.001f,
        )
    }

    /** 手改角度上限：一次 1°，且不会越过下限。 */
    @Test
    fun `angle max steps clamp to the lower bound`() {
        assertEquals(29f, stepAngleMax(30f, 10f, -1f), 0.001f)
        assertEquals(10f, stepAngleMax(10f, 10f, -1f), 0.001f)
        assertEquals(
            SensorGesture.MAX_ANGLE_DEG,
            stepAngleMax(SensorGesture.MAX_ANGLE_DEG, 10f, +1f),
            0.001f,
        )
    }

    /** 手改出来的范围照样参与判定：把范围填宽，命中就宽松得多 —— 这正是手改的用处。 */
    @Test
    fun `hand edited range still drives matching`() {
        val wide = gesture(-20f, 20f, -20f, 20f)

        assertTrue(wide.matches(15f, -15f))
        assertFalse(wide.matches(80f, 0f))
    }

    /** 单个角度按一位小数显示，整数不带多余的小数点。 */
    @Test
    fun `angle label keeps one decimal`() {
        assertEquals("30°", formatAngle(30f))
        assertEquals("30.4°", formatAngle(30.44f))
    }

    /** 采样节流：第一拍一定采，之后不到一个间隔就不采；处理完对齐到网格、落后太多才重新对齐。 */
    @Test
    fun `sample gate waits for the interval`() {
        assertEquals(100L, dueSampleTimeMs(100L, -1L, 200))
        assertNull(dueSampleTimeMs(150L, 100L, 200))
        assertNull(dueSampleTimeMs(299L, 100L, 200))
        assertEquals(300L, dueSampleTimeMs(300L, 100L, 200))
        // 对齐到间隔网格，而不是直接取 now
        assertEquals(200L, dueSampleTimeMs(300L, 0L, 200))
        // 落后超过两拍（例如刚从后台回来）：重新对齐到当前时刻，不追旧账
        assertEquals(1000L, dueSampleTimeMs(1000L, 100L, 200))
    }

    /** 网格对齐的实际意义：事件比间隔早几毫秒时只会少采第一拍，稳态下不会两拍才采一次。 */
    @Test
    fun `sample gate aligns to the interval grid`() {
        var last = 0L
        val taken = mutableListOf<Long>()
        // 事件每 198ms 来一次，比 200ms 的间隔早 2ms
        for (t in 198L..990L step 198L) {
            val due = dueSampleTimeMs(t, last, 200) ?: continue
            last = due
            taken.add(t)
        }

        assertEquals(listOf(396L, 594L, 792L, 990L), taken)
    }

    private fun gesture(minRoll: Float, maxRoll: Float, minPitch: Float, maxPitch: Float) =
        SensorGesture(
            id = "a",
            name = "测试",
            minRoll = minRoll,
            maxRoll = maxRoll,
            minPitch = minPitch,
            maxPitch = maxPitch,
        )
}
