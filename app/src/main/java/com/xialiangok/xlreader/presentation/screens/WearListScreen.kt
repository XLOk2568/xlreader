package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.xialiangok.xlreader.data.TimeDisplay
import com.xialiangok.xlreader.presentation.theme.LocalReaderPreferences
import com.xialiangok.xlreader.presentation.theme.ReaderBlack
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 有 header 的页面, 头离屏幕顶部的固定间距.
 *
 * 屏高只有两百来 dp, 再让头跟着 10% 的系统留白下移就太浪费了; 目录页的紫条因此贴顶放.
 */
private val HeaderTopPadding = 4.dp

/**
 * 所有页面共用的骨架。
 *
 * 这里刻意选择**普通的 [LazyColumn]**，而不是带缩放/渐变变换的列表，
 * 于是滚动时列表项不会放大、缩小或淡出——这正是本应用“减少视觉效果”的做法：
 * 没有任何逐项动画，滚动跟手且省电。
 *
 * 下面两点也都跟“库里的规格写死、不受主题控制”有关
 * （[androidx.wear.compose.material3.MotionScheme] 与 `LocalReduceMotion` 都够不着它们）：
 * 1. 顶部时间：不把 `scrollState` 交给 [ScreenScaffold]，因而它拿不到
 *    `scrollInfoProvider`（默认 null）。库只有在拿到该 provider 时才会用 `scrollAway`
 *    把时间文本包起来，而那一层里的缩放/淡出/位移是写死时长的 tween，所以留空之后
 *    时间文本就是纯静态的：滚动时不缩、不淡、不移，“停手 2 秒再浮回来”的回弹也没有了。
 * 2. 滚动指示条：全应用都不再显示（[ScreenScaffold] 的 `scrollIndicator` 恒传 `null`）。
 *    这里有个坑值得记下来：库只在「传了 `scrollState`」的那条分支里给指示条套
 *    `Modifier.align(Alignment.CenterEnd)`（见 `ScreenScaffold` 源码里
 *    `scrollInfoProvider?.let { AnimatedIndicator(modifier = Modifier.align(CenterEnd)) } ?: scrollIndicator`），
 *    而本骨架为了关掉时间文本的动效**刻意不传 scrollState**，于是就算自己写指示条，
 *    也会落在 Box 的默认位置 TopStart —— 也就是屏幕上**左上角**，而不是右侧。
 *    反正这个应用不需要它，直接整条不显示。
 *
 * @param timeText  屏幕顶部的时间文本, 默认跟着设置里的「时间显示」走
 *   ([TimeDisplay.Wear] / [TimeDisplay.Phone] / [TimeDisplay.None]).
 *   传空 lambda 表示这一页不要时间 (正文页和目录页就这么做).
 * @param resetKey  变化时把列表滚到 [startIndex]。切换目录、翻章都必须重置，
 *   否则列表状态会被复用，用户一进新内容就停在中间。
 *   传 **null 表示这一页自己管滚动位置**：进出组合时都不动它（文件列表要靠这一点
 *   保住从阅读页 / 设置页 / 关于页回来时的原位置），需要重置时由调用方自己滚。
 * @param startIndex [resetKey] 变化时滚动到的位置。正文页用它恢复上次读到的段落。
 * @param listState 调用方如果需要在外面读滚动位置（例如记录阅读进度），
 *   可以自己创建状态传进来。
 * @param onTap     单击列表区域（不是点在具体按钮上）的回调。正文页用它切换快捷菜单。
 * @param header    钉在列表上方, **不跟着滚动**的一块内容 (传 null 就是没有).
 *   有它的时候头离屏幕顶部固定 [HeaderTopPadding], 上下边距改由页面自己在列表首尾放
 *   Spacer 负责; 于是滚动只发生在下面的列表里, 头一直待在顶上. 目录页用它放那根紫条.
 * @param applyTopMargin 上边距是否生效 (有 header 时不生效). 目录页顶上已经钉了紫条, 上下边距
 *   在列表内容里用 Spacer 处理.
 * @param content   列表内容.
 */
@Composable
fun WearListScreen(
    timeText: @Composable () -> Unit = { ReaderTimeText() },
    resetKey: Any? = null,
    startIndex: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    onTap: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    applyTopMargin: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    // 系统默认上下留白是屏高的 10%, 这里按设置换成用户自己定的上下边距 (替换而非叠加).
    val prefs = LocalReaderPreferences.current
    val layoutDirection = LocalLayoutDirection.current
    // timeText 必须同时传给 AppScaffold: 它的默认值是 { TimeText() } (原生弧形时间),
    // 而 ScreenScaffold 没登记到 timeText 时, AppScaffold 会拿这个默认值垫上,
    // 于是「不要时间」的页面 (正文页 timeText = {}) 也会冒出原生 Wear 时间. 两处传同一份就不会.
    AppScaffold(timeText = timeText) {
        // resetKey 为 null 时**连这个 effect 都不建**: LaunchedEffect 每次进入组合都会跑一遍,
        // 页面重新组合 (比如从设置页回到文件列表) 时 resetKey 没变也会把位置拉回 [startIndex].
        if (resetKey != null) {
            LaunchedEffect(resetKey) {
                // 下标越界时由 LazyList 自己钳制; 恢复进度失败不该让页面崩掉.
                runCatching { listState.scrollToItem(startIndex.coerceAtLeast(0)) }
            }
        }
        // 注意: 这里**没有传 scrollState**, 走的是 [ScreenScaffold] 那个不带滚动状态的
        // 重载, scrollInfoProvider 保持默认的 null —— 这就是上面注释里关掉动效的开关本身.
        ScreenScaffold(
            timeText = timeText,
            // 整条滚动指示条不创建 (null = 不显示). 理由见上面类注释第 2 条.
            scrollIndicator = null,
        ) { contentPadding ->
            val effectivePadding = remember(
                contentPadding,
                prefs.marginTop,
                prefs.marginBottom,
                applyTopMargin,
                header != null,
                layoutDirection,
            ) {
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    // 有 header 时外面只留头的 4dp, 上下边距留给列表首尾的 Spacer (否则头也会被推开).
                    top = when {
                        header != null -> HeaderTopPadding
                        applyTopMargin -> prefs.marginTop.dp
                        else -> contentPadding.calculateTopPadding()
                    },
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = if (header != null) 0.dp else prefs.marginBottom.dp,
                )
            }
            // 用 rememberUpdatedState 拿到最新的回调, 避免每次重组都重建手势检测器.
            val tapHandler = rememberUpdatedState(onTap)
            val tapModifier = if (onTap != null) {
                Modifier.pointerInput(Unit) {
                    // 只认「按下后没有移动」的点击; 拖动交给 LazyColumn 自己滚动.
                    // 子级按钮会消费掉自己的点击, 所以这里不会误触发.
                    detectTapGestures { tapHandler.value?.invoke() }
                }
            } else {
                Modifier
            }

            if (header == null) {
                LazyColumn(
                    state = listState,
                    contentPadding = effectivePadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(tapModifier),
                    content = content,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(effectivePadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    header()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(tapModifier),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * 页内分隔用的 Spacer, 高度统一取设置里的「大多控件间距」.
 *
 * 各页原来各自写死 4/6/8/10/16dp, 现在同一个数值, 改一处就全变.
 * 只给原本就有 Spacer 的地方用, 原先没有的不加, 免得凭空多出一截留白.
 */
@Composable
fun ControlSpacer() {
    Spacer(Modifier.height(LocalReaderPreferences.current.controlSpacing.dp))
}

/**
 * 默认的顶部时间: 按设置三选一.
 *
 * [TimeDisplay.Wear] 用库自带的弧形时间; [TimeDisplay.Phone] 用无弧度的数字时间;
 * [TimeDisplay.None] 什么都不画. 只有没显式传 `timeText` 的页面会走到这里.
 */
@Composable
private fun ReaderTimeText() {
    when (LocalReaderPreferences.current.timeDisplay) {
        TimeDisplay.Wear -> TimeText()
        TimeDisplay.Phone -> PhoneTimeText()
        TimeDisplay.None -> Unit
    }
}

/**
 * 无弧度的数字时间, 样式接近手机状态栏: 顶部居中, 每跨过一分钟刷新一次.
 *
 * 不复用 [TimeText]: 它画在弧线上, 用户明确要一个「没有弧度」的方案.
 * 时间固定 24 小时制, 不跟随系统的 12 小时设置.
 *
 * 2026-10-31 起按用户口径: 字号与颜色亮度都跟随正文 (fontSize / 正文亮的同一份),
 * 不透明, 底色块改成直角 (不再用圆角).
 *
 * 文字底下垫一块**纯黑**底色: 时间文本是被 [AppScaffold] 叠在内容之上画的 (先 content 后 timeText),
 * 列表滚动时会从数字下面透出来, 看着很脏; 库自带的弧形时间靠背后那圈弧线底色挡住, 这里没有弧,
 * 只能自己补一层. 底色**只包住文字** (不 fillMaxWidth): 铺满整行会在滚动时把整条卡片都盖黑,
 * 左右还露出一条突兀的直边.
 * 用纯黑而不是设置里的背景色, AMOLED 上黑像素不发光, 这一块不会变成一块灰板.
 */
@Composable
private fun PhoneTimeText() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 对齐到下一个整分钟再刷新, 不轮询也不每秒重组.
    LaunchedEffect(Unit) {
        while (true) {
            delay((60_000L - System.currentTimeMillis() % 60_000L).milliseconds)
            now = System.currentTimeMillis()
        }
    }
    // 默认 24 小时制, 不跟随系统的 12/24 小时设置.
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    // 离屏幕上沿多远由设置里的「顶部悬浮上边距」决定 (只有 PhoneUI 这一种时间有).
    val topMargin = LocalReaderPreferences.current.homeHoverTop.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topMargin, bottom = 4.dp),
        // 文字水平居中; 黑色底随文字宽度收缩, 不再铺满整行.
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatter.format(Date(now)),
            textAlign = TextAlign.Center,
            // 字号跟随正文 (bodyMedium 就是正文那一档, 见 Theme.kt 的 Typography 重写).
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            // onSurface 是正文色叠加亮度后的值, 不透明, 不再压暗成次要文字色.
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(ReaderBlack, RectangleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

