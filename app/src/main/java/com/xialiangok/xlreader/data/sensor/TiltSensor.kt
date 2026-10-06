package com.xialiangok.xlreader.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * 这台设备能不能用加速度计。
 *
 * 加速度计本身**不需要任何运行时权限** —— Android 只对心率这类「身体传感器」要
 * `BODY_SENSORS`，加速度计不在其列。所以这里返回 false 只剩两种情况：
 * 设备根本没有这个传感器，或者系统 / ROM 把它禁用了；两种都只能去系统设置里确认，
 * 因此界面按「权限引导」的样子做（见 `SensorPermissionScreen`）。
 */
fun hasAccelerometer(context: Context): Boolean {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return false
    return manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
}

/**
 * 体感手势检测器：只听加速度计，把 xyz 换算成横滚/俯仰角，命中哪个手势就回调哪个。
 *
 * 三件事决定了它的功耗：**只注册加速度计**（不开陀螺仪、不用 Rotation Vector）、
 * **按设置里的检测间隔采样**（默认 200ms ≈ 5Hz）、以及**不需要的时候立刻注销**
 * （总开关关掉、当前页面没启用手势、手势全被单独关掉，都走 [stop]）。
 *
 * 采样间隔是注册时交给框架的**建议值**，但很多设备（尤其手表）按固定 ODR 汇报、这个建议
 * 根本传不下去，所以这里**再用 [dueSampleTimeMs] 自己节流一次** —— 设置里的间隔必须说了算，
 * 否则「每个检测周期触发一次」实际变成「每个传感器回调触发一次」。
 *
 * **刻意不去重**（用户口径「每个检测间隔都触发」）：只要当前角度还落在某个手势的范围里，
 * 每个检测周期都会执行一次 —— 所以摆着不动时，翻页类手势会连续滚、单击类会反复开合。
 * 命中多条时按 [MultiGestureMode] 执行：默认只认列表里最靠前的那条；
 * 打开「允许多个手势」后，可以一拍全执行，也可以按顺序逐条执行（相邻两条隔 20ms）。
 *
 * 触发后的休息由设置里的休息间隔决定（**仅限本轮**，休息完继续按检测间隔走）：
 * 休息期直接注销监听、到点自动接回来；设置成 -1 时触发一次就彻底停下。
 * 见 [rest]。
 */
class TiltGestureDetector(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val filtered = FloatArray(3)
    private var primed = false

    private var gestures: List<SensorGesture> = emptyList()
    private var intervalMs = SensorSettings.DEFAULT_INTERVAL_MS

    /** 触发后的休息间隔；0 = 不休息，[SensorSettings.REST_STOP_MS] = 触发一次后停止后续检测。 */
    private var restMs = SensorSettings.DEFAULT_REST_MS

    /** 命中多条时的执行方式；null = 只跑第一个命中的。 */
    private var mode: MultiGestureMode? = null

    /** 「按顺序执行」时用来把同一拍里的第 2、3… 条手势隔 20ms 依次投递出去。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 还在检测吗。休息期到点后要靠它判断该不该自动重新注册；[stop] 会把它关掉。 */
    private var active = false

    /** 休息结束后的自动重新注册（休息期注销监听，到点再由它接回来）。 */
    private val resume = Runnable {
        if (active && gestures.isNotEmpty()) register()
    }

    /** 上一次真正做判定的时刻（[SystemClock.elapsedRealtime] 毫秒）；负数表示还没采过。 */
    private var lastSampleMs = -1L

    /** 命中手势时回调（传感器事件在主线程派发）。 */
    var onTrigger: ((SensorGesture) -> Unit)? = null

    /** 手表上有没有加速度计。没有就不必折腾了。 */
    val available: Boolean get() = accelerometer != null

    /**
     * 注册监听。
     *
     * 第三个参数是微秒，只是**建议值**（[intervalMs] 为 0 就是「不节流」，框架按最快给），
     * 真正的节流在 [onSensorChanged] 里。
     */
    private fun register() {
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, intervalMs * 1000)
    }

    /** 需要检测的手势列表（已经过滤掉被关掉的）、采样间隔、休息间隔，以及多手势的执行方式。 */
    fun start(
        gestures: List<SensorGesture>,
        intervalMs: Int,
        restMs: Int,
        mode: MultiGestureMode?,
    ) {
        if (accelerometer == null) return
        this.gestures = gestures
        this.intervalMs = intervalMs
        this.restMs = restMs
        this.mode = mode
        primed = false
        active = true
        // 上一轮「按顺序执行」还没投递完的、以及休息期的自动重注册，重新开始前一律丢掉。
        handler.removeCallbacksAndMessages(null)
        lastSampleMs = -1L
        register()
    }

    /** 注销监听。重复调用是安全的，切页面时自己不必判断有没有在跑。 */
    fun stop() {
        active = false
        sensorManager.unregisterListener(this)
        // 排队中的后续手势也要清掉：已经离开这一页了，不该隔 20ms 再补一刀。
        handler.removeCallbacksAndMessages(null)
        gestures = emptyList()
    }

    /**
     * 触发后的休息（用户口径：**仅限本轮**）。
     *
     * 正数：注销监听、歇够 [restMs] 再由 [resume] 接回来 —— 休息期一个采样点都不收，最省电；
     * [SensorSettings.REST_STOP_MS]：彻底停下，直到换页 / 改动设置重新 [start]。
     *
     * 排队中的后续手势（「按顺序执行」）不清：它们属于刚刚这一轮，本来就该发完。
     */
    private fun rest() {
        // 0 = 不休息：什么都不做，监听保持注册、照常按检测间隔走。
        if (restMs == SensorSettings.MIN_REST_MS) return

        sensorManager.unregisterListener(this)
        lastSampleMs = -1L
        handler.removeCallbacks(resume)
        if (restMs == SensorSettings.REST_STOP_MS) {
            active = false
        } else if (restMs > 0) {
            handler.postDelayed(resume, restMs.toLong())
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 没到下一个检测间隔就先不采（框架给的速率可能比设置快很多）。
        val due = dueSampleTimeMs(SystemClock.elapsedRealtime(), lastSampleMs, intervalMs)
            ?: return
        lastSampleMs = due

        val values = event.values
        if (!primed) {
            // 第一个点直接作为滤波初值，免得从 0 慢慢爬上来。
            filtered[0] = values[0]
            filtered[1] = values[1]
            filtered[2] = values[2]
            primed = true
        } else {
            filtered[0] = smoothTilt(filtered[0], values[0])
            filtered[1] = smoothTilt(filtered[1], values[1])
            filtered[2] = smoothTilt(filtered[2], values[2])
        }

        val roll = tiltRoll(filtered[0], filtered[2])
        val pitch = tiltPitch(filtered[1], filtered[2])

        // 当前角度落在谁的范围里就执行谁；不去重，下一个检测间隔还可以再执行一次。
        // 命中多条时：默认只跑第一个；打开多手势后按设置里的方式全跑或按顺序轮着跑。
        val matched = gestures.filter { it.matches(roll, pitch) }
        when (mode) {
            null -> matched.firstOrNull()?.let { onTrigger?.invoke(it) }

            MultiGestureMode.Simultaneous -> matched.forEach { onTrigger?.invoke(it) }

            // 逐条执行、相邻两条隔 20ms：第一条当场走，其余的排到主线程队列里。
            // 微间隔是用户口径（动作都是瞬时的，全塞在同一瞬间就分不出先后）。
            MultiGestureMode.Sequential -> matched.forEachIndexed { index, gesture ->
                if (index == 0) {
                    onTrigger?.invoke(gesture)
                } else {
                    handler.postDelayed(
                        { onTrigger?.invoke(gesture) },
                        SensorSettings.MULTI_SEQUENCE_GAP_MS * index,
                    )
                }
            }
        }

        // 这一轮真的触发了手势，才进休息（没触发就继续按设置的检测间隔走）。
        if (matched.isNotEmpty()) rest()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * 录制用的采样器：只管把点攒下来，**时限与提前结束由界面控制**。
 *
 * 最多攒 [SensorSettings.MAX_SAMPLES] 个点，攒满就不再收；整理成角度范围是
 * [summarizeTilt] 的事，这里不掺和。
 */
class TiltRecorder(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val samples = ArrayList<TiltSample>(SensorSettings.MAX_SAMPLES)
    private val filtered = FloatArray(3)
    private var primed = false

    /** 每收下一个点回调一次（带上当前总点数），让界面显示进度。 */
    var onSample: ((Int) -> Unit)? = null

    fun start(intervalMs: Int) {
        val sensor = accelerometer ?: return
        samples.clear()
        primed = false
        sensorManager.registerListener(this, sensor, intervalMs * 1000)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** 把当前攒下的点整理成角度范围；一个点都没有时返回 null。 */
    fun result(): TiltRange? = summarizeTilt(samples)

    override fun onSensorChanged(event: SensorEvent) {
        if (samples.size >= SensorSettings.MAX_SAMPLES) return

        val values = event.values
        if (!primed) {
            filtered[0] = values[0]
            filtered[1] = values[1]
            filtered[2] = values[2]
            primed = true
        } else {
            filtered[0] = smoothTilt(filtered[0], values[0])
            filtered[1] = smoothTilt(filtered[1], values[1])
            filtered[2] = smoothTilt(filtered[2], values[2])
        }

        samples.add(TiltSample(filtered[0], filtered[1], filtered[2]))
        onSample?.invoke(samples.size)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
