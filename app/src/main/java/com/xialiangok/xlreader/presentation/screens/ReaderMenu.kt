package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.blueOf
import com.xialiangok.xlreader.data.formatScreenBrightness
import com.xialiangok.xlreader.data.greenOf
import com.xialiangok.xlreader.data.packRgb
import com.xialiangok.xlreader.data.redOf
import com.xialiangok.xlreader.data.spacingLabel
import com.xialiangok.xlreader.data.stepChannel
import com.xialiangok.xlreader.data.stepFontSize
import com.xialiangok.xlreader.data.stepSpacingLevel
import com.xialiangok.xlreader.presentation.theme.ReaderButtonContainer
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** 菜单底色：完全不透明（`0xFF`），不透过下面的正文，也不做任何淡入淡出。 */
private val MenuBackground = Color(0xFF141414)

/** 屏幕亮度可选值；-1 表示跟随系统。 */
private val SCREEN_BRIGHTNESS_STEPS = listOf(
    ReaderPreferences.BRIGHTNESS_SYSTEM,
    0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f,
)

/** 文字亮度可选值，从最暗到最亮。 */
private val TEXT_BRIGHTNESS_STEPS: List<Float> = buildList {
    var value = ReaderPreferences.MIN_TEXT_BRIGHTNESS
    while (value <= ReaderPreferences.MAX_TEXT_BRIGHTNESS + 0.001f) {
        add((value * 100).toInt() / 100f)
        value += ReaderPreferences.TEXT_BRIGHTNESS_STEP
    }
}

/**
 * 阅读界面的快捷设置菜单。
 *
 * 单击正文弹出，再次单击（面板空白处）或点「完成」关闭。
 * 这里改的每一项都直接写回 [ReaderPreferences]，也就是设置页看到的那份数据，
 * 所以「等同于设置页面效果」。
 *
 * 面板铺满整屏，**空白处的单击等于关闭菜单**；按钮、开关、步进器会消费掉自己的点击，
 * 所以不会误关。
 *
 * @param onBackToCatalog 返回章节目录，和正文底部的「返回目录」走同一条路
 *   （因此进度会在同一条路上落盘、常亮与屏幕亮度也会随正文页一起还原）。
 *   放在这里是为了不用滚到本章末尾才能离开这一章。
 * @param onBackToFileList 返回文件列表，和正文底部的「返回文件列表」走同一条路。
 *   想换一本书时不用说先回目录、再滚到目录页最底下点返回。
 */
@Composable
fun ReaderMenuOverlay(
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onBackToCatalog: () -> Unit,
    onBackToFileList: () -> Unit,
    onDismiss: () -> Unit,
) {
    val update = rememberUpdatedState(onPreferencesChange)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val time2 = remember {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
            Text(

                text = "$time2\n阅读设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("关闭菜单")
            }
            // 紧挨着「关闭菜单」：想离开这一章时，不必先关掉菜单再滚到本章末尾去找那个按钮。
            Button(
                onClick = onBackToCatalog,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("返回目录")
            }
            // 紧挨着「返回目录」：想换一本书时，一步就能回到文件列表。
            Button(
                onClick = onBackToFileList,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("返回文件列表")
            }
            Spacer(Modifier.height(4.dp))
            // ---- 字号：和亮度、RGB 一样用「− / ＋」一次走一磅，直接显示磅值 ----
            StepRow(
                label = "字号",
                value = preferences.fontSize.toString(),
                onMinus = { update.value(preferences.copy(fontSize = stepFontSize(preferences.fontSize, -1))) },
                onPlus = { update.value(preferences.copy(fontSize = stepFontSize(preferences.fontSize, +1))) },
            )
            // ---- 段间距：和字号一样是直接的数字（单位 dp），「− / ＋」一次走 1 dp ----
            StepRow(
                label = "段间距",
                value = spacingLabel(preferences.spacingLevel),
                onMinus = { update.value(preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, -1))) },
                onPlus = { update.value(preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, +1))) },
            )

            // ---- 常亮 ----
            SwitchButton(
                checked = preferences.keepScreenOn,
                onCheckedChange = { update.value(preferences.copy(keepScreenOn = it)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("阅读时常亮", style = MaterialTheme.typography.bodySmall)
            }

            SectionLabel("屏幕亮度")
            StepRow(
                label = "亮度",
                value = formatScreenBrightness(preferences.screenBrightness),
                onMinus = {
                    update.value(
                        preferences.copy(
                            screenBrightness = stepInList(
                                SCREEN_BRIGHTNESS_STEPS,
                                preferences.screenBrightness,
                                -1,
                            ),
                        ),
                    )
                },
                onPlus = {
                    update.value(
                        preferences.copy(
                            screenBrightness = stepInList(
                                SCREEN_BRIGHTNESS_STEPS,
                                preferences.screenBrightness,
                                +1,
                            ),
                        ),
                    )
                },
            )

            SectionLabel("文字亮度")
            StepRow(
                label = "亮度",
                value = "${(preferences.textBrightness * 100).toInt()}%",
                onMinus = {
                    update.value(
                        preferences.copy(
                            textBrightness = stepInList(
                                TEXT_BRIGHTNESS_STEPS,
                                preferences.textBrightness,
                                -1,
                            ),
                        ),
                    )
                },
                onPlus = {
                    update.value(
                        preferences.copy(
                            textBrightness = stepInList(
                                TEXT_BRIGHTNESS_STEPS,
                                preferences.textBrightness,
                                +1,
                            ),
                        ),
                    )
                },
            )

            SectionLabel("文字颜色（RGB，每次 1 档）")
            ColorPreview(preferences)
            Spacer(Modifier.height(2.dp))
            ChannelRow(
                channel = "R",
                value = redOf(preferences.textColor),
                onChange = { update.value(preferences.copy(textColor = packRgb(it, greenOf(preferences.textColor), blueOf(preferences.textColor)))) },
            )
            Spacer(Modifier.height(2.dp))
            ChannelRow(
                channel = "G",
                value = greenOf(preferences.textColor),
                onChange = { update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), it, blueOf(preferences.textColor)))) },
            )
            Spacer(Modifier.height(2.dp))
            ChannelRow(
                channel = "B",
                value = blueOf(preferences.textColor),
                onChange = { update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), greenOf(preferences.textColor), it))) },
            )
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = {
                    update.value(
                        preferences.copy(
                            textColor = ReaderPreferences.DEFAULT_TEXT_COLOR,
                            textBrightness = ReaderPreferences.MAX_TEXT_BRIGHTNESS,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("文字颜色恢复默认")
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyExtraSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

@Composable
private fun ColorPreview(preferences: ReaderPreferences) {
    val rgb = preferences.effectiveTextColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF000000.toInt() or rgb)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            // 直接给出十六进制，方便用户对着别的工具调
            text = "#%02X%02X%02X".format(redOf(rgb), greenOf(rgb), blueOf(rgb)),
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** R / G / B 单独一行，每次走 1 档。 */
@Composable
private fun ChannelRow(
    channel: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    StepRow(
        label = channel,
        value = value.toString(),
        onMinus = { onChange(stepChannel(value, -1)) },
        onPlus = { onChange(stepChannel(value, +1)) },
    )
}

/** 一行「标签 + − 值 ＋」的步进控件；设置页的字号也复用它。 */
@Composable
internal fun StepRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", onMinus)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(58.dp),
        )
        StepButton("＋", onPlus)
    }
}

/**
 * 一次走一档的步进按钮。
 *
 * 手感与同屏的 [Button] 对齐：**抬手时**、且这一按没有被滑动带走，才算一次点击。
 * 原来是按下就立刻走一档，手指其实想滑列表、只是落在了按钮上时也会被改掉一个值，
 * 所以误触很明显；现在滑动（事件被列表滚动消费，[waitForUpOrCancellation] 返回 null）
 * 会让整次手势作废，不会既滑动了列表又改了值。
 *
 * 连发仍然保留（步长 1，从 0 调到 255 要点 255 次，见用户要求「精准、不含糊」）：
 * 按住不动超过 500ms 后每 60ms 走一档；比 [HOLD_DELAY_MS] 短的按压按一次点击算，
 * 抬手时补走那一档，两者不会重复。
 */
@Composable
private fun StepButton(label: String, onStep: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val stepState = rememberUpdatedState(onStep)

    // pressed 为 true 时开始连发；抬手（pressed 变 false）会让这个 effect 被取消。
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(HOLD_DELAY_MS.milliseconds)
        while (true) {
            stepState.value()
            delay(REPEAT_INTERVAL_MS.milliseconds)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ReaderButtonContainer)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 消费掉这次手势，父级的「空白处点击关闭菜单」就不会被触发。
                    down.consume()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    pressed = false
                    // 抬手才算数；按得够久说明连发已经接管，不再补这一档。
                    if (up != null && up.uptimeMillis - down.uptimeMillis < HOLD_DELAY_MS) {
                        stepState.value()
                    }
                }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val HOLD_DELAY_MS = 500L
private const val REPEAT_INTERVAL_MS = 60L

/** 在离散档位表里走一步；找不到当前值时就近取。 */
private fun stepInList(steps: List<Float>, current: Float, delta: Int): Float {
    if (steps.isEmpty()) return current
    val index = steps.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
        .takeIf { it >= 0 }
        ?: steps.indices.minByOrNull { kotlin.math.abs(steps[it] - current) }
        ?: 0
    return steps[(index + delta).coerceIn(0, steps.lastIndex)]
}
