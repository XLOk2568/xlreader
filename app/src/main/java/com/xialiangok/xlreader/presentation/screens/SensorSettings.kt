package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.sensor.GestureAction
import com.xialiangok.xlreader.data.sensor.GesturePage
import com.xialiangok.xlreader.data.sensor.MultiGestureMode
import com.xialiangok.xlreader.data.sensor.SensorGesture
import com.xialiangok.xlreader.data.sensor.SensorSettings
import com.xialiangok.xlreader.data.sensor.TiltRange
import com.xialiangok.xlreader.data.sensor.TiltRecorder
import com.xialiangok.xlreader.data.sensor.formatAngle
import com.xialiangok.xlreader.data.sensor.stepAngleMax
import com.xialiangok.xlreader.data.sensor.stepAngleMin
import com.xialiangok.xlreader.data.sensor.stepIntervalMs
import com.xialiangok.xlreader.data.sensor.stepSpeed
import com.xialiangok.xlreader.presentation.theme.ReaderSurface
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * 体感手势设置页。
 *
 * 这份设置与阅读排版无关，单独一份 [SensorSettings]（存在 `xlreader_sensor` 里）。
 * 页面只负责显示与改动，真正的检测任务由根组件按「总开关 + 启用页面 + 手势开关」
 * 三个条件决定要不要注册传感器。
 *
 * @param onAddGesture   去新增一个手势（先倒计时再录制）。
 * @param onOpenGesture  打开某个手势的详情（改名 / 动作 / 速度 / 重录 / 删除）。
 * @param onOpenPages    选择在哪些页面启用手势。
 */
@Composable
fun SensorSettingsScreen(
    settings: SensorSettings,
    onChange: (SensorSettings) -> Unit,
    onAddGesture: () -> Unit,
    onOpenGesture: (String) -> Unit,
    onOpenPages: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item { ListHeader { Text("体感手势", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            // 总开关。关掉它不是「手势不生效」那么简单：根组件会直接注销传感器，
            // 一个采样点都不再收。
            SwitchButton(
                checked = settings.enabled,
                onCheckedChange = { onChange(settings.copy(enabled = it)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("启用手势", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            // 关掉（默认）时，一次采样里同时命中多条只跑列表里最靠前的那条；
            // 打开后由下面两个选项决定怎么跑。
            SwitchButton(
                checked = settings.multiGesture,
                onCheckedChange = { onChange(settings.copy(multiGesture = it)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("允许多个手势一起执行", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        if (settings.multiGesture) {
            MultiGestureMode.entries.forEach { mode ->
                item {
                    val selected = settings.multiMode == mode
                    Card(
                        onClick = {
                            if (!selected) onChange(settings.copy(multiMode = mode))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (selected) "✓ ${mode.label}" else mode.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            StepRow(
                label = "检测间隔",
                value = "${settings.intervalMs} ms",
                onMinus = {
                    onChange(
                        settings.copy(
                            intervalMs = stepIntervalMs(
                                settings.intervalMs,
                                -SensorSettings.INTERVAL_STEP_MS,
                            ),
                        ),
                    )
                },
                onPlus = {
                    onChange(
                        settings.copy(
                            intervalMs = stepIntervalMs(
                                settings.intervalMs,
                                SensorSettings.INTERVAL_STEP_MS,
                            ),
                        ),
                    )
                },
            )
        }

        item {
            Button(
                onClick = onOpenPages,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text(text = "启用页面（${settings.pages.size}/${GesturePage.entries.size}）", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Button(
                onClick = onAddGesture,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("新增手势", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Text(
                text = RECORD_HINT,
                style = MaterialTheme.typography.bodyExtraSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (settings.gestures.isNotEmpty()) {
            item { ListSubHeader { Text("已录制的手势", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        }

        settings.gestures.forEach { gesture ->
            item {
                // 点进详情：改名、换动作、改速度、重录、删除。
                Card(onClick = { onOpenGesture(gesture.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = gesture.name,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (gesture.enabled) {
                            "${gesture.action.label} · 已启用"
                        } else {
                            "${gesture.action.label} · 已停用"
                        },
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = if (gesture.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                // 单个手势的开关：关掉它这一条就不再参与检测（全关掉等于没有手势可检测，
                // 根组件随即注销传感器）。
                SwitchButton(
                    checked = gesture.enabled,
                    onCheckedChange = { checked ->
                        onChange(
                            settings.copy(
                                gestures = settings.gestures.map {
                                    if (it.id == gesture.id) it.copy(enabled = checked) else it
                                },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("启用「${gesture.name}」", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (settings.gestures.isEmpty()) {
            item {
                Text(
                    text = "还没有手势。点「新增手势」，摆好姿势再点一下屏幕就能录下来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape=RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("完成", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/** 「在哪些页面启用手势」。 */
@Composable
fun SensorPagesScreen(
    settings: SensorSettings,
    onChange: (SensorSettings) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item { ListHeader { Text("启用页面", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }

        item {
            Text(
                text = "只有勾上的页面才会开启手势检测任务；进别的页面会立刻停掉，" +
                    "一个采样点都不收。",
                style = MaterialTheme.typography.bodyExtraSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GesturePage.entries.forEach { page ->
            item {
                SwitchButton(
                    checked = page in settings.pages,
                    onCheckedChange = { checked ->
                        onChange(
                            settings.copy(
                                pages = if (checked) settings.pages + page else settings.pages - page,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(page.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape=RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("完成", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/**
 * 单个手势的详情：改名、换动作、（翻页类）改速度、重新录制、删除。
 *
 * @param onChange 改动后的这一条手势整条回写。
 */
@Composable
fun SensorGestureDetailScreen(
    gesture: SensorGesture,
    onChange: (SensorGesture) -> Unit,
    onRerecord: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item { ListHeader { Text("手势设置", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }

        item { ListSubHeader { Text("名称", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            // Wear M3 的 Card 只有「可点击」那一种重载，而这里只是输入框的外壳，
            // 包成卡片会跟输入框抢点击，所以用一个同色的圆角容器。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReaderSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // 手表上打字不方便，但名字只有一两个词，直接给一个单行输入框最省事。
                BasicTextField(
                    value = gesture.name,
                    onValueChange = { onChange(gesture.copy(name = it)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { ListSubHeader { Text("动作", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        GestureAction.entries.forEach { action ->
            item {
                val selected = gesture.action == action
                Card(
                    onClick = {
                        if (!selected) onChange(gesture.copy(action = action))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (selected) "✓ ${action.label}" else action.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (gesture.action.hasSpeed) {
            item {
                // 速度就是「一次触发滚动多少条目」：正文里一条是一段，列表里一条是一项。
                StepRow(
                    label = "翻页速度（条目）",
                    value = gesture.speed.toString(),
                    onMinus = { onChange(gesture.copy(speed = stepSpeed(gesture.speed, -1))) },
                    onPlus = { onChange(gesture.copy(speed = stepSpeed(gesture.speed, +1))) },
                )
            }
        }

        item { ListSubHeader { Text("角度范围（一次 1°）", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                // 判定口径讲清楚：当前角度落进这组数字就执行，判定时两侧还会再放宽一点容差，
                // 所以「填 ±5°」并不等于「必须停在 5° 以内」。
                text = "当前角度落进这个范围就执行动作（判定时两侧还会各放宽一点容差）。" +
                    "录制只是给一组初值，这里可以自己调。",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StepRow(
                label = "横滚最小",
                value = formatAngle(gesture.minRoll),
                onMinus = {
                    onChange(
                        gesture.copy(
                            minRoll = stepAngleMin(gesture.minRoll, gesture.maxRoll, -ANGLE_STEP),
                        ),
                    )
                },
                onPlus = { onChange(gesture.copy(minRoll = stepAngleMin(gesture.minRoll, gesture.maxRoll))) },
            )
        }
        item {
            StepRow(
                label = "横滚最大",
                value = formatAngle(gesture.maxRoll),
                onMinus = {
                    onChange(
                        gesture.copy(
                            maxRoll = stepAngleMax(gesture.maxRoll, gesture.minRoll, -ANGLE_STEP),
                        ),
                    )
                },
                onPlus = { onChange(gesture.copy(maxRoll = stepAngleMax(gesture.maxRoll, gesture.minRoll))) },
            )
        }
        item {
            StepRow(
                label = "俯仰最小",
                value = formatAngle(gesture.minPitch),
                onMinus = {
                    onChange(
                        gesture.copy(
                            minPitch = stepAngleMin(gesture.minPitch, gesture.maxPitch, -ANGLE_STEP),
                        ),
                    )
                },
                onPlus = { onChange(gesture.copy(minPitch = stepAngleMin(gesture.minPitch, gesture.maxPitch))) },
            )
        }
        item {
            StepRow(
                label = "俯仰最大",
                value = formatAngle(gesture.maxPitch),
                onMinus = {
                    onChange(
                        gesture.copy(
                            maxPitch = stepAngleMax(gesture.maxPitch, gesture.minPitch, -ANGLE_STEP),
                        ),
                    )
                },
                onPlus = { onChange(gesture.copy(maxPitch = stepAngleMax(gesture.maxPitch, gesture.minPitch))) },
            )
        }
        item {
            Button(
                onClick = onRerecord,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("重新录制", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("删除这个手势", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("完成", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/** 录制过程的三个阶段。 */
private enum class RecordPhase { Countdown, Recording, Failed }

/**
 * 录制一个手势：先倒计时 [SensorSettings.COUNTDOWN_SECONDS] 秒，再在
 * [SensorSettings.RECORD_SECONDS] 秒内最多采 [SensorSettings.MAX_SAMPLES] 个点；
 * **期间单击屏幕立刻停止**，两种情况都马上整理出角度范围并保存。
 *
 * @param intervalMs  采样间隔，与检测间隔共用同一份设置。
 * @param onRecorded  录制成功（有数据）时回调整理好的角度范围。
 * @param onCancel    直接放弃录制。
 */
@Composable
fun SensorGestureRecordScreen(
    intervalMs: Int,
    onRecorded: (TiltRange) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember(context) { TiltRecorder(context) }
    val done = rememberUpdatedState(onRecorded)

    // 重试就是把这个计数 +1，重新跑一遍倒计时。
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(RecordPhase.Countdown) }
    var countdown by remember { mutableIntStateOf(SensorSettings.COUNTDOWN_SECONDS) }
    var secondsLeft by remember { mutableIntStateOf(SensorSettings.RECORD_SECONDS) }
    var samples by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        recorder.stop()
        val range = recorder.result()
        if (range == null) {
            // 一个点都没采到（手表没有加速度计、或按键太早），不保存空手势。
            phase = RecordPhase.Failed
        } else {
            done.value(range)
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.stop() }
    }

    LaunchedEffect(attempt) {
        phase = RecordPhase.Countdown
        finished = false
        samples = 0
        secondsLeft = SensorSettings.RECORD_SECONDS

        for (tick in SensorSettings.COUNTDOWN_SECONDS downTo 1) {
            countdown = tick
            delay(1.seconds)
        }

        // 倒计时结束才开始采样，免得用户还在摆姿势就把前面的乱数据录进去。
        phase = RecordPhase.Recording
        recorder.onSample = { samples = it }
        recorder.start(intervalMs)

        for (tick in SensorSettings.RECORD_SECONDS downTo 1) {
            secondsLeft = tick
            delay(1.seconds)
        }

        // 10 秒到点，自动结束并保存。
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 单击屏幕立刻停止记录并保存（子级按钮会消费自己的点击，不会误触发）。
            .pointerInput(attempt) {
                detectTapGestures { finish() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            when (phase) {
                RecordPhase.Countdown -> {
                    Text(
                        text = "准备",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$countdown",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "马上把表摆成要用的姿势\n（保持不动）",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RecordPhase.Recording -> {
                    Text(
                        text = "正在记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "剩余 $secondsLeft 秒\n已采 $samples / ${SensorSettings.MAX_SAMPLES} 个点",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "单击屏幕立即结束并保存",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RecordPhase.Failed -> {
                    Text(
                        text = "没有采到数据",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "手表可能没有加速度计，或者刚开始就结束了。",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (phase == RecordPhase.Failed) {
                Button(
                    onClick = { attempt += 1 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("重试", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(
                onClick = {
                    recorder.stop()
                    onCancel()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("取消", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 读不到加速度计时的引导页 —— 排版与「需要文件访问权限」那一页（`PermissionScreen`）一致。
 *
 * 加速度计严格来说**不需要运行时权限**（Android 只对心率这类身体传感器要 `BODY_SENSORS`），
 * 所以这一页挡住的其实是两种情况：设备没有这个传感器，或系统 / ROM 把它禁用了。
 * 两种都只能去系统设置里确认，于是就给同一套「去系统设置 + 已授权，重新检查」。
 */
@Composable
fun SensorPermissionScreen(
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item { ListHeader { Text("读不到加速度计", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = "体感手势要靠加速度计算倾斜角度，现在这台设备上没读到它。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "可能是设备本身没有这个传感器，也可能是系统 / 应用权限里把它关掉了。" +
                    "应用不联网，传感器数据只在手表本地做判断，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Spacer(Modifier.height(6.dp)) }
        item {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("去系统设置查看", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = onRecheck,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("已授权，重新检查", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = readerButtonColors(),
            ) {
                Text("返回设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Text(
                text = "检查路径：设置 → 应用 → XLreader → 权限",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/** 手改角度一次走 1°；往下走的那一侧用它的相反数。 */
private const val ANGLE_STEP = SensorGesture.ANGLE_STEP_DEG

private const val RECORD_HINT =
    "提示：新增手势时先倒计时 3 秒，再在 10 秒内把表摆成要用的姿势；" +
        "期间单击屏幕可提前结束。只保留这段时间里横滚/俯仰的最大与最小角度，\n\n" +
        "检测间隔对所有手势通用。"
