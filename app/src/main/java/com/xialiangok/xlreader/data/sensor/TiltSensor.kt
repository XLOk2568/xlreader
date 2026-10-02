package com.xialiangok.xlreader.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

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
 * 同一姿势只触发一次：命中后先记进 [fired]，要等角度离开范围才重新武装，
 * 否则一直保持那个姿势就会每 200ms 触发一下。
 */
class TiltGestureDetector(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** 已经触发过、还没离开范围的手势 id。 */
    private val fired = mutableSetOf<String>()
    private val filtered = FloatArray(3)
    private var primed = false

    private var gestures: List<SensorGesture> = emptyList()

    /** 命中手势时回调（传感器事件在主线程派发）。 */
    var onTrigger: ((SensorGesture) -> Unit)? = null

    /** 手表上有没有加速度计。没有就不必折腾了。 */
    val available: Boolean get() = accelerometer != null

    /** 需要检测的手势列表（已经过滤掉被关掉的），以及采样间隔。 */
    fun start(gestures: List<SensorGesture>, intervalMs: Int) {
        val sensor = accelerometer ?: return
        this.gestures = gestures
        fired.clear()
        primed = false
        // registerListener 的第三个参数是微秒。
        sensorManager.registerListener(this, sensor, intervalMs * 1000)
    }

    /** 注销监听。重复调用是安全的，切页面时不必自己判断有没有在跑。 */
    fun stop() {
        sensorManager.unregisterListener(this)
        gestures = emptyList()
        fired.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
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

        // 同时落进多个范围时只认列表里最靠前的那个，一次姿势只出一个动作。
        val matched = gestures.firstOrNull { it.matches(roll, pitch) }
        if (matched == null) {
            fired.clear()
            return
        }
        if (fired.add(matched.id)) onTrigger?.invoke(matched)
        fired.retainAll(setOf(matched.id))
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
