package com.xialiangok.xlreader.data.sensor

import androidx.compose.runtime.Immutable
import kotlin.math.atan2
import kotlin.math.max

/**
 * 体感手势能执行的动作。
 *
 * 「退出 / 单击 / 返回」是页面级动作，「向下翻页 / 向上翻页」本身带一个速度（一次滚多少条目）。
 */
enum class GestureAction(val label: String) {
    /** 退出本应用：结束任务并从最近任务里移除。 */
    Exit("退出"),

    /** 等价于「单击屏幕」：正文页弹出/收起快捷菜单，列表页打开屏幕上正中央的那一项。 */
    Tap("单击"),

    /** 等价于系统返回：往后退一层。 */
    Back("返回"),

    /** 向下翻页：一次向下滚动 [SensorGesture.speed] 条目。 */
    ScrollDown("向下翻页"),

    /** 向上翻页：一次向上滚动 [SensorGesture.speed] 条目。 */
    ScrollUp("向上翻页");

    /** 只有翻页类手势需要「速度」这一项。 */
    val hasSpeed: Boolean get() = this == ScrollDown || this == ScrollUp
}

/** 手势可以生效的页面（按用户口径只列三个内容页）。 */
enum class GesturePage(val label: String) {
    FileList("文件列表"),
    Catalog("章节目录"),
    Reader("正文阅读"),
}

/**
 * 一个录制好的体感手势：一个倾斜姿势 + 一个动作。
 *
 * 只保存**最大和最小的角度**（roll 横滚 / pitch 俯仰各一对），判定时再按范围放宽一点容差，
 * 所以存储里就是四个数字，人眼可读、也方便以后换判定模型。
 */
@Immutable
data class SensorGesture(
    val id: String,
    val name: String,
    val action: GestureAction = GestureAction.Tap,
    val minRoll: Float = 0f,
    val maxRoll: Float = 0f,
    val minPitch: Float = 0f,
    val maxPitch: Float = 0f,
    /** 单个手势的开关。 */
    val enabled: Boolean = true,
    /** 翻页手势一次滚动的条目数（速度）。 */
    val speed: Int = DEFAULT_SPEED,
) {
    companion object {
        const val MIN_SPEED = 1
        const val MAX_SPEED = 50
        const val DEFAULT_SPEED = 6
    }
}

/**
 * 体感手势的全部设置。
 *
 * @param enabled   总开关。关掉它就完全不再注册传感器（最省电）。
 * @param intervalMs 检测间隔（毫秒）。**所有手势共用一个**，就是传感器回调的周期。
 * @param pages     在哪些页面启用手势。不在列表里的页面一进去就不注册传感器。
 * @param gestures  录制好的手势列表。
 */
@Immutable
data class SensorSettings(
    val enabled: Boolean = true,
    val intervalMs: Int = DEFAULT_INTERVAL_MS,
    val pages: Set<GesturePage> = GesturePage.entries.toSet(),
    val gestures: List<SensorGesture> = emptyList(),
) {
    /**
     * 某个页面此刻真正要检测的手势。
     *
     * 三种情况都返回空：总开关关掉、这个页面没启用、手势自己被关掉。
     * 返回空就等于「这一页不用检测」，检测任务据此立刻停掉。
     */
    fun activeOn(page: GesturePage): List<SensorGesture> =
        if (!enabled || page !in pages) emptyList() else gestures.filter { it.enabled }

    companion object {
        const val MIN_INTERVAL_MS = 50
        const val MAX_INTERVAL_MS = 2000
        /** 默认 200ms ≈ 5Hz，示例代码里推荐的低功耗采样率。 */
        const val DEFAULT_INTERVAL_MS = 200
        const val INTERVAL_STEP_MS = 10

        /** 录制流程：先倒计时 3 秒，再在 10 秒内最多采 200 个点。 */
        const val COUNTDOWN_SECONDS = 3
        const val RECORD_SECONDS = 10
        const val MAX_SAMPLES = 200

        /** 判定容差的下限（度）：录得再窄也要留出能复现的余量。 */
        const val MIN_TOLERANCE_DEG = 6f

        /** 判定容差的上限（度）：录到一大片动作时不至于把整个半球都算进来。 */
        const val MAX_TOLERANCE_DEG = 25f
    }
}

/** 传感器的一次三轴采样（单位 m/s²）。 */
data class TiltSample(val x: Float, val y: Float, val z: Float)

/** 录制结果：只有最大和最小的角度。 */
data class TiltRange(
    val minRoll: Float,
    val maxRoll: Float,
    val minPitch: Float,
    val maxPitch: Float,
)

/** 低通滤波系数，与「加速度计传感示例代码」一致：越接近 1 越平滑、也越迟钝。 */
const val TILT_FILTER_ALPHA = 0.8f

/** 一阶低通滤波：把抖动压掉，免得手一抖就命中。 */
fun smoothTilt(previous: Float, current: Float, alpha: Float = TILT_FILTER_ALPHA): Float =
    alpha * previous + (1 - alpha) * current

/** 横滚角（左右倾斜），单位度：x 与 z 的夹角。 */
fun tiltRoll(x: Float, z: Float): Float =
    Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()

/** 俯仰角（前后翻转），单位度：y 与 z 的夹角。 */
fun tiltPitch(y: Float, z: Float): Float =
    Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()

/**
 * 把一串采样整理成角度范围：只留 roll / pitch 各自的最大最小值。
 *
 * 一个点都没有时返回 null（界面上会提示重录，而不是存一个空手势）。
 */
fun summarizeTilt(samples: List<TiltSample>): TiltRange? {
    if (samples.isEmpty()) return null

    var minRoll = Float.MAX_VALUE
    var maxRoll = -Float.MAX_VALUE
    var minPitch = Float.MAX_VALUE
    var maxPitch = -Float.MAX_VALUE

    for (sample in samples) {
        val roll = tiltRoll(sample.x, sample.z)
        val pitch = tiltPitch(sample.y, sample.z)
        if (roll < minRoll) minRoll = roll
        if (roll > maxRoll) maxRoll = roll
        if (pitch < minPitch) minPitch = pitch
        if (pitch > maxPitch) maxPitch = pitch
    }

    return TiltRange(minRoll, maxRoll, minPitch, maxPitch)
}

/**
 * 判定容差：范围越窄，容差越大。
 *
 * 用户录一个「静止姿势」时，最大最小角可能只差一两度 —— 不留容差的话根本复现不了；
 * 反过来录到一大片甩腕动作时，容差按范围的一半跟随会让判定宽得离谱，所以上下都夹住。
 */
fun gestureTolerance(min: Float, max: Float): Float =
    max(SensorSettings.MIN_TOLERANCE_DEG, (max - min) * 0.5f)
        .coerceAtMost(SensorSettings.MAX_TOLERANCE_DEG)

/** 当前角度是否落在手势录制出来的范围里（两侧各放宽 [gestureTolerance]）。 */
fun SensorGesture.matches(roll: Float, pitch: Float): Boolean {
    val rollSlack = gestureTolerance(minRoll, maxRoll)
    val pitchSlack = gestureTolerance(minPitch, maxPitch)
    return roll >= minRoll - rollSlack && roll <= maxRoll + rollSlack &&
        pitch >= minPitch - pitchSlack && pitch <= maxPitch + pitchSlack
}

/** 检测间隔走一步（±10ms），夹在合法范围内。 */
fun stepIntervalMs(ms: Int, delta: Int): Int =
    (ms + delta).coerceIn(SensorSettings.MIN_INTERVAL_MS, SensorSettings.MAX_INTERVAL_MS)

/** 翻页速度走一步（±1 条目），夹在合法范围内。 */
fun stepSpeed(speed: Int, delta: Int): Int =
    (speed + delta).coerceIn(SensorGesture.MIN_SPEED, SensorGesture.MAX_SPEED)

/** 把手势的角度范围写成一行给人看的文本。 */
fun formatTiltRange(gesture: SensorGesture): String =
    "横滚 ${gesture.minRoll.round1()}° ~ ${gesture.maxRoll.round1()}°\n" +
        "俯仰 ${gesture.minPitch.round1()}° ~ ${gesture.maxPitch.round1()}°"

/** 保留一位小数，去掉「30.0」后面那个多余的 0。 */
private fun Float.round1(): String {
    val rounded = Math.round(this * 10f) / 10f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}
