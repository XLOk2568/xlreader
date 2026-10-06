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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.scrollByItems
import com.xialiangok.xlreader.presentation.theme.ReaderButtonContainer
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** 菜单底色：完全不透明（`0xFF`），不透过下面的正文，也不做任何淡入淡出。 */
private val MenuBackground = Color(0xFF141414)

/** 当前选中控件的边框色：用户指定的 rgb(255, 0, 255)。 */
private val SelectionBorderColor = Color(0xFFFF00FF)

/** 边框粗细：标准的 52dp 按钮、36dp 的圆形步进按钮上都看得清，又不至于盖住内容。 */
private val SelectionBorderWidth = 2.dp

/**
 * 菜单里每一行的 key。
 *
 * 用字符串当 key 而不是下标：手势翻页是按条目滚的，光标要能知道「现在停在哪个控件上」，
 * 下标会跟着列表内容变（比如以后加了新行），名字不会。
 */
private const val ROW_TIME = "menu.time"
private const val ROW_CLOSE = "menu.close"
private const val ROW_CATALOG = "menu.catalog"
private const val ROW_FILE_LIST = "menu.fileList"
private const val ROW_SETTINGS = "menu.settings"
private const val ROW_FONT_SIZE = "menu.fontSize"
private const val ROW_SPACING = "menu.spacing"
private const val ROW_KEEP_ON = "menu.keepScreenOn"
private const val ROW_BRIGHTNESS_LABEL = "menu.brightnessLabel"
private const val ROW_BRIGHTNESS = "menu.brightness"
private const val ROW_TEXT_BRIGHTNESS_LABEL = "menu.textBrightnessLabel"
private const val ROW_TEXT_BRIGHTNESS = "menu.textBrightness"
private const val ROW_COLOR_LABEL = "menu.colorLabel"
private const val ROW_COLOR_PREVIEW = "menu.colorPreview"
private const val ROW_R = "menu.colorR"
private const val ROW_G = "menu.colorG"
private const val ROW_B = "menu.colorB"
private const val ROW_RESET_COLOR = "menu.resetColor"
private const val ROW_BOTTOM_SPACE = "menu.bottomSpace"

/**
 * 光标能落下的行（有可点控件的行），顺序就是上下翻页时光标走的顺序。
 *
 * 标题、色块预览这些不可点的行不在里面：光标经过它们时会直接跳过去，
 * 不会停在一条「按下没反应」的行上。
 */
private val SELECTABLE_ROWS = listOf(
    ROW_CLOSE,
    ROW_CATALOG,
    ROW_FILE_LIST,
    ROW_SETTINGS,
    ROW_FONT_SIZE,
    ROW_SPACING,
    ROW_KEEP_ON,
    ROW_BRIGHTNESS,
    ROW_TEXT_BRIGHTNESS,
    ROW_R,
    ROW_G,
    ROW_B,
    ROW_RESET_COLOR,
)

private val SELECTABLE_ROW_SET = SELECTABLE_ROWS.toSet()

/**
 * 每一行里有几个可点控件。
 *
 * 「字号」「段间距」「RGB」这些步进行有两个（`−` / `＋`），其余行都是一个。
 * 现在上下翻页只走行、落到行内第一个控件上；以后加左右手势时就靠这个数字在行内走。
 */
private val ROW_CONTROL_COUNTS = mapOf(
    ROW_FONT_SIZE to 2,
    ROW_SPACING to 2,
    ROW_BRIGHTNESS to 2,
    ROW_TEXT_BRIGHTNESS to 2,
    ROW_R to 2,
    ROW_G to 2,
    ROW_B to 2,
)

/** 这一行有几个可点控件；没登记的行按一个算（整行就是一个控件）。 */
private fun controlCountOf(row: String): Int = ROW_CONTROL_COUNTS[row] ?: 1

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

/** 给当前选中的控件套一圈边框；没被选中就原样返回（连一层 modifier 都不加）。 */
private fun Modifier.selectionBorder(selected: Boolean, shape: Shape = RectangleShape): Modifier =
    if (selected) border(SelectionBorderWidth, SelectionBorderColor, shape) else this

/**
 * 屏幕上离正中最近的**可选中**行；列表还没布局出来时返回 null。
 *
 * 取「离正中最近」而不是「第一个可见行」：翻页手势是按条目滚的，滚完之后第一行
 * 往往已经贴着屏幕上沿了，正中那一行才更接近用户眼里的「当前控件」。
 * 不可点的行（时间、标题、色块预览）直接跳过。
 */
private fun centeredSelectableRow(listState: LazyListState): String? {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    var best: String? = null
    var bestDistance = Int.MAX_VALUE
    for (item in info.visibleItemsInfo) {
        val key = item.key as? String ?: continue
        if (key !in SELECTABLE_ROW_SET) continue
        val distance = abs(item.offset + item.size / 2 - center)
        if (distance < bestDistance) {
            bestDistance = distance
            best = key
        }
    }
    return best
}

/**
 * 阅读界面的快捷设置菜单。
 *
 * 单击正文弹出；点面板空白处、点「关闭菜单」、体感「返回」、系统返回手势都能关闭。
 * 这里改的每一项都直接写回 [ReaderPreferences]，也就是设置页看到的那份数据，
 * 所以「等同于设置页面效果」。
 *
 * 面板铺满整屏，**空白处的单击等于关闭菜单**；按钮、开关、步进器会消费掉自己的点击，
 * 所以不会误关。
 *
 * 体感手势（这一层在菜单开着时**自己接管**，正文页那一份同时注销，见 [ChapterScreen]）：
 * - 上下翻页 = 按体感设置里的「速度」滚菜单内容，和正文页用同一套滚动；
 * - 光标 = 屏幕上离正中最近的那个可点控件，滚动时跟着屏幕走，**套一圈 rgb(255, 0, 255) 的边框**；
 *   菜单刚打开时它默认停在「关闭菜单」上（第一帧不跟随屏幕正中，见下面那个 effect）；
 * - 内容已经滚到头（下面没有更多行 / 上面已经到顶）时，翻页手势就只挪光标、不滚内容；
 * - 单击 = 执行光标所在的那个控件（所以这时单击不再关菜单，关菜单请点空白处或用手指点）。
 * - 返回 = 关菜单（走 [onDismiss]，和系统返回同一个出口），不是退出这本书。
 *
 * [gestureEnabled] 为假时上面这一整套都不存在：不登记任何手势动作、也不画选中光标，
 * 菜单只靠手指使用（开关在「启用页面」里的**正文阅读(菜单)**，默认不勾）。
 *
 * @param gestureEnabled 要不要在菜单里跑体感光标导航。
 *
 * @param onOpenCatalog 打开章节目录，和正文底部的「打开目录」走同一条路
 *   （因此进度会在同一条路上落盘、常亮与屏幕亮度也会随正文页一起还原）。
 *   放在这里是为了不用滚到本章末尾才能翻目录。这是「打开」不是「返回」：
 *   在目录里再返回一次是回到这一章，不会一路退出这本书。
 *   菜单开着这个状态记在根组件上、这里刻意不清掉它，所以从目录返回时
 *   回到的是**这个菜单**（而不是直接落回正文）；想带着菜单回来就得从这里进目录。
 * @param onBackToFileList 返回文件列表，和正文底部的「返回文件列表」走同一条路。
 *   想换一本书时不用说先回目录、再滚到目录页最底下点返回。
 * @param onOpenSettings 打开设置页（和文件列表里的「设置」是同一页）。
 *   这是「打开」不是「返回」：在设置页里返回时回到的是这一章、而不是文件列表。
 * @param onDismiss 关闭菜单，不算离开这本书：点「关闭菜单」、点面板空白处、
 *   体感「返回」、系统返回手势（ChapterScreen 的 BackHandler）都走它，四种方式效果一致。
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

    // 光标：停在哪一行、行内第几个控件。向上/向下翻页在内容滚到头之后改的就是它；
    // 内容还能滚的时候它跟着屏幕走（见下面那个 effect）。
    // 初值就是列表第一个可选中行 —— 也就是「关闭菜单」，菜单一打开光标就停在它上面。
    var cursorRow by remember { mutableStateOf(SELECTABLE_ROWS.first()) }
    var cursorIndex by remember { mutableIntStateOf(0) }

    /** 光标此刻在不在 [row] 这一行。没启用手势导航时一律为假，边框一个都不画。 */
    fun cursorAt(row: String): Boolean = gestureEnabled && cursorRow == row

    /** 把光标沿行挪 [delta] 行（挪到头就停住），并把行内下标夹回这一行真有的控件数。 */
    fun moveSelection(delta: Int) {
        val index = SELECTABLE_ROWS.indexOf(cursorRow).coerceAtLeast(0)
        val next = SELECTABLE_ROWS[(index + delta).coerceIn(0, SELECTABLE_ROWS.lastIndex)]
        cursorRow = next
        cursorIndex = cursorIndex.coerceAtMost(controlCountOf(next) - 1)
    }

    // 内容滚动时（手势翻页或手指滑）光标跟着「屏幕上离正中最近的那一行」走：
    // 上一行滚出屏幕、下一行滚进来，边框自然落到新的行上，用户不用自己去数。
    // 用 snapshotFlow + distinctUntilChanged 而不是每次重组都读：滚动中每帧都会变，
    // 只有「当前行真的换了」时才重组一次菜单。
    //
    // **第一次布局出来的那一帧不动光标**：菜单刚打开时光标必须停在「关闭菜单」上（用户口径），
    // 不能让「离正中最近」这条规则立刻把它挪到中间那几行去；等用户真的滚动了内容，
    // 后面每一次「正中那行变了」才接手。
    LaunchedEffect(listState) {
        var settled = false
        snapshotFlow { centeredSelectableRow(listState) }
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

    // 每一项的动作。控件行之外的 lambda 只写在这里一份，UI 上和手势单击共用同一份，
    // 不会出现「手势点一下和手指点一下效果不一样」。
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
    val onRedDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(stepChannel(redOf(preferences.textColor), -1), greenOf(preferences.textColor), blueOf(preferences.textColor))))
    }
    val onRedUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(stepChannel(redOf(preferences.textColor), +1), greenOf(preferences.textColor), blueOf(preferences.textColor))))
    }
    val onGreenDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), stepChannel(greenOf(preferences.textColor), -1), blueOf(preferences.textColor))))
    }
    val onGreenUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), stepChannel(greenOf(preferences.textColor), +1), blueOf(preferences.textColor))))
    }
    val onBlueDown: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), greenOf(preferences.textColor), stepChannel(blueOf(preferences.textColor), -1))))
    }
    val onBlueUp: () -> Unit = {
        update.value(preferences.copy(textColor = packRgb(redOf(preferences.textColor), greenOf(preferences.textColor), stepChannel(blueOf(preferences.textColor), +1))))
    }
    val onResetTextColor: () -> Unit = {
        update.value(
            preferences.copy(
                textColor = ReaderPreferences.DEFAULT_TEXT_COLOR,
                textBrightness = ReaderPreferences.MAX_TEXT_BRIGHTNESS,
            ),
        )
    }

    /** 行 key → 这一行的控件（顺序就是行内顺序）。体感单击照着 [cursorRow] / [cursorIndex] 取。 */
    val controlsByRow: Map<String, List<() -> Unit>> = mapOf(
        ROW_CLOSE to listOf(onDismiss),
        ROW_CATALOG to listOf(onOpenCatalog),
        ROW_FILE_LIST to listOf(onBackToFileList),
        ROW_SETTINGS to listOf(onOpenSettings),
        ROW_FONT_SIZE to listOf(onFontSizeDown, onFontSizeUp),
        ROW_SPACING to listOf(onSpacingDown, onSpacingUp),
        ROW_KEEP_ON to listOf(onKeepScreenOnToggle),
        ROW_BRIGHTNESS to listOf(onScreenBrightnessDown, onScreenBrightnessUp),
        ROW_TEXT_BRIGHTNESS to listOf(onTextBrightnessDown, onTextBrightnessUp),
        ROW_R to listOf(onRedDown, onRedUp),
        ROW_G to listOf(onGreenDown, onGreenUp),
        ROW_B to listOf(onBlueDown, onBlueUp),
        ROW_RESET_COLOR to listOf(onResetTextColor),
    )

    // 菜单开着时接管体感手势（正文页那份同时注销，见 ChapterScreen 里的条件登记）。
    // 单击只执行光标所在的控件 —— 以前这里是「关菜单」，现在关菜单交给空白处/手指。
    // 返回 = 关菜单：菜单铺满整屏，往回退一步的意图是关掉它，
    // 所以这里和系统返回（ChapterScreen 的 BackHandler）走同一条路，都不退出这本书。
    // 没启用「正文阅读(菜单)」这一页时干脆不登记：菜单里没有任何体感动作，只靠手指用。
    if (gestureEnabled) {
        BindGestureActions(
            onTap = { controlsByRow[cursorRow]?.getOrNull(cursorIndex)?.invoke() },
            onBack = onDismiss,
            onScrollBy = { delta ->
                // 内容还能往这个方向滚就滚内容（和正文页同一套 scrollItems），
                // 已经滚到头（没有更多行 / 已经到顶）就只挪光标：这时再滚页面也不动了，
                // 手势总得有点用，光标停在边框上也是给用户的反馈。
                // 「还没量出来」时（刚打开菜单、列表高度还是 0）一律当还能滚：
                // 否则第一下手势就会被误判成「已经在头/尾」而只挪光标。
                val measured = listState.layoutInfo.totalItemsCount > 0
                val atEnd = measured &&
                    (if (delta > 0) !listState.canScrollForward else !listState.canScrollBackward)
                if (atEnd) moveSelection(delta) else scope.launch { scrollByItems(listState, delta) }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBackground)
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
    ) {
        // 用 LazyColumn 而不是带 verticalScroll 的 Column：手势翻页要「按条目滚」、
        // 光标要知道每一行在屏幕上的位置，这些都要求列表自己知道条目下标。
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
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
                    shape = RectangleShape,          // 直角
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
                    shape = RectangleShape,          // 直角
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
                    shape = RectangleShape,          // 直角
                    colors = readerButtonColors(),
                ) {
                    Text(text = "设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = "menu.spacer4") { Spacer(Modifier.height(4.dp)) }

            // ---- 字号：和亮度、RGB 一样用「− / ＋」一次走一磅，直接显示磅值 ----
            item(key = ROW_FONT_SIZE) {
                StepRow(
                    label = "字号",
                    value = preferences.fontSize.toString(),
                    onMinus = onFontSizeDown,
                    onPlus = onFontSizeUp,
                    selectedControl = if (cursorAt(ROW_FONT_SIZE)) cursorIndex else null,
                )
            }
            // ---- 段间距：和字号一样是直接的数字（单位 dp），「− / ＋」一次走 1 dp ----
            item(key = ROW_SPACING) {
                StepRow(
                    label = "段间距",
                    value = spacingLabel(preferences.spacingLevel),
                    onMinus = onSpacingDown,
                    onPlus = onSpacingUp,
                    selectedControl = if (cursorAt(ROW_SPACING)) cursorIndex else null,
                )
            }

            // ---- 常亮 ----
            item(key = ROW_KEEP_ON) {
                SwitchButton(
                    checked = preferences.keepScreenOn,
                    onCheckedChange = { update.value(preferences.copy(keepScreenOn = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_KEEP_ON)),
                ) {
                    Text("阅读时常亮", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = ROW_BRIGHTNESS_LABEL) { SectionLabel("屏幕亮度") }
            item(key = ROW_BRIGHTNESS) {
                StepRow(
                    label = "亮度",
                    value = formatScreenBrightness(preferences.screenBrightness),
                    onMinus = onScreenBrightnessDown,
                    onPlus = onScreenBrightnessUp,
                    selectedControl = if (cursorAt(ROW_BRIGHTNESS)) cursorIndex else null,
                )
            }

            item(key = ROW_TEXT_BRIGHTNESS_LABEL) { SectionLabel("文字亮度") }
            item(key = ROW_TEXT_BRIGHTNESS) {
                StepRow(
                    label = "亮度",
                    value = "${(preferences.textBrightness * 100).toInt()}%",
                    onMinus = onTextBrightnessDown,
                    onPlus = onTextBrightnessUp,
                    selectedControl = if (cursorAt(ROW_TEXT_BRIGHTNESS)) cursorIndex else null,
                )
            }

            item(key = ROW_COLOR_LABEL) { SectionLabel("文字颜色（RGB，每次 1 档）") }
            item(key = ROW_COLOR_PREVIEW) { ColorPreview(preferences) }
            item(key = "menu.spacerRGB2") { Spacer(Modifier.height(2.dp)) }
            item(key = ROW_R) {
                ChannelRow(
                    channel = "R",
                    value = redOf(preferences.textColor),
                    onMinus = onRedDown,
                    onPlus = onRedUp,
                    selectedControl = if (cursorAt(ROW_R)) cursorIndex else null,
                )
            }
            item(key = "menu.spacerRGB3") { Spacer(Modifier.height(2.dp)) }
            item(key = ROW_G) {
                ChannelRow(
                    channel = "G",
                    value = greenOf(preferences.textColor),
                    onMinus = onGreenDown,
                    onPlus = onGreenUp,
                    selectedControl = if (cursorAt(ROW_G)) cursorIndex else null,
                )
            }
            item(key = "menu.spacerRGB4") { Spacer(Modifier.height(2.dp)) }
            item(key = ROW_B) {
                ChannelRow(
                    channel = "B",
                    value = blueOf(preferences.textColor),
                    onMinus = onBlueDown,
                    onPlus = onBlueUp,
                    selectedControl = if (cursorAt(ROW_B)) cursorIndex else null,
                )
            }
            item(key = "menu.spacerRGB5") { Spacer(Modifier.height(2.dp)) }
            item(key = ROW_RESET_COLOR) {
                Button(
                    onClick = onResetTextColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectionBorder(cursorAt(ROW_RESET_COLOR)),
                    shape = RectangleShape,          // 直角
                    colors = readerButtonColors(),
                ) {
                    Text(text = "文字颜色恢复默认", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            item(key = ROW_BOTTOM_SPACE) { Spacer(Modifier.height(20.dp)) }
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
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    selectedControl: Int? = null,
) {
    StepRow(
        label = channel,
        value = value.toString(),
        onMinus = onMinus,
        onPlus = onPlus,
        selectedControl = selectedControl,
    )
}

/**
 * 一行「标签 + − 值 ＋」的步进控件；设置页的字号也复用它。
 *
 * [selectedControl] 是体感光标在这一行里的下标（null 表示光标不在这一行）：
 * 0 给 `−` 套边框、1 给 `＋`，其余不套。设置页不传，就是没有任何高亮。
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
            maxLines = 1,
            modifier = Modifier.width(58.dp),
        )
        StepButton("＋", onPlus, selected = selectedControl == 1)
    }
}

/**
 * 一行「标签 + − 数字输入框 ＋」；体感设置页的检测间隔 / 触发后休息间隔用它。
 *
 * 和 [StepRow] 只差中间那一格：这里是**可以直接输入整数**的输入框。
 * 为什么需要：间隔跨度是 0 ~ 60000ms、休息间隔是 0 ~ 5 分钟，全靠 10ms 一挡连发走到上限
 * 要按十来分钟，输入整数才是「快速设值」的那条路（用户口径）。
 *
 * 输入规则：只收数字（`-1` 那一挡由 `＋` 走到，手表数字键盘上也没有减号）；
 * 输入过程中只有**落进 [inputRange] 的整数**才写回设置，越界 / 空串先留在框里、
 * 失去焦点时还原成当前值 —— 否则想输 200 时刚敲下第一个 2 就被夹到下限、框里的字还被改掉，
 * 后面根本没法接着输。
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
                // 焦点走了就把没生效的中间输入还回去，免得框里留着一个和设置不一样的值。
                .onFocusChanged { if (!it.isFocused) text = value.toString() },
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
private fun StepButton(label: String, onStep: () -> Unit, selected: Boolean = false) {
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
            // 边框画在背景之后：圆形按钮上刚好贴着边，套在文字外圈。
            .selectionBorder(selected, CircleShape)
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
