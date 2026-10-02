package com.xialiangok.xlreader.data.sensor

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 体感手势设置的持久化。
 *
 * 手势是一串结构化数据（四个角度 + 名字 + 动作），SharedPreferences 只能存原语，
 * 所以整体序列化成一段 JSON 存在一个键里 —— 不引入任何第三方序列化库，
 * `org.json` 是 Android 自带的。
 *
 * 读的时候凡是损坏、越界、认不出的，一律退回默认值或夹回合法范围：
 * 一份坏掉的设置不该让应用打不开。
 */
class SensorStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): SensorSettings {
        val raw = prefs.getString(KEY_SENSOR, null) ?: return SensorSettings()
        return runCatching { decode(raw) }.getOrElse { SensorSettings() }
    }

    fun save(value: SensorSettings) {
        prefs.edit { putString(KEY_SENSOR, encode(value)) }
    }

    private fun encode(value: SensorSettings): String {
        val gestures = JSONArray()
        for (gesture in value.gestures) {
            gestures.put(
                JSONObject().apply {
                    put("id", gesture.id)
                    put("name", gesture.name)
                    put("action", gesture.action.name)
                    put("minRoll", gesture.minRoll.toDouble())
                    put("maxRoll", gesture.maxRoll.toDouble())
                    put("minPitch", gesture.minPitch.toDouble())
                    put("maxPitch", gesture.maxPitch.toDouble())
                    put("enabled", gesture.enabled)
                    put("speed", gesture.speed)
                },
            )
        }

        val pages = JSONArray()
        for (page in value.pages) pages.put(page.name)

        return JSONObject().apply {
            put("enabled", value.enabled)
            put("intervalMs", value.intervalMs)
            put("pages", pages)
            put("gestures", gestures)
        }.toString()
    }

    private fun decode(raw: String): SensorSettings {
        val json = JSONObject(raw)

        val pagesJson = json.optJSONArray("pages") ?: JSONArray()
        val pages = buildSet {
            for (index in 0 until pagesJson.length()) {
                // 认不出的页面名直接跳过（老版本存下来的、或手改坏的）。
                runCatching { GesturePage.valueOf(pagesJson.getString(index)) }.getOrNull()?.let(::add)
            }
        }

        val gesturesJson = json.optJSONArray("gestures") ?: JSONArray()
        val gestures = buildList {
            for (index in 0 until gesturesJson.length()) {
                val item = gesturesJson.optJSONObject(index) ?: continue
                val action = runCatching { GestureAction.valueOf(item.optString("action")) }
                    .getOrDefault(GestureAction.Tap)
                add(
                    SensorGesture(
                        id = item.optString("id").ifEmpty { "gesture-$index" },
                        name = item.optString("name").ifEmpty { "手势 ${index + 1}" },
                        action = action,
                        minRoll = item.optDouble("minRoll", 0.0).toFloat(),
                        maxRoll = item.optDouble("maxRoll", 0.0).toFloat(),
                        minPitch = item.optDouble("minPitch", 0.0).toFloat(),
                        maxPitch = item.optDouble("maxPitch", 0.0).toFloat(),
                        enabled = item.optBoolean("enabled", true),
                        speed = item.optInt("speed", SensorGesture.DEFAULT_SPEED)
                            .coerceIn(SensorGesture.MIN_SPEED, SensorGesture.MAX_SPEED),
                    ),
                )
            }
        }

        return SensorSettings(
            enabled = json.optBoolean("enabled", true),
            intervalMs = json.optInt("intervalMs", SensorSettings.DEFAULT_INTERVAL_MS)
                .coerceIn(SensorSettings.MIN_INTERVAL_MS, SensorSettings.MAX_INTERVAL_MS),
            // 老设置里没有 pages 这个键时，给「全部页面」这份默认值，而不是空集（空集等于手势全废）。
            pages = if (json.has("pages")) pages else GesturePage.entries.toSet(),
            gestures = gestures,
        )
    }

    private companion object {
        const val PREFS_NAME = "xlreader_sensor"
        const val KEY_SENSOR = "sensor_settings"
    }
}
