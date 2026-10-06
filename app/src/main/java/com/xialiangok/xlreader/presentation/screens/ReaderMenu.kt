package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
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
import com.xialiangok.xlreader.data.stepAlpha
import com.xialiangok.xlreader.data.stepChannel
import com.xialiangok.xlreader.data.stepFontSize
import com.xialiangok.xlreader.data.stepSpacingLevel
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.scrollByDp
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import com.xialiangok.xlreader.presentation.theme.readerButtonContainerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** 当前选中控件的边框色: 用户指定的 rgb(255, 0, 255). */
private val SelectionBorderColor = Color(0xFFFF00FF)

/** 边框粗细: 标准 52dp 按钮与 36dp 圆形步进按钮上都看得清, 又不至于盖住内容. */
private val SelectionBorderWidth = 2.dp

/**
 * 菜单里每一行的 key.
 *
 * 用字符串当 key 而不是下标: 手势翻页是按像素 (dp) 滚的, 光标要知道自己在哪个控件上,
 * 下标会跟着列表内容 (分组展开/收起) 变, 名字不会.
 */
private const val ROW_TIME = "menu.time"
private const val ROW_CLOSE = "menu.close"
private const val ROW_CATALOG = "menu.catalog"
private const val ROW_FILE_LIST = "menu.fileList"
private const val ROW_SETTINGS = "menu.settings"
private const val ROW_GROUP_DISPLAY = "屏幕设置"
private const val ROW_GROUP_FONT = "阅读界面设置"
private const val ROW_GROUP_TEXT = "短距和字号"
private const val ROW_KEEP_ON = "menu.keepScreenOn"
private const val ROW_BRIGHTNESS = "menu.brightness"
private const val ROW_TEXT_BRIGHTNESS = "menu.textBrightness"
private const val ROW_TEXT_RED = "menu.textRed"
private const val ROW_TEXT_GREEN = "menu.textGreen"
private const val ROW_TEXT_BLUE = "menu.textBlue"
private const val ROW_TEXT_ALPHA = "menu.textAlpha"
private const val ROW_RESET_TEXT_COLOR = "menu.resetTextColor"
private const val ROW_FONT_SIZE = "menu.fontSize"
private const val ROW_SPACING = "menu.spacing"

/** 三个可展开的设置分组. */
private enum class MenuGroup(val header: String, val rows: List<String>) {
    /** 亮度与显示: 常亮 + 屏幕亮度. */
    Display(ROW_GROUP_DISPLAY, listOf(ROW_KEEP_ON, ROW_BRIGHTNESS)),

    /** 字体显示: 文字亮度 + 文字颜色 RGB + 透明度 + 恢复默认, 更多颜色在设置页. */
    Font(
        ROW_GROUP_FONT,
        listOf(
            ROW_TEXT_BRIGHTNESS,
            ROW_TEXT_RED,
            ROW_TEXT_GREEN,
            ROW_TEXT_BLUE,
            ROW_TEXT_ALPHA,
            ROW_RESET_TEXT_COLOR,
        ),
    ),

    /** 文本显示: 字号 + 段间距. */
    Text(ROW_GROUP_TEXT, listOf(ROW_FONT_SIZE, ROW_SPACING)),
}

/** 每一行里有几个可点控件; 没登记的按一个算. */
private val ROW_CONTROL_COUNTS = mapOf(
    ROW_BRIGHTNESS to 2,
    ROW_TEXT_BRIGHTNESS to 2,
    ROW_TEXT_RED to 2,
    ROW_TEXT_GREEN to 2,
    ROW_TEXT_BLUE to 2,
    ROW_TEXT_ALPHA to 2,
    ROW_FONT_SIZE to 2,
    ROW_SPACING to 2,
)

private fun controlCountOf(row: String): Int = ROW_CONTROL_COUNTS[row] ?: 1

/** 屏幕亮度可选值; -1 表示跟随系统. */
private val SCREEN_BRIGHTNESS_STEPS = listOf(
    ReaderPreferences.BRIGHTNESS_SYSTEM,
    0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f,
)

/** 文字亮度可选值, 从最暗到最亮. */
private val TEXT_BRIGHTNESS_STEPS: List<Float> = buildList {
    var value = ReaderPreferences.MIN_TEXT_BRIGHTNESS
    while (value <= ReaderPreferences.MAX_TEXT_BRIGHTNESS + 0.001f) {
        add((value * 100).toInt() / 100f)
        value += ReaderPreferences.TEXT_BRIGHTNESS_STEP
    }
}

/** 给当前选中的控件套一圈边框; 没被选中就原样返回. */
private fun Modifier.selectionBorder(selected: Boolean, shape: Shape = RectangleShape): Modifier =
    if (selected) border(SelectionBorderWidth, SelectionBorderColor, shape) else this

/**
 * 屏幕上离正中最近的**可选中**行; 列表还没布局出来时返回 null.
 *
 * 取离正中最近而不是第一个可见行: 翻页手势按像素 (dp) 滚, 滚完之后第一行往往贴着屏幕上沿,
 * 正中那一行才更接近用户眼里的当前控件. 分组标题也能落光标, 不可点的行直接跳过.
 */
private fun centeredSelectableRow(listState: LazyListState, selectable: Set<String>): String? {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    var best: String? = null
    var bestDistance = Int.MAX_VALUE
    for (item in info.visibleItemsInfo) {
        val key = item.key as? String ?: continue
        if (key !in selectable) continue
        val distance = abs(item.offset + item.size / 2 - center)
        if (distance < bestDistance) {
            bestDistance = distance
            best = key
        }
    }
    return best
}

/**
 * 阅读界面的快捷设置菜单.
 *
 * 单击正文弹出, 点面板空白处, 点「关闭菜单」, 体感返回, 系统返回手势都能关闭.
 * 这里改的每一项都直接写回 [ReaderPreferences], 也就是设置页看到的那份数据.
 *
 * 面板铺满整屏, 空白处的单击等于关闭菜单; 按钮, 开关, 步进器会消费掉自己的点击.
 *
 * 设置项收进三个分组按钮 (亮度与显示 / 字体显示 / 文本显示), 默认都不展开,
 * 点一下展开, 再点一下收起; 展开另一组时原来那组自动收起, 避免列表越滚越长.
 * 「字体显示」里有文字亮度, 文字颜色 RGB, 透明度与恢复默认; 更多颜色在设置页.
 *
 * 体感手势 (菜单开着时这一层自己接管, 正文页那一份同时注销, 见 [ChapterScreen]):
 * - 上下翻页 = 按体感设置里的速度 (dp) 滚菜单内容, 和正文页用同一套滚动;
 * - 光标 = 屏幕上离正中最近的可点控件, 套一圈 rgb(255, 0, 255) 的边框;
 *   菜单刚打开时它默认停在「关闭菜单」上;
 * - 内容已经滚到头时, 翻页手势只挪光标 (一次一行), 不滚内容;
 * - 单击 = 执行光标所在的那个控件, 返回 = 关菜单.
 *
 * [gestureEnabled] 为假时上面这一整套都不存在: 不登记任何手势动作, 也不画选中光标.
 */
@Composable
fun ReaderMenuOverlay(
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToFileList: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    gestureEnabled: Boolean,
) {
    val update = rememberUpdatedState(onPreferencesChange)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 当前展开的分组; null = 三组都收起 (默认状态).
    var expandedGroup by remember { mutableStateOf<MenuGroup?>(null) }

    // 光标停在哪一行, 行内第几个控件. 初值就是第一个可选中行, 也就是「关闭菜单」.
    var cursorRow by remember { mutableStateOf(ROW_CLOSE) }
    var cursorIndex by remember { mutableIntStateOf(0) }

    // 可见的可选中行会随分组展开/收起变化, 光标与翻页都只在这份列表里走.
    val selectableRows = buildList {
        add(ROW_CLOSE)
        add(ROW_CATALOG)
        add(ROW_FILE_LIST)
        add(ROW_SETTINGS)
        MenuGroup.entries.forEach { group ->
            add(group.header)
            if (expandedGroup == group) addAll(group.rows)
        }
    }
    val selectableSet = selectableRows.toSet()

    fun cursorAt(row: String): Boolean = gestureEnabled && cursorRow == row

    /** 把光标沿行挪 [delta] 行 (挪到头就停住), 并把行内下标夹回这一行真有的控件数. */
    fun moveSelection(delta: Int) {
        val index = selectableRows.indexOf(cursorRow).coerceAtLeast(0)
        val next = selectableRows[(index + delta).coerceIn(0, selectableRows.lastIndex)]
        cursorRow = next
        cursorIndex = cursorIndex.coerceAtMost(controlCountOf(next) - 1)
    }

    /** 展开/收起一个分组; 光标落在刚被收起的子控件上时退回该组按钮. */
    fun toggleGroup(group: MenuGroup) {
        val target = if (expandedGroup == group) null else group
        expandedGroup = target
        val owner = MenuGroup.entries.firstOrNull { cursorRow in it.rows }
        if (owner != null && owner != target) cursorRow = group.header
    }

    // 内容滚动时, 光标跟着屏幕上离正中最近的那一行走.
    // 第一次布局那一帧不动光标: 菜单刚打开时光标必须停在「关闭菜单」上.
    LaunchedEffect(listState, selectableSet) {
        var settled = false
        snapshotFlow { centeredSelectableRow(listState, selectableSet) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { row ->
                if (!settled) {
                    settled = true
                    return@collect
                }
                cursorRow = row
                cursorIndex = cursorIndex.coerceAtMost(controlCountOf(row) - 1)
            }
    }

    // 每一项的动作. 控件行之外的 lambda 只写在这里一份, UI 上和手势单击共用同一份.
    val onFontSizeDown: () -> Unit = { update.value(preferences.copy(fontSize = stepFontSize(preferences.fontSize, -1))) }
    val onFontSizeUp: () -> Unit = { update.value(preferences.copy(fontSize = stepFontSize(preferences.fontSize, +1))) }
    val onSpacingDown: () -> Unit = { update.value(preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, -1))) }
    val onSpacingUp: () -> Unit = { update.value(preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, +1))) }
    val onKeepScreenOnToggle: () -> Unit = { update.value(preferences.copy(keepScreenOn = !preferences.keepScreenOn)) }
    val onScreenBrightnessDown: () -> Unit = {
        update.value(preferences.copy(screenBrightness = stepInList(SCREEN_BRIGHTNESS_STEPS, preferences.screenBrightness, -1)))
    }
    val onScreenBrightnessUp: () -> Unit = {
        update.value(preferences.copy(screenBrightness = stepInList(SCREEN_BRIGHTNESS_STEPS, preferences.screenBrightness, +1)))
    }
    val onTextBrightnessDown: () -> Unit = {
        update.value(preferences.copy(textBrightness = stepInList(TEXT_BRIGHTNESS_STEPS, preferences.textBrightness, -1)))
    }
    val onTextBrightnessUp: () -> Unit = {
        update.value(preferences.copy(textBrightness = stepInList(TEXT_BRIGHTNESS_STEPS, preferences.textBrightness, +1)))
    }
    val textRed = redOf(preferences.textColor)
    val textGreen = greenOf(preferences.textColor)
    val textBlue = blueOf(preferences.textColor)
    val onTextRedDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(stepChannel(textRed, -1), textGreen, textBlue)))
    }
    val onTextRedUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(stepChannel(textRed, +1), textGreen, textBlue)))
    }
    val onTextGreenDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(textRed, stepChannel(textGreen, -1), textBlue)))
    }
    val onTextGreenUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(textRed, stepChannel(textGreen, +1), textBlue)))
    }
    val onTextBlueDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(textRed, textGreen, stepChannel(textBlue, -1))))
    }
    val onTextBlueUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(textRed, textGreen, stepChannel(textBlue, +1))))
    }
    val onTextAlphaDown: () -> Unit = {
        update.value(preferences.copy(textColorAlpha = stepAlpha(preferences.textColorAlpha, -1)))
    }
    val onTextAlphaUp: () -> Unit = {
        update.value(preferences.copy(textColorAlpha = stepAlpha(preferences.textColorAlpha, +1)))
    }
    val onResetTextColor: () -> Unit = {
        update.value(
            preferences.copy(
                textColor = ReaderPreferences.DEFAULT_TEXT_COLOR,
                textColorAlpha = ReaderPreferences.DEFAULT_COLOR_ALPHA,
                textBrightness = ReaderPreferences.MAX_TEXT_BRIGHTNESS,
            ),
        )
    }

    /** 行 key 到这一行的控件 (顺序就是行内顺序), 体感单击照着 cursorRow / cursorIndex 取. */
    val controlsByRow: Map<String, List<() -> Unit>> = mapOf(
        ROW_CLOSE to listOf(onDismiss),
        ROW_CATALOG to listOf(onOpenCatalog),
        ROW_FILE_LIST to listOf(onBackToFileList),
        ROW_SETTINGS to listOf(onOpenSettings),
        ROW_GROUP_DISPLAY to listOf { toggleGroup(MenuGroup.Display) },
        ROW_GROUP_FONT to listOf { toggleGroup(MenuGroup.Font) },
        ROW_GROUP_TEXT to listOf { toggleGroup(MenuGroup.Text) },
        ROW_KEEP_ON to listOf(onKeepScreenOnToggle),
        ROW_BRIGHTNESS to listOf(onScreenBrightnessDown, onScreenBrightnessUp),
        ROW_TEXT_BRIGHTNESS to listOf(onTextBrightnessDown, onTextBrightnessUp),
        ROW_TEXT_RED to listOf(onTextRedDown, onTextRedUp),
        ROW_TEXT_GREEN to listOf(onTextGreenDown, onTextGreenUp),
        ROW_TEXT_BLUE to listOf(onTextBlueDown, onTextBlueUp),
        ROW_TEXT_ALPHA to listOf(onTextAlphaDown, onTextAlphaUp),
        ROW_RESET_TEXT_COLOR to listOf(onResetTextColor),
        ROW_FONT_SIZE to listOf(onFontSizeDown, onFontSizeUp),
        ROW_SPACING to listOf(onSpacingDown, onSpacingUp),
    )

    // 菜单开着时接管体感手势 (正文页那份同时注销, 见 ChapterScreen).
    // 没启用「正文阅读(菜单)」这一页时干脆不登记: 菜单里没有任何体感动作, 只靠手指用.
    if (gestureEnabled) {
        BindGestureActions(
            onTap = { controlsByRow[cursorRow]?.getOrNull(cursorIndex)?.invoke() },
            onBack = onDismiss,
            onScrollBy = { delta ->
                // 内容还能往这个方向滚就滚内容, 已经滚到头就只挪光标 (一次一行).
                // 还没量出来 (刚打开菜单, 列表高度还是 0) 时一律当还能滚.
                val measured = listState.layoutInfo.totalItemsCount > 0
                val atEnd = measured &&
                    (if (delta > 0) !listState.canScrollForward else !listState.canScrollBackward)
                if (atEnd) moveSelection(if (delta > 0) 1 else -1)
                else scope.launch { scrollByDp(listState, delta, density) }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 底色跟随设置里的背景色 (主题的 background 就是它), 不透过下面的正文, 也不做淡入淡出.
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
    ) {
        // 用 LazyColumn 而不是带 verticalScroll 的 Column: 分组要懒加载,
        // 而且光标要知道每一行在屏幕上的位置.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 上边距在这里不生效, 只让下边距跟着设置走
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 14.dp,
                end = 20.dp,
                bottom = preferences.marginBottom.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = ROW_TIME) {
                val time2 = remember {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                }
                Text(
                    text = "$time2\n阅读设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = ROW_CLOSE) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_CLOSE)),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text(text = "关闭菜单", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = ROW_CATALOG) {
                Button(
                    onClick = onOpenCatalog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_CATALOG)),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text(text = "打开目录", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = ROW_FILE_LIST) {
                Button(
                    onClick = onBackToFileList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_FILE_LIST)),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text(text = "返回文件列表", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = ROW_SETTINGS) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_SETTINGS)),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text(text = "设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = "menu.spacer") { ControlSpacer() }

            // ---- 亮度与显示 ----
            item(key = ROW_GROUP_DISPLAY) {
                GroupButton(
                    group = MenuGroup.Display,
                    expanded = expandedGroup == MenuGroup.Display,
                    selected = cursorAt(ROW_GROUP_DISPLAY),
                    onClick = { toggleGroup(MenuGroup.Display) },
                )
            }
            if (expandedGroup == MenuGroup.Display) {
                item(key = ROW_KEEP_ON) {
                    SwitchButton(
                        checked = preferences.keepScreenOn,
                        onCheckedChange = { update.value(preferences.copy(keepScreenOn = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectionBorder(cursorAt(ROW_KEEP_ON)),
                    ) {
                        Text(
                            "阅读时常亮",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = ROW_BRIGHTNESS) {
                    StepRow(
                        label = "屏幕亮度",
                        value = formatScreenBrightness(preferences.screenBrightness),
                        onMinus = onScreenBrightnessDown,
                        onPlus = onScreenBrightnessUp,
                        selectedControl = if (cursorAt(ROW_BRIGHTNESS)) cursorIndex else null,
                    )
                }
            }

            // ---- 字体显示 ----
            item(key = ROW_GROUP_FONT) {
                GroupButton(
                    group = MenuGroup.Font,
                    expanded = expandedGroup == MenuGroup.Font,
                    selected = cursorAt(ROW_GROUP_FONT),
                    onClick = { toggleGroup(MenuGroup.Font) },
                )
            }
            if (expandedGroup == MenuGroup.Font) {
                item(key = ROW_TEXT_BRIGHTNESS) {
                    StepRow(
                        label = "文字亮度",
                        value = "${(preferences.textBrightness * 100).toInt()}%",
                        onMinus = onTextBrightnessDown,
                        onPlus = onTextBrightnessUp,
                        selectedControl = if (cursorAt(ROW_TEXT_BRIGHTNESS)) cursorIndex else null,
                    )
                }
                item(key = ROW_TEXT_RED) {
                    StepRow(
                        label = "R",
                        value = textRed.toString(),
                        onMinus = onTextRedDown,
                        onPlus = onTextRedUp,
                        selectedControl = if (cursorAt(ROW_TEXT_RED)) cursorIndex else null,
                    )
                }
                item(key = ROW_TEXT_GREEN) {
                    StepRow(
                        label = "G",
                        value = textGreen.toString(),
                        onMinus = onTextGreenDown,
                        onPlus = onTextGreenUp,
                        selectedControl = if (cursorAt(ROW_TEXT_GREEN)) cursorIndex else null,
                    )
                }
                item(key = ROW_TEXT_BLUE) {
                    StepRow(
                        label = "B",
                        value = textBlue.toString(),
                        onMinus = onTextBlueDown,
                        onPlus = onTextBlueUp,
                        selectedControl = if (cursorAt(ROW_TEXT_BLUE)) cursorIndex else null,
                    )
                }
                item(key = ROW_TEXT_ALPHA) {
                    StepRow(
                        label = "透明度",
                        value = preferences.textColorAlpha.toString(),
                        onMinus = onTextAlphaDown,
                        onPlus = onTextAlphaUp,
                        selectedControl = if (cursorAt(ROW_TEXT_ALPHA)) cursorIndex else null,
                    )
                }
                item(key = ROW_RESET_TEXT_COLOR) {
                    Button(
                        onClick = onResetTextColor,
                        modifier = Modifier.selectionBorder(cursorAt(ROW_RESET_TEXT_COLOR)),
                        shape = RectangleShape,
                        colors = readerButtonColors(),
                    ) {
                        Text(text = "文字颜色恢复默认", textAlign = TextAlign.Center)
                    }
                }
            }

            // ---- 文本显示 ----
            item(key = ROW_GROUP_TEXT) {
                GroupButton(
                    group = MenuGroup.Text,
                    expanded = expandedGroup == MenuGroup.Text,
                    selected = cursorAt(ROW_GROUP_TEXT),
                    onClick = { toggleGroup(MenuGroup.Text) },
                )
            }
            if (expandedGroup == MenuGroup.Text) {
                item(key = ROW_FONT_SIZE) {
                    StepRow(
                        label = "字号",
                        value = preferences.fontSize.toString(),
                        onMinus = onFontSizeDown,
                        onPlus = onFontSizeUp,
                        selectedControl = if (cursorAt(ROW_FONT_SIZE)) cursorIndex else null,
                    )
                }
                item(key = ROW_SPACING) {
                    StepRow(
                        label = "段间距",
                        value = spacingLabel(preferences.spacingLevel),
                        onMinus = onSpacingDown,
                        onPlus = onSpacingUp,
                        selectedControl = if (cursorAt(ROW_SPACING)) cursorIndex else null,
                    )
                }
            }
        }
    }
}

/** 分组按钮: 收起时带 ▸, 展开时带 ▾. */
@Composable
private fun GroupButton(
    group: MenuGroup,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .selectionBorder(selected),
        shape = RectangleShape,
        colors = readerButtonColors(),
    ) {
        Text(
            text = "${group.header} ${if (expanded) "▾" else "▸"}",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 一行「标签 + − 值 ＋」的步进控件; 设置页的字号也复用它.
 *
 * [selectedControl] 是体感光标在这一行里的下标 (null 表示光标不在这一行):
 * 0 给 − 套边框, 1 给 ＋, 其余不套. 设置页不传, 就是没有任何高亮.
 */
@Composable
internal fun StepRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    selectedControl: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", onMinus, selected = selectedControl == 0)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(58.dp),
        )
        StepButton("＋", onPlus, selected = selectedControl == 1)
    }
}

/**
 * 一行「标签 + − 数字输入框 ＋」; 体感设置页的检测间隔 / 触发后休息间隔用它.
 *
 * 和 [StepRow] 只差中间那一格: 这里是**可以直接输入整数**的输入框.
 * 输入过程中只有落进 [inputRange] 的整数才写回设置, 越界 / 空串先留在框里,
 * 失去焦点时还原成当前值, 否则想输 200 时刚敲下第一个 2 就被夹到下限.
 */
@Composable
internal fun NumberStepRow(
    label: String,
    value: Int,
    inputRange: IntRange,
    onValueChange: (Int) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", onMinus)
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                text = digits
                digits.toIntOrNull()?.takeIf { it in inputRange }?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .width(58.dp)
                // 焦点走了就把没生效的中间输入还回去, 免得框里留着一个和设置不一样的值.
                .onFocusChanged { if (!it.isFocused) text = value.toString() },
        )
        StepButton("＋", onPlus)
    }
}

/**
 * 一次走一档的步进按钮.
 *
 * 抬手时且这一按没有被滑动带走, 才算一次点击; 滑动 (事件被列表滚动消费) 会让整次手势作废.
 * 按住不动超过 500ms 后每 60ms 走一档, 比 [HOLD_DELAY_MS] 短的按压在抬手时补走一档.
 */
@Composable
private fun StepButton(label: String, onStep: () -> Unit, selected: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    val stepState = rememberUpdatedState(onStep)

    // pressed 为 true 时开始连发; 抬手 (pressed 变 false) 会让这个 effect 被取消.
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
            .background(readerButtonContainerColor())
            // 边框画在背景之后: 圆形按钮上刚好贴着边, 套在文字外圈.
            .selectionBorder(selected, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 消费掉这次手势, 父级的「空白处点击关闭菜单」就不会被触发.
                    down.consume()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    up?.consume()
                    pressed = false
                    // 抬手才算数; 按得够久说明连发已经接管, 不再补这一档.
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

/** 在离散档位表里走一步; 找不到当前值时就近取. */
private fun stepInList(steps: List<Float>, current: Float, delta: Int): Float {
    if (steps.isEmpty()) return current
    val index = steps.indexOfFirst { abs(it - current) < 0.001f }
        .takeIf { it >= 0 }
        ?: steps.indices.minByOrNull { abs(steps[it] - current) }
        ?: 0
    return steps[(index + delta).coerceIn(0, steps.lastIndex)]
}
