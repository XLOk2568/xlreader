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
            put("restMs", value.restMs)
            put("pages", pages)
            // 记下这份页面集合是哪个版本写的: 以后新增页面时据此只给老设置补上新增的那几页,
            // 用户已经手动取消勾选的页面不会被反复加回来.
            put("pagesVersion", PAGES_VERSION)
            put("gestures", gestures)
            put("multiGesture", value.multiGesture)
            put("multiMode", value.multiMode.name)
            put("autoClick", value.autoClick)
            put("autoClickDelaySeconds", value.autoClickDelaySeconds)
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
            // 老设置里没有 restMs：默认「不休息」，即一直以来的行为。
            // -1（停止后续检测）这一挡要原样保留，所以只夹非负的那部分。
            restMs = json.optInt("restMs", SensorSettings.DEFAULT_REST_MS).let { rest ->
                if (rest == SensorSettings.REST_STOP_MS) {
                    rest
                } else {
                    rest.coerceIn(SensorSettings.MIN_REST_MS, SensorSettings.MAX_REST_MS)
                }
            },
            // 老设置里没有 pages 这个键时，给「全部页面」这份默认值，而不是空集（空集等于手势全废）。
            // 有 pages 但版本更老时补上那一版新增的几页: 用户之前没得选, 不该默认把它们关着.
            pages = when {
                !json.has("pages") -> GesturePage.entries.toSet()
                json.optInt("pagesVersion", 0) < PAGES_VERSION -> pages + PAGES_ADDED_IN_V2
                else -> pages
            },
            gestures = gestures,
            // 老设置里没有这两个键：默认「只跑第一个命中的」，即一直以来的行为。
            multiGesture = json.optBoolean("multiGesture", false),
            multiMode = runCatching { MultiGestureMode.valueOf(json.optString("multiMode")) }
                .getOrDefault(MultiGestureMode.Simultaneous),
            // 老设置里没有自动单击: 默认关, 也就是一直以来的行为.
            autoClick = json.optBoolean("autoClick", false),
            autoClickDelaySeconds = json.optInt(
                "autoClickDelaySeconds",
                SensorSettings.DEFAULT_AUTO_CLICK_SECONDS,
            ).coerceIn(
                SensorSettings.MIN_AUTO_CLICK_SECONDS,
                SensorSettings.MAX_AUTO_CLICK_SECONDS,
            ),
        )
    }

    private companion object {
        const val PREFS_NAME = "xlreader_sensor"
        const val KEY_SENSOR = "sensor_settings"

        /** 页面集合的迁移版本: 2 起支持设置 / 关于 / 解压询问 / 局域网文件管理. */
        const val PAGES_VERSION = 2

        /** 版本 2 新增的页面: 老设置里没有它们, 读的时候补上 (之后用户可以自己关). */
        val PAGES_ADDED_IN_V2 = setOf(
            GesturePage.Settings,
            GesturePage.About,
            GesturePage.CachePrompt,
            GesturePage.LanFileManager,
        )
    }
}
